/*
 * Copyright 2020 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.esri.arcgisruntime.sample.offlinerouting

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.esri.arcgisruntime.data.TileCache
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.layers.ArcGISTiledLayer
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.TextSymbol
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask
import com.esri.arcgisruntime.tasks.networkanalysis.Stop
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ExecutionException
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

  private val stopsOverlay: GraphicsOverlay by lazy { GraphicsOverlay() }
  private val routeOverlay: GraphicsOverlay by lazy { GraphicsOverlay() }
  private var routeParameters: RouteParameters? = null
  private val routeTask: RouteTask by lazy {
    RouteTask(
      this,
      getExternalFilesDir(null)?.path + getString(R.string.geodatabase_path),
      "Streets_ND"
    )
  }
  private val TAG: String = MainActivity::class.java.simpleName

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // create a tile cache from the tpk
    val tileCache = TileCache(getExternalFilesDir(null)?.path + getString(R.string.tpk_path))
    val tiledLayer = ArcGISTiledLayer(tileCache)
    // make a basemap with the tiled layer
    val basemap = Basemap(tiledLayer)
    mapView.map = ArcGISMap(basemap)

    // add the graphics overlays to the map view
    mapView.graphicsOverlays.addAll(listOf(stopsOverlay, routeOverlay))

    // load the route task
    routeTask.loadAsync()
    routeTask.addDoneLoadingListener {
      if (routeTask.loadStatus == LoadStatus.LOADED) {
        try {
          // create route parameters
          routeParameters = routeTask.createDefaultParametersAsync().get()
        } catch (e: Exception) {
          when (e) {
            is InterruptedException, is ExecutionException -> Log.e(
              TAG,
              "Error getting default route parameters. ${e.stackTrace}"
            )
          }
        }
      } else {
        Log.e(TAG, "Error loading route task. ${routeTask.loadError.message}")
      }
    }

    // set up travel mode switch
    modeSwitch.setOnCheckedChangeListener { _, isChecked ->
      routeParameters?.travelMode = when (isChecked) {
        true -> routeTask.routeTaskInfo.travelModes[0]
        false -> routeTask.routeTaskInfo.travelModes[1]
      }
      Toast.makeText(
        this,
        "${routeParameters?.travelMode?.name} route selected.",
        Toast.LENGTH_SHORT
      ).show()
      updateRoute()
    }

    // make a clear button to reset the stops and routes
    clearButton.setOnClickListener {
      stopsOverlay.graphics.clear()
      routeOverlay.graphics.clear()
    }

    // add a graphics overlay to show the boundary
    GraphicsOverlay().let {
      val envelope = Envelope(
        Point(-13045352.223196, 3864910.900750, 0.0, SpatialReferences.getWebMercator()),
        Point(-13024588.857198, 3838880.505604, 0.0, SpatialReferences.getWebMercator())
      )
      val boundarySymbol = SimpleLineSymbol(SimpleLineSymbol.Style.DASH, 0xFF00FF00.toInt(), 5f)
      it.graphics.add(Graphic(envelope, boundarySymbol))
      mapView.graphicsOverlays.add(it)
    }

    // set up the touch listeners on the map view
    createMapGestures()
  }

  /**
   * Sets up the onTouchListener for the mapView.
   * For single taps, graphics will be selected.
   * For double touch drags, graphics will be moved.
   * */
  private fun createMapGestures() {
    mapView.onTouchListener = object : DefaultMapViewOnTouchListener(this, mapView) {
      override fun onSingleTapConfirmed(motionEvent: MotionEvent): Boolean {

        val screenPoint = android.graphics.Point(
          motionEvent.x.roundToInt(),
          motionEvent.y.roundToInt()
        )
        addOrSelectGraphic(screenPoint)
        return true
      }

      override fun onDoubleTouchDrag(motionEvent: MotionEvent): Boolean {

        val screenPoint = android.graphics.Point(
          motionEvent.x.roundToInt(),
          motionEvent.y.roundToInt()
        )§§§§§

        // move the selected graphic to the new location
        if (stopsOverlay.selectedGraphics.isNotEmpty()) {
          stopsOverlay.selectedGraphics[0]?.geometry = mapView.screenToLocation(screenPoint)
          updateRoute()
        }
        // ignore default double touch drag gesture
        return true
      }

      // ignore default double tap gesture
      override fun onDoubleTap(e: MotionEvent?): Boolean {
        return true
      }
    }
  }

  /**
   * Updates the calculated route by calling routeTask.solveRouteAsync().
   * Creates a graphic to display the route.
   * */
  private fun updateRoute() {
    // get a list of stops from the graphics currently on the graphics overlay.
    val stops = stopsOverlay.graphics.map {
      Stop(it.geometry as Point)
    }

    routeParameters?.setStops(stops)

    // solve the route
    val results = routeTask.solveRouteAsync(routeParameters)
    results.addDoneListener {
      try {
        val result = results.get()
        val route = result.routes[0]

        // create graphic for route
        val graphic = Graphic(
          route.routeGeometry, SimpleLineSymbol(
            SimpleLineSymbol.Style.SOLID,
            0xFF0000FF.toInt(), 3F
          )
        )

        routeOverlay.graphics.clear()
        routeOverlay.graphics.add(graphic)
      } catch (e: Exception) {
        when (e) {
          is InterruptedException, is ExecutionException -> Log.e(
            TAG,
            "No route solution. ${e.stackTrace}"
          )
        }
        routeOverlay.graphics.clear()
      }
    }
  }

  /**
   * Selects a graphic if there is one at the provided tapped location or, if there is none, creates a new graphic.
   *
   * @param screenPoint a point in screen space where the user tapped
   * */
  private fun addOrSelectGraphic(screenPoint: android.graphics.Point) {
    // identify the selected graphic
    val results = mapView.identifyGraphicsOverlayAsync(stopsOverlay, screenPoint, 10.0, false)
    results.addDoneListener {
      try {
        val graphics = results.get().graphics
        // unselect everything
        if (stopsOverlay.selectedGraphics.size > 0) {
          stopsOverlay.unselectGraphics(stopsOverlay.selectedGraphics)
        }
        // if the user tapped on something, select it
        if (graphics.size > 0) {
          val firstGraphic = graphics[0]
          firstGraphic.isSelected = true
        } else { // there is no graphic at this location
          // make a new graphic at the tapped location
          val locationPoint = mapView.screenToLocation(screenPoint)
          val stopLabel = TextSymbol(
            20f,
            (stopsOverlay.graphics.size + 1).toString(),
            0xFFFF0000.toInt(),
            TextSymbol.HorizontalAlignment.RIGHT,
            TextSymbol.VerticalAlignment.TOP
          )
          val graphic = Graphic(locationPoint, stopLabel)
          stopsOverlay.graphics.add(graphic)
          updateRoute()
        }
      } catch (e: Exception) {
        when (e) {
          is InterruptedException, is ExecutionException -> Log.e(
            TAG,
            "Error identifying graphic: ${e.stackTrace}"
          )
        }
      }
    }
  }

  override fun onPause() {
    mapView.pause()
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    mapView.resume()
  }

  override fun onDestroy() {
    mapView.dispose()
    super.onDestroy()
  }

}
