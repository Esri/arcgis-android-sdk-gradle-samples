/*
 *  Copyright 2019 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.esri.arcgisruntime.sample.navigateinar;

import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.security.AuthenticationManager;
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.symbology.SimpleRenderer;
import com.esri.arcgisruntime.tasks.networkanalysis.Route;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteResult;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask;
import com.esri.arcgisruntime.tasks.networkanalysis.Stop;
import com.esri.arcgisruntime.tasks.networkanalysis.TravelMode;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = MainActivity.class.getSimpleName();

  private TextView mHelpLabel;
  private Button mNavigateButton;

  private MapView mMapView;

  private GraphicsOverlay mRouteOverlay;
  private GraphicsOverlay mStopsOverlay;

  private Point mStartPoint;
  private Point mEndPoint;

  private RouteTask mRouteTask;
  private Route mRoute;
  private RouteResult mRouteResult;
  private RouteParameters mRouteParameters;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // get a reference to the map view
    mMapView = findViewById(R.id.mapView);
    // create a map with an imagery base map and set to to the map view
    ArcGISMap map = new ArcGISMap(Basemap.createImagery());
    mMapView.setMap(map);

    // Get references to the views defined in the layout
    mHelpLabel = findViewById(R.id.helpLabel);

    mNavigateButton = findViewById(R.id.navigateButton);

    // request location permissions before starting
    requestPermissions();
  }

  /**
   * Start location display and define route task and graphic overlays.
   */
  private void initialize() {
    // enable the map view's location display
    LocationDisplay locationDisplay = mMapView.getLocationDisplay();
    // listen for changes in the status of the location data source.
    locationDisplay.addDataSourceStatusChangedListener(dataSourceStatusChangedEvent -> {
      if (!dataSourceStatusChangedEvent.isStarted() || dataSourceStatusChangedEvent.getError() != null) {
        // report data source errors to the user
        String message = String.format(getString(R.string.data_source_status_error),
            dataSourceStatusChangedEvent.getSource().getLocationDataSource().getError().getMessage());
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        mHelpLabel.setText(getString(R.string.location_failed_error_message));
      }
    });
    // enable autopan and start location display
    locationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
    locationDisplay.startAsync();
    // set up activity to handle authentication
    DefaultAuthenticationChallengeHandler authenticationChallengeHandler = new DefaultAuthenticationChallengeHandler(
        this);
    // set the challenge handler onto the AuthenticationManager
    AuthenticationManager.setAuthenticationChallengeHandler(authenticationChallengeHandler);
    // create and load a route task from the world routing service. This will trigger logging in to your AGOL account
    mRouteTask = new RouteTask(this, getString(R.string.world_routing_service_url));
    mRouteTask.loadAsync();
    // enable the user to specify a route once the service is ready
    mRouteTask.addDoneLoadingListener(() -> {
      if (mRouteTask.getLoadStatus() == LoadStatus.LOADED) {
        enableTapToPlace();
      } else {
        String error = "Error connecting to route service: " + mRouteTask.getLoadError().getMessage();
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        Log.e(TAG, error);
        mHelpLabel.setText(getString(R.string.route_failed_error_message));
      }
    });
    // create a graphics overlay for showing the calculated route and add it to the map view
    mRouteOverlay = new GraphicsOverlay();
    SimpleLineSymbol routeSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.YELLOW, 1);
    SimpleRenderer routeRenderer = new SimpleRenderer(routeSymbol);
    mRouteOverlay.setRenderer(routeRenderer);
    mMapView.getGraphicsOverlays().add(mRouteOverlay);
    // create and configure an overlay for showing the route's stops and add it to the map view
    mStopsOverlay = new GraphicsOverlay();
    SimpleMarkerSymbol stopSymbol = new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.RED, 5);
    SimpleRenderer stopRenderer = new SimpleRenderer(stopSymbol);
    mStopsOverlay.setRenderer(stopRenderer);
    mMapView.getGraphicsOverlays().add(mStopsOverlay);
  }

  private void enableTapToPlace() {
    // notify the user to place start point
    mHelpLabel.setText(R.string.place_start_message);

    // on single tap
    mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
      @Override
      public boolean onSingleTapConfirmed(MotionEvent e) {
        // if no start point has been defined
        if (mStartPoint == null) {
          // create a start point at the tapped point
          mStartPoint = MainActivity.this.mMapView.screenToLocation(new android.graphics.Point(Math.round(e.getX()), Math.round(e.getY())));
          Graphic graphic = new Graphic(mStartPoint);
          mStopsOverlay.getGraphics().add(graphic);
          // notify user to place end point
          mHelpLabel.setText(R.string.place_end_message);
          // if no end point has been defined
        } else if (mEndPoint == null) {
          // crate an end point at the tapped point
          mEndPoint = MainActivity.this.mMapView.screenToLocation(new android.graphics.Point(Math.round(e.getX()), Math.round(e.getY())));
          Graphic graphic = new Graphic(mEndPoint);
          mStopsOverlay.getGraphics().add(graphic);
          // solve the route between the two points
          solveRoute();
        }
        return true;
      }
    });
  }

  private void enableNavigation() {
    mNavigateButton.setOnClickListener(v -> {
      // TODO - this seems bad, but route doesn't implement serializable so..
      // I'd just pass the start and end point, but I don't want to have to re-connect to the route service
      // and re-calculate in the AR activity
      ARNavigateActivity.routeParameters = mRouteParameters;
      ARNavigateActivity.routeResult = mRouteResult;
      ARNavigateActivity.routeTask = mRouteTask;

      // Pass route to activity and navigate
      Intent myIntent = new Intent(MainActivity.this, ARNavigateActivity.class);
      Bundle bundle = new Bundle();
      startActivity(myIntent, bundle);
    });

    mNavigateButton.setVisibility(View.VISIBLE);
    mHelpLabel.setText(R.string.nav_ready_message);
  }

  private void solveRoute() {
    // Update UI
    mHelpLabel.setText(R.string.solving_route_message);

    final ListenableFuture<RouteParameters> listenableFuture = mRouteTask.createDefaultParametersAsync();
    listenableFuture.addDoneListener(() -> {
      try {
        if (listenableFuture.isDone()) {
          mRouteParameters = listenableFuture.get();

          // Parameters needed for navigation (happens in ARNavigate)
          mRouteParameters.setReturnStops(true);
          mRouteParameters.setReturnDirections(true);
          mRouteParameters.setReturnRoutes(true);

          // This sample is intended for navigating while walking only
          List<TravelMode> travelModes = mRouteTask.getRouteTaskInfo().getTravelModes();
          TravelMode walkingMode = travelModes.get(0);
          // TODO - streams aren't allowed???
          // TravelMode walkingMode = travelModes.stream().filter(tm -> tm.getName().contains("Walking")).findFirst();
          for (TravelMode tm : travelModes) {
            if (tm.getName().contains("Walking")) {
              walkingMode = tm;
              break;
            }
          }

          mRouteParameters.setTravelMode(walkingMode);

          // create stops
          Stop stop1 = new Stop(mStartPoint);
          Stop stop2 = new Stop(mEndPoint);

          List<Stop> routeStops = new ArrayList<>();

          // add stops
          routeStops.add(stop1);
          routeStops.add(stop2);
          mRouteParameters.setStops(routeStops);

          // set return directions as true to return turn-by-turn directions in the result of
          mRouteParameters.setReturnDirections(true);

          // solve
          mRouteResult = mRouteTask.solveRouteAsync(mRouteParameters).get();
          final List routes = mRouteResult.getRoutes();
          mRoute = (Route) routes.get(0);
          // create a mRouteSymbol graphic
          Graphic routeGraphic = new Graphic(mRoute.getRouteGeometry());
          // add mRouteSymbol graphic to the map
          mRouteOverlay.getGraphics().add(routeGraphic);

          enableNavigation();
        }
      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
      }
    });
  }

  /**
   * Request read external storage for API level 23+.
   */
  private void requestPermissions() {
    // define permission to request
    String[] reqPermission = { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION };
    int requestCode = 2;
    if (ContextCompat.checkSelfPermission(this, reqPermission[0]) == PackageManager.PERMISSION_GRANTED
        && ContextCompat.checkSelfPermission(this, reqPermission[1]) == PackageManager.PERMISSION_GRANTED) {
      initialize();
    } else {
      // request permission
      ActivityCompat.requestPermissions(this, reqPermission, requestCode);
    }
  }

  /**
   * Handle the permissions request response.
   */
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
      initialize();
    } else {
      // report to user that permission was denied
      Toast.makeText(this, getString(R.string.navigate_ar_permission_denied), Toast.LENGTH_SHORT).show();
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  @Override
  protected void onPause() {
    AuthenticationManager.CredentialCache.clear();
    AuthenticationManager.clearOAuthConfigurations();

    super.onPause();
  }
}
