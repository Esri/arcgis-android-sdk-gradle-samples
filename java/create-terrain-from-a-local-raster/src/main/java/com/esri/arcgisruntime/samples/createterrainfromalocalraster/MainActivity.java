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
 */

package com.esri.arcgisruntime.samples.createterrainfromalocalraster;

import java.util.ArrayList;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.RasterElevationSource;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.SceneView;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = MainActivity.class.getSimpleName();

  private static final int PERMISSIONS_REQUEST_CODE = 1;

  private static final String[] PERMISSIONS = { Manifest.permission.READ_EXTERNAL_STORAGE };

  private SceneView mSceneView;

  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mSceneView = findViewById(R.id.sceneView);

    // create a scene and add a basemap to it
    ArcGISScene scene = new ArcGISScene();
    scene.setBasemap(Basemap.createImagery());

    // add the scene to the sceneview
    mSceneView.setScene(scene);

    // specify the initial camera position
    Camera camera = new Camera(36.525, -121.80, 300.0, 180, 80.0, 0.0);
    mSceneView.setViewpointCamera(camera);

    requestReadPermission();
  }

  /**
   * Request read external storage for API level 23+.
   */
  private void requestReadPermission() {
    if (ContextCompat.checkSelfPermission(this, PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED) {
      createTiledElevationSource();
    } else {
      // Request permission
      ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSIONS_REQUEST_CODE);
    }
  }

  /**
   * Handle the permissions request response
   */
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      createTiledElevationSource();
    } else {
      // Report to user that permission was denied
      logErrorToUser(getString(R.string.error_read_permission_denied_message));
    }
  }

  private void createTiledElevationSource() {
    // raster package file paths
    ArrayList<String> filePaths = new ArrayList<>();
    filePaths.add(Environment.getExternalStorageDirectory() + getString(R.string.raster_package_location));

    try {
      // Add a RasterElevationSource to the scene by passing the URI of the local tile package to the constructor
      RasterElevationSource rasterElevationSource = new RasterElevationSource(filePaths);

      // Add a listener to perform operations when the load status of the RasterElevationSource changes
      rasterElevationSource.addLoadStatusChangedListener(loadStatusChangedEvent -> {
        // When RasterElevationSource loads
        if (loadStatusChangedEvent.getNewLoadStatus() == LoadStatus.LOADED) {
          // Add the RasterElevationSource to the elevation sources of the scene
          mSceneView.getScene().getBaseSurface().getElevationSources().add(rasterElevationSource);
        } else if (loadStatusChangedEvent.getNewLoadStatus() == LoadStatus.FAILED_TO_LOAD) {
          // Notify user that the RasterElevationSource has failed to load
          logErrorToUser(getString(R.string.error_tiled_elevation_source_load_failure_message, ""));
        }
      });

      // Load the RasterElevationSource asynchronously
      rasterElevationSource.loadAsync();
    } catch (IllegalArgumentException e) {
      // catch exception thrown by RasterElevationSource when a file is invalid/not found
      logErrorToUser(getString(R.string.error_tiled_elevation_source_load_failure_message, e.getMessage()));
    }
  }

  private void logErrorToUser(String message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    Log.e(TAG, message);
  }
}