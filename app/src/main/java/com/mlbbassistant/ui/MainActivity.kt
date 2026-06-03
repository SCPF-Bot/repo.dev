package com.mlbbassistant.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.mlbbassistant.R
import com.mlbbassistant.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var navController: NavController? = null

    companion object { private const val TAG = "MainActivity" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
    }

    private fun setupNavigation() {
        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                ?: run { Log.e(TAG, "NavHostFragment not found"); return }

            navController = navHostFragment.navController
            binding.bottomNavigation.setupWithNavController(navHostFragment.navController)
        } catch (e: Exception) {
            Log.e(TAG, "Navigation setup failed", e)
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController?.navigateUp() ?: super.onSupportNavigateUp()
}
