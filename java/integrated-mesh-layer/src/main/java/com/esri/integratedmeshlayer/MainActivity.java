/*
 *
 *  * Copyright 2019 Esri.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.esri.integratedmeshlayer;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import com.esri.arcgisruntime.layers.IntegratedMeshLayer;
import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.SceneView;

public class MainActivity extends AppCompatActivity {

  private SceneView mSceneView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // create a scene and add it to the scene view
    mSceneView = findViewById(R.id.sceneView);
    ArcGISScene scene = new ArcGISScene(Basemap.createImagery());
    mSceneView.setScene(scene);

    // create IntegratedMeshLayer and add to the scene's operational layers
    IntegratedMeshLayer integratedMeshLayer = new IntegratedMeshLayer(getString(R.string.mesh_layer_url));
    scene.getOperationalLayers().add(integratedMeshLayer);

    // create a camera and initial camera position
    Camera camera = new Camera(37.720650, -119.622075, 2104.901239, 315.50368761552056, 78.09465920130114, 0.0);

    // set Viewpoint for SceneView using camera
    mSceneView.setViewpointCamera(camera);
  }

  @Override
  protected void onPause() {
    super.onPause();
    mSceneView.pause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mSceneView.resume();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mSceneView.dispose();
  }

}
