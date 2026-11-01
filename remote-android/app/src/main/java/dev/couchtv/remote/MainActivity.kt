package dev.couchtv.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.couchtv.remote.ui.CouchTheme
import dev.couchtv.remote.ui.RemoteRoot
import dev.couchtv.remote.ui.RemoteViewModel

/**
 * One activity, one Compose tree, no fragments. The remote is a single surface; splitting it into
 * activities would only add transitions between things that belong to the same gesture.
 */
class MainActivity : ComponentActivity() {

    private val model: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CouchTheme {
                RemoteRoot(model = model)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Returning to the app is the moment to retry a dropped link, not on a timer.
        model.foregrounded()
    }

    override fun onDestroy() {
        if (isFinishing) model.stopDiscovery()
        super.onDestroy()
    }
}
