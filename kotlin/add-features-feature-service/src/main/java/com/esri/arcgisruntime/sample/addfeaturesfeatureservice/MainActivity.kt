/*
 * Copyright 2019 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.esri.arcgisruntime.sample.addfeaturesfeatureservice

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import com.esri.arcgisruntime.ArcGISRuntimeException
import com.esri.arcgisruntime.data.ServiceFeatureTable
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // create a map with streets basemap
    ArcGISMap(Basemap.Type.STREETS, 40.0, -95.0, 4).let { map ->

      // create service feature table from URL
      ServiceFeatureTable(getString(R.string.service_layer_url)).let { serviceFeatureTable ->

        // add a listener to the MapView to detect when a user has performed a single tap to add a new feature to
        // the service feature table
        mapView.onTouchListener = object : DefaultMapViewOnTouchListener(this, mapView) {
          override fun onSingleTapConfirmed(motionEvent: MotionEvent?): Boolean {
            motionEvent?.let { event ->
              // create a point from where the user clicked
              android.graphics.Point(event.x.toInt(), event.y.toInt()).let { point ->
                // create a map point from a point
                mapView.screenToLocation(point)
              }.let { mapPoint ->
                // for a wrapped around map, the point coordinates include the wrapped around value
                // for a service in projected coordinate system, this wrapped around value has to be normalized
                GeometryEngine.normalizeCentralMeridian(mapPoint) as Point
              }.let { normalizedMapPoint ->
                // add a new feature to the service feature table
                addFeature(normalizedMapPoint, serviceFeatureTable)
              }
            }
            return super.onSingleTapConfirmed(motionEvent)
          }
        }

        // create a feature layer from table
        FeatureLayer(serviceFeatureTable)
      }.let { featureLayer ->

        // add the layer to the ArcGISMap
        map.operationalLayers.add(featureLayer)
      }

      // set ArcGISMap to be displayed in map view
      mapView.map = map
    }
  }

  /**
   * Adds a new Feature to a ServiceFeatureTable and applies the changes to the
   * server.
   *
   * @param mapPoint location to add feature
   * @param featureTable service feature table to add feature
   */
  private fun addFeature(mapPoint: Point, featureTable: ServiceFeatureTable) {

    // create default attributes for the feature
    hashMapOf<String, Any>("typdamage" to "Destroyed", "primcause" to "Earthquake").let { attributes ->
      // creates a new feature using default attributes and point
      featureTable.createFeature(attributes, mapPoint)
    }.let { feature ->
      // check if feature can be added to feature table
      if (featureTable.canAdd()) {
        // add the new feature to the feature table and to server
        featureTable.addFeatureAsync(feature).addDoneListener { applyEdits(featureTable) }
      } else {
        logToUser(getString(R.string.error_cannot_add_to_feature_table))
      }
    }

  }

  /**
   * Sends any edits on the ServiceFeatureTable to the server.
   *
   * @param featureTable service feature table
   */
  private fun applyEdits(featureTable: ServiceFeatureTable) {

    // apply the changes to the server
    featureTable.applyEditsAsync().let { editResult ->
      editResult.addDoneListener {
        try {
          editResult.get().let { edits ->
            // check if the server edit was successful
            if (edits != null && edits.size > 0) {
              if (!edits[0].hasCompletedWithErrors()) {
                logToUser(getString(R.string.feature_added))
              } else {
                throw edits[0].error
              }
            }
          }
        } catch (e: ArcGISRuntimeException) {
          logToUser(getString(R.string.error_applying_edits, e.cause?.message))
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    mapView.resume()
  }

  override fun onPause() {
    mapView.pause()
    super.onPause()
  }

  override fun onDestroy() {
    mapView.dispose()
    super.onDestroy()
  }
}

/*
* AppCompatActivity Extensions
*/

/**
 * Shows a Toast to user and logs to logcat.
 *
 * @param message message to display
 */
fun AppCompatActivity.logToUser(message: String) {
  Toast.makeText(this, message, Toast.LENGTH_LONG).show()
  Log.d(this::class.java.simpleName, message)
}