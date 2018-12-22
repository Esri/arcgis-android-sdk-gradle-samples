/* Copyright 2018 Esri
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

package com.esri.android.samples.iwasampleapp

import java.util.concurrent.CountDownLatch

import android.app.Activity
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast

import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.loadable.Loadable
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.portal.Portal
import com.esri.arcgisruntime.security.AuthenticationChallenge
import com.esri.arcgisruntime.security.AuthenticationChallengeHandler
import com.esri.arcgisruntime.security.AuthenticationChallengeResponse
import com.esri.arcgisruntime.security.AuthenticationManager
import com.esri.arcgisruntime.security.UserCredential

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.credential_dialog.view.*

/**
 * This sample app shows how to use a custom authentication challenge handler to work with
 * IWA portals. 
 * 
 * 1. Enter url of the portal you want to connect in the edit text box. The url of a default test portal has
 * been set to the box.
 * 2. Tap on the "Sign In" button to connect to the portal as a named user. A dialog will be popped up for
 * username/password. When a correct credential is passed, portal will be loaded. If you keep passing in a
 * wrong credential, no more prompt for a credential will occur after the default number of attempts. 
 * Then portal will fail to load. You can click the "Cancel" button to cancel the sign-in process.
 * Portal will be failed to load with different error message.
 */
class MainActivity : AppCompatActivity() {

    private val TAG = MainActivity::class.java.simpleName
    
    private lateinit var map: ArcGISMap
    private lateinit var portal: Portal
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // create a Streets BaseMap
        map = ArcGISMap(Basemap.createStreets())
        // set the map to be displayed in this view
        mapView.map = map
        
        // set authentication challenge handler
        AuthenticationManager.setAuthenticationChallengeHandler(IWACustomChallengeHandler(this))
        
        // sign in a portal
        signinButton.setOnClickListener {
            // validate portal url
            val url: String? = portalUrl.text.toString()
            if (url.isNullOrEmpty()) {
                val errorMessage = "Portal url is empty. Please enter portal url!"
                Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_LONG).show()
                Log.e(TAG, errorMessage)
                
                return@setOnClickListener
            }

            // create a Portal object with loginRequired as true, which forces user to sign in
            portal = Portal(url, true)
            // IWACustomChallengeHandler handleChallenge() will be invoked when we try to load the portal
            // as loginRequired is true for the Portal instance
            portal.loadAsync()
            portal.addDoneLoadingListener {
                if (portal.loadStatus == LoadStatus.LOADED) {
                    // portal is loaded. Add more logic here
                    Toast.makeText(applicationContext, "Portal is loaded!", Toast.LENGTH_LONG).show()
                } else {
                    // portal is failed to load. Handle load errors
                    val error = portal.loadError
                    val errorMessage = if (error.errorCode == 17) {
                        // user canceled exception
                        "Portal sign in was cancelled by user!"
                    } else {
                        // other failures
                        "Portal sign in failed: " + (error.cause?.message ?: error.message)
                    }
                    Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_LONG).show()
                    Log.e(TAG, errorMessage)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.dispose()
    }
}

/**
 * A custom authentication challenge handler to handle user credential challenges.
 * 
 * When a user credential challenge is issued, a dialog will be presented to the user, to provide a credential.
 * The portal url will be displayed as a message in the dialog. If a wrong credential has been
 * passed in the previous attempt, a different message will be displayed in the dialog.
 * The dialog has two edit text boxes for username and password respectively. Other SDKs'
 * samples may have one more parameter for IWA domain. As indicated by the Javadoc of UseCredential
 * the SDK is in favor of passing username as username@domain. This sample doesn't provide
 * an edit text box for the domain for simplicity.
 *  
 * @see <a href="https://developers.arcgis.com/android/latest/api-reference/reference/com/esri/arcgisruntime/security/UserCredential.html#UserCredential(java.lang.String,%20java.lang.String)">Javadoc of UserCredential</a>
 * 
 */
class IWACustomChallengeHandler(val activity: Activity) : AuthenticationChallengeHandler {

    private val maxAttempts = 5
    
    override fun handleChallenge(challenge: AuthenticationChallenge): AuthenticationChallengeResponse {
        if (challenge.type == AuthenticationChallenge.Type.USER_CREDENTIAL_CHALLENGE) {
            if (challenge.failureCount > maxAttempts) {
                // exceeded maximum amount of attempts. Act like it was a cancel
                Toast.makeText(activity, "Exceeded maximum amount of attempts. Please try again!", Toast.LENGTH_LONG).show()
                return AuthenticationChallengeResponse(AuthenticationChallengeResponse.Action.CANCEL, challenge)
            }
            
            // create a countdown latch with a count of one to synchronize the dialog
            val signal = CountDownLatch(1)
            var credential: UserCredential? = null
            
            // present the sign-in dialog
            activity.runOnUiThread {
                // inflate the layout
                val dialogView = activity.layoutInflater.inflate(R.layout.credential_dialog, null)
                // create the dialog
                val builder: AlertDialog.Builder = AlertDialog.Builder(activity)
                val hostname = dialogView.auth_hostname
                val username = dialogView.auth_username
                val password = dialogView.auth_password
                // set click listeners
                builder.setPositiveButton("Sign In") {dialog, which ->
                            // create user credential
                            credential = UserCredential(username.text.toString(), password.text.toString())
                            signal.countDown()
                        }
                        .setNegativeButton("Cancel") {dialog, which ->
                            // user cancelled the signin process
                            val remoteResource = challenge.remoteResource
                            if (remoteResource is Loadable) {
                                remoteResource.cancelLoad()
                            }
                            signal.countDown()
                        }
                        .setView(dialogView)
                // set message text
                if (challenge.failureCount > 0) {
                    hostname.setText("Wrong credential was passed to ${challenge.remoteResource.uri}")
                } else {
                    hostname.setText("Credential is required to access ${challenge.remoteResource.uri}")
                }
                
                val dialog: AlertDialog = builder.create()
                dialog.setCanceledOnTouchOutside(false)
                dialog.show()
                // apply the button texts and disable the positive button unless both username and password contain text
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                val watcher = object : TextWatcher {
                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                    override fun afterTextChanged(s: Editable) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = 
                                (username.text.length > 0) && (password.text.length > 0)
                    }
                }
                username.addTextChangedListener(watcher)
                password.addTextChangedListener(watcher)
            }
            signal.await()

            // if credentials were set, return a new auth challenge response with them. otherwise, act like it was a cancel
            if (credential != null) {
                return AuthenticationChallengeResponse(
                        AuthenticationChallengeResponse.Action.CONTINUE_WITH_CREDENTIAL, credential)
            }
        }

        // no credentials were set , return a new auth challenge response with a cancel
        return AuthenticationChallengeResponse(AuthenticationChallengeResponse.Action.CANCEL, challenge)
    }
}
