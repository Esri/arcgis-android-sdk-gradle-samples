/* Copyright 2017 Esri
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

package com.esri.arcgisruntime.sample.findplace;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.Multipoint;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.IdentifyGraphicsOverlayResult;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.mapping.view.ViewpointChangedEvent;
import com.esri.arcgisruntime.mapping.view.ViewpointChangedListener;
import com.esri.arcgisruntime.mapping.view.WrapAroundMode;
import com.esri.arcgisruntime.symbology.PictureMarkerSymbol;
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters;
import com.esri.arcgisruntime.tasks.geocode.GeocodeResult;
import com.esri.arcgisruntime.tasks.geocode.LocatorTask;
import com.esri.arcgisruntime.tasks.geocode.SuggestParameters;
import com.esri.arcgisruntime.tasks.geocode.SuggestResult;

public class MainActivity extends AppCompatActivity {

  String[] reqPermissions =
      new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION };
  private int requestCode = 2;
  private String TAG;

  private SearchView mPoiSearchView;
  private SearchView mLocationSearchView;

  private final String COLUMN_NAME_ADDRESS = "address";
  private String[] mColumnNames = { BaseColumns._ID, COLUMN_NAME_ADDRESS };

  private String mPoiAddress;
  private Point mPreferredSearchLocation;

  private MapView mMapView;
  private LocationDisplay mLocationDisplay;
  private LocatorTask mLocatorTask;
  private GraphicsOverlay mGraphicsOverlay;
  private SuggestParameters mPoiSuggestParameters;
  private GeocodeParameters mPoiGeocodeParameters;
  private SuggestParameters mLocationSuggestParameters;
  private GeocodeParameters mLocationGeocodeParameters;
  private PictureMarkerSymbol mPinSourceSymbol;
  private Geometry mCurrentExtentGeometry;
  private Callout mCallout;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    TAG = "Find a place";

    // if permissions are not already granted, request permission from the user
    if (!(ContextCompat.checkSelfPermission(MainActivity.this, reqPermissions[0]) == PackageManager.PERMISSION_GRANTED
        && ContextCompat.checkSelfPermission(MainActivity.this, reqPermissions[1]) == PackageManager.PERMISSION_GRANTED)) {
      ActivityCompat.requestPermissions(MainActivity.this, reqPermissions, requestCode);
    }

    // setup the two SearchViews and show text hint
    mPoiSearchView = (SearchView) findViewById(R.id.search_searchView);
    mPoiSearchView.setIconified(false);
    mPoiSearchView.setFocusable(false);
    mPoiSearchView.setQueryHint(getResources().getString(R.string.search_hint));
    mLocationSearchView = (SearchView) findViewById(R.id.location_searchView);
    mLocationSearchView.setIconified(false);
    mLocationSearchView.setFocusable(false);
    mLocationSearchView.setQueryHint(getResources().getString(R.string.location_search_hint));
    // on redo button click call redoSearchInThisArea
    Button redoSearchButton = (Button) findViewById(R.id.redo_search_button);
    redoSearchButton.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        redoSearchInThisArea();
      }
    });

    // define pin drawable
    BitmapDrawable pinDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.pin);
    try {
      mPinSourceSymbol = PictureMarkerSymbol.createAsync(pinDrawable).get();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
      Toast.makeText(getApplicationContext(), "Failed to load pin drawable.", Toast.LENGTH_LONG).show();
    }
    // set pin to half of native size
    mPinSourceSymbol.setWidth(19f);
    mPinSourceSymbol.setHeight(72f);

    // create a LocatorTask from an online service
    mLocatorTask = new LocatorTask("http://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer");

    // inflate MapView from layout
    mMapView = (MapView) findViewById(R.id.mapView);
    // disable map wraparound
    mMapView.setWrapAroundMode(WrapAroundMode.DISABLED);
    // create a map with the BasemapType topographic
    ArcGISMap map = new ArcGISMap(Basemap.createTopographic());
    // set the map to be displayed in this view
    mMapView.setMap(map);
    // add listener to update extent when viewpoint has changed
    mMapView.addViewpointChangedListener(new ViewpointChangedListener() {
      @Override public void viewpointChanged(ViewpointChangedEvent viewpointChangedEvent) {
        if (!mMapView.isNavigating()) {
          // get the current map extent
          mCurrentExtentGeometry = mMapView.getCurrentViewpoint(Viewpoint.Type.BOUNDING_GEOMETRY).getTargetGeometry();
        }
      }
    });
    // add listener to handle callouts
    mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
      @Override
      public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
        showCallout(motionEvent);
        return true;
      }
    });
    // setup and start location display
    mLocationDisplay = mMapView.getLocationDisplay();
    mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
    mLocationDisplay.startAsync();
    // initially use device location to focus POI search
    mLocationDisplay.addLocationChangedListener(new LocationDisplay.LocationChangedListener() {
      @Override public void onLocationChanged(LocationDisplay.LocationChangedEvent locationChangedEvent) {
        mPoiGeocodeParameters.setPreferredSearchLocation(mLocationDisplay.getMapLocation());
      }
    });
    // define the graphics overlay
    mGraphicsOverlay = new GraphicsOverlay();

    setupPoi();
    setupLocation();
  }

  /**
   * Sets up the POI SearchView. Uses MatrixCursor to show suggestions to the user as the user inputs text.
   */
  private void setupPoi() {

    mPoiSuggestParameters = new SuggestParameters();
    // filter categories for POI
    mPoiSuggestParameters.getCategories().add("POI");
    mPoiGeocodeParameters = new GeocodeParameters();
    // get all attributes
    mPoiGeocodeParameters.getResultAttributeNames().add("*");
    mPoiSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

      @Override
      public boolean onQueryTextSubmit(String address) {
        geoCodeTypedAddress(address);
        // clear focus from search views
        mPoiSearchView.clearFocus();
        mLocationSearchView.clearFocus();
        return true;
      }

      @Override
      public boolean onQueryTextChange(String newText) {
        // as long as newText isn't empty, get suggestions from the locatorTask
        if (!newText.equals("")) {
          mPoiSuggestParameters.setSearchArea(mCurrentExtentGeometry);
          final ListenableFuture<List<SuggestResult>> suggestionsFuture = mLocatorTask.suggestAsync(newText, mPoiSuggestParameters);
          suggestionsFuture.addDoneListener(new Runnable() {

            @Override public void run() {
              try {
                // get the results of the async operation
                List<SuggestResult> suggestResults = suggestionsFuture.get();
                MatrixCursor suggestionsCursor = new MatrixCursor(mColumnNames);
                int key = 0;
                // add each poi_suggestion result to a new row
                for (SuggestResult result : suggestResults) {
                  suggestionsCursor.addRow(new Object[] { key++, result.getLabel() });
                }
                // define SimpleCursorAdapter
                String[] cols = new String[] { COLUMN_NAME_ADDRESS };
                int[] to = new int[] { R.id.suggestion_address };
                final SimpleCursorAdapter suggestionAdapter = new SimpleCursorAdapter(MainActivity.this,
                    R.layout.suggestion, suggestionsCursor, cols, to, 0);
                mPoiSearchView.setSuggestionsAdapter(suggestionAdapter);
                // handle a poi_suggestion being chosen
                mPoiSearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
                  @Override public boolean onSuggestionSelect(int position) {
                    return false;
                  }

                  @Override public boolean onSuggestionClick(int position) {
                    // get the selected row
                    MatrixCursor selectedRow = (MatrixCursor) suggestionAdapter.getItem(position);
                    // get the row's index
                    int selectedCursorIndex = selectedRow.getColumnIndex(COLUMN_NAME_ADDRESS);
                    // get the string from the row at index
                    mPoiAddress = selectedRow.getString(selectedCursorIndex);
                    mPoiSearchView.setQuery(mPoiAddress, true);
                    return true;
                  }
                });
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          });
        }
        return true;
      }
    });
  }

  private void setupLocation() {

    mLocationSuggestParameters = new SuggestParameters();
    mLocationGeocodeParameters = new GeocodeParameters();
    // get all attributes
    mLocationGeocodeParameters.getResultAttributeNames().add("*");
    mLocationSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
      @Override public boolean onQueryTextSubmit(String address) {
        geoCodeTypedAddress(address);
        // clear focus from search views
        mPoiSearchView.clearFocus();
        mLocationSearchView.clearFocus();
        return true;
      }

      @Override public boolean onQueryTextChange(String newText) {
        // as long as newText isn't empty, get suggestions from the locatorTask
        if (!newText.equals("")) {
          final ListenableFuture<List<SuggestResult>> suggestionsFuture = mLocatorTask.suggestAsync(newText, mLocationSuggestParameters);
          suggestionsFuture.addDoneListener(new Runnable() {

            @Override public void run() {
              try {
                // get the list of suggestions
                List<SuggestResult> suggestResults = suggestionsFuture.get();
                MatrixCursor suggestionsCursor = new MatrixCursor(mColumnNames);
                int key = 0;
                // add each SuggestResult to a new row
                for (SuggestResult result : suggestResults) {
                  suggestionsCursor.addRow(new Object[] { key++, result.getLabel() });
                }
                // define SimpleCursorAdapter
                String[] cols = new String[] { COLUMN_NAME_ADDRESS };
                int[] to = new int[] { R.id.suggestion_address };
                final SimpleCursorAdapter suggestionAdapter = new SimpleCursorAdapter(MainActivity.this,
                    R.layout.suggestion, suggestionsCursor, cols, to, 0);
                mLocationSearchView.setSuggestionsAdapter(suggestionAdapter);
                mLocationSearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
                  @Override public boolean onSuggestionSelect(int position) {
                    return false;
                  }

                  @Override public boolean onSuggestionClick(int position) {
                    // get the selected row
                    MatrixCursor selectedRow = (MatrixCursor) suggestionAdapter.getItem(position);
                    // get the row's index
                    int selectedCursorIndex = selectedRow.getColumnIndex(COLUMN_NAME_ADDRESS);
                    // get the string from the row at index
                    final String address = selectedRow.getString(selectedCursorIndex);
                    mLocatorTask.addDoneLoadingListener(new Runnable() {
                      @Override
                      public void run() {
                        if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
                          // geocode the selected address to get access to the resulting display location
                          final ListenableFuture<List<GeocodeResult>> geocodeFuture = mLocatorTask
                              .geocodeAsync(address, mLocationGeocodeParameters);
                          geocodeFuture.addDoneListener(new Runnable() {
                            @Override
                            public void run() {
                              try {
                                // Get the results of the async operation
                                List<GeocodeResult> geocodeResults = geocodeFuture.get();
                                if (geocodeResults.size() > 0) {
                                  // use geocodeResult to focus search area
                                  GeocodeResult geocodeResult = geocodeResults.get(0);
                                  mPoiGeocodeParameters
                                      .setPreferredSearchLocation(geocodeResult.getDisplayLocation());
                                  mPoiGeocodeParameters.setSearchArea(geocodeResult.getDisplayLocation());
                                  // set the address string to the SearchView, but don't submit as a query
                                  mLocationSearchView.setQuery(address, false);
                                  // call search query
                                  mPoiSearchView.setQuery(mPoiAddress, true);
                                  mLocationSearchView.clearFocus();
                                  mPoiSearchView.clearFocus();
                                } else {
                                  Toast.makeText(getApplicationContext(),
                                      getString(R.string.location_not_found) + address, Toast.LENGTH_LONG).show();
                                }
                              } catch (InterruptedException | ExecutionException e) {
                                e.printStackTrace();
                                Toast.makeText(getApplicationContext(), getString(R.string.geo_locate_error),
                                    Toast.LENGTH_LONG).show();
                              }
                              // done processing and can remove this listener
                              geocodeFuture.removeDoneListener(this);
                            }
                          });
                        }
                      }
                    });

                    return true;
                  }
                });
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          });
        } else {
          // if no location selected, use device location
          mPoiGeocodeParameters.setPreferredSearchLocation(mLocationDisplay.getMapLocation());
        }
        return true;
      }
    });
  }

  /**
   * Performs a search for the POI listed in the SearchView, using the MapView's current extent to inform the search.
   */

  private void redoSearchInThisArea() {
    // set center of current extent to preferred search location
    mPoiGeocodeParameters.setPreferredSearchLocation(mCurrentExtentGeometry.getExtent().getCenter());
    mPoiGeocodeParameters.setSearchArea(mCurrentExtentGeometry);
    mLocationSearchView.setQuery(getString(R.string.searching_by_area), false);
    // use whatever text is in the search box to call geoCodeTypedAddress
    geoCodeTypedAddress(mPoiSearchView.getQuery().toString());
  }

  /**
   * Identifies the Graphic at the clicked point. Gets attribute of that Graphic and assigns it to a Callout, which is
   * then displayed.
   *
   * @param motionEvent from onSingleTapConfirmed
   */
  private void showCallout(MotionEvent motionEvent) {
    // get the screen point
    android.graphics.Point screenPoint = new android.graphics.Point(Math.round(motionEvent.getX()),
        Math.round(motionEvent.getY()));
    // convert to map point
    final Point mapPoint = mMapView.screenToLocation(screenPoint);
    // from the graphics overlay, get graphics near the tapped location
    final ListenableFuture<IdentifyGraphicsOverlayResult> identifyResultsFuture = mMapView
        .identifyGraphicsOverlayAsync(mGraphicsOverlay, screenPoint, 10, false);
    identifyResultsFuture.addDoneListener(new Runnable() {
      @Override public void run() {
        try {
          IdentifyGraphicsOverlayResult identifyGraphicsOverlayResult = identifyResultsFuture.get();
          List<Graphic> graphics = identifyGraphicsOverlayResult.getGraphics();
          // if a graphic has been identified
          if (graphics.size() > 0) {
            //get the first graphic identified
            Graphic identifiedGraphic = graphics.get(0);
            // create a TextView for the Callout
            TextView calloutContent = new TextView(getApplicationContext());
            calloutContent.setTextColor(Color.BLACK);
            // set the text of the Callout to graphic's attributes
            calloutContent.setText(identifiedGraphic.getAttributes().get("PlaceName").toString() + "\n"
                + identifiedGraphic.getAttributes().get("StAddr").toString());
            // get Callout and set its options: animateCallout: true, recenterMap: false, animateRecenter: false
            mCallout = mMapView.getCallout();
            mCallout.setShowOptions(new Callout.ShowOptions(true, false, false));
            // set the leader position and show the callout
            mCallout.setLocation(identifiedGraphic.computeCalloutLocation(mapPoint, mMapView));
            mCallout.setContent(calloutContent);
            mCallout.show();
          } else {
            mCallout.dismiss();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  /**
   * Geocode an address typed in by user
   *
   * @param address
   */
  private void geoCodeTypedAddress(final String address) {
    // Execute async task to find the address
    mLocatorTask.addDoneLoadingListener(new Runnable() {
      @Override
      public void run() {
        if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
          // Call geocodeAsync passing in an address
          final ListenableFuture<List<GeocodeResult>> geocodeResultListenableFuture = mLocatorTask
              .geocodeAsync(address, mPoiGeocodeParameters);
          geocodeResultListenableFuture.addDoneListener(new Runnable() {
            @Override
            public void run() {
              try {
                // Get the results of the async operation
                List<GeocodeResult> geocodeResults = geocodeResultListenableFuture.get();
                if (geocodeResults.size() > 0) {
                  displaySearchResult(geocodeResults);
                } else {
                  Toast.makeText(getApplicationContext(), getString(R.string.location_not_found) + address,
                      Toast.LENGTH_LONG).show();
                }
              } catch (InterruptedException | ExecutionException e) {
                // Deal with exception...
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), getString(R.string.geo_locate_error), Toast.LENGTH_LONG).show();
              }
            }
          });
        } else {
          Log.i(TAG, "Trying to reload locator task");
          mLocatorTask.retryLoadAsync();
        }
      }
    });
    mLocatorTask.loadAsync();
  }

  /**
   * Turns a list of GeocodeResults into Points and adds them to a GraphicOverlay which is then drawn on the map. The
   * points are added to a multipoint used to calculate a viewpoint
   *
   * @param geocodeResults list of geocode results
   */
  private void displaySearchResult(List<GeocodeResult> geocodeResults) {
    // dismiss any callout and clear map of any existing graphics
    if (mMapView.getCallout().isShowing()) {
      mMapView.getCallout().dismiss();
    }
    mMapView.getGraphicsOverlays().clear();
    mGraphicsOverlay.getGraphics().clear();
    // create a list of points from the geocode results
    List<Point> resultPoints = new ArrayList<>();
    for (GeocodeResult result : geocodeResults) {
      // create graphic object for resulting location
      Point resultPoint = result.getDisplayLocation();
      Graphic resultLocGraphic = new Graphic(resultPoint, result.getAttributes(), mPinSourceSymbol);
      // add graphic to location layer
      mGraphicsOverlay.getGraphics().add(resultLocGraphic);
      resultPoints.add(resultPoint);
    }
    // add result points to a Multipoint and get an envelope surrounding it
    Multipoint resultsMultipoint = new Multipoint(resultPoints);
    Envelope resultsEnvelope = resultsMultipoint.getExtent();
    // add a 25% buffer to the extent Envelope of result points
    Envelope resultsEnvelopeWithBuffer = new Envelope(resultsEnvelope.getCenter(), resultsEnvelope.getWidth() * 1.25,
        resultsEnvelope.getHeight() * 1.25);
    // zoom map to result over 3 seconds
    mMapView.setViewpointAsync(new Viewpoint(resultsEnvelopeWithBuffer), 3);
    // set the graphics overlay to the map
    mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    // if request is cancelled, the result arrays are empty
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      mLocationDisplay.startAsync();
    } else {
      // if permission was denied, show toast to inform user what was chosen
      Toast.makeText(MainActivity.this, getResources().getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show();
    }
  }
}
