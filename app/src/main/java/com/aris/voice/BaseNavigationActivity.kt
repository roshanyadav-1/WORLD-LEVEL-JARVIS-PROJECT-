package com.aris.voice

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

abstract class BaseNavigationActivity : AppCompatActivity() {

    protected abstract fun getContentLayoutId(): Int
    protected abstract fun getCurrentNavItem(): NavItem

    enum class NavItem {
        HOME, TRIGGERS, MOMENTS, UPGRADE, SETTINGS, AIR_GESTURES, HYBRID
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Disable any default activity transitions
        disableTransitions()
    }
    
    override fun setContentView(layoutResID: Int) {
        super.setContentView(R.layout.activity_base_navigation)
        
        // Inflate the child activity's content into the content container
        val contentContainer = findViewById<LinearLayout>(R.id.content_container)
        layoutInflater.inflate(layoutResID, contentContainer, true)
        
        setupDrawerNavigation()
        com.aris.voice.utilities.ThemeManager.applyTheme(this)
    }

    override fun onResume() {
        super.onResume()
        com.aris.voice.utilities.ThemeManager.applyTheme(this)
    }

    private fun setupDrawerNavigation() {
        val currentItem = getCurrentNavItem()
        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
        
        findViewById<android.widget.ImageView>(R.id.btn_open_drawer)?.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
        
        fun handleNavClick(item: NavItem, clazz: Class<*>) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            if (currentItem != item) {
                navigateToActivity(clazz, currentItem)
            }
        }
        
        findViewById<LinearLayout>(R.id.nav_triggers).apply {
            setOnClickListener { handleNavClick(NavItem.TRIGGERS, com.aris.voice.triggers.ui.TriggersActivity::class.java) }
            alpha = if (currentItem == NavItem.TRIGGERS) 1.0f else 0.5f
        }
        
        findViewById<LinearLayout>(R.id.nav_moments).apply {
            setOnClickListener { handleNavClick(NavItem.MOMENTS, MomentsActivity::class.java) }
            alpha = if (currentItem == NavItem.MOMENTS) 1.0f else 0.5f
        }

        findViewById<LinearLayout>(R.id.nav_air_gestures)?.apply {
            setOnClickListener { handleNavClick(NavItem.AIR_GESTURES, GestureControlActivity::class.java) }
            alpha = if (currentItem == NavItem.AIR_GESTURES) 1.0f else 0.5f
        }
        
        findViewById<LinearLayout>(R.id.nav_home).apply {
            setOnClickListener { handleNavClick(NavItem.HOME, MainActivity::class.java) }
            alpha = if (currentItem == NavItem.HOME) 1.0f else 0.5f
        }
        
        findViewById<LinearLayout>(R.id.nav_upgrade).apply {
            setOnClickListener { handleNavClick(NavItem.UPGRADE, ProPurchaseActivity::class.java) }
            alpha = if (currentItem == NavItem.UPGRADE) 1.0f else 0.7f // Keep a bit brighter
        }

        findViewById<LinearLayout>(R.id.nav_hybrid)?.apply {
            setOnClickListener { handleNavClick(NavItem.HYBRID, HybridAutomationActivity::class.java) }
            alpha = if (currentItem == NavItem.HYBRID) 1.0f else 0.5f
        }
        
        findViewById<LinearLayout>(R.id.nav_settings).apply {
            setOnClickListener { handleNavClick(NavItem.SETTINGS, SettingsActivity::class.java) }
            alpha = if (currentItem == NavItem.SETTINGS) 1.0f else 0.5f
        }
    }
    
    private fun navigateToActivity(activityClass: Class<*>, currentItem: NavItem) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        // Disable transition animations
        disableTransitions()
        if (currentItem != NavItem.HOME) {
            finish()
            // Also disable animations when finishing
            disableTransitions()
        }
    }
    
    override fun finish() {
        super.finish()
        // Disable animations when finishing
        disableTransitions()
    }
    
    @Suppress("DEPRECATION")
    private fun disableTransitions() {
        // Use the legacy method for all Android versions since the new API
        // requires more complex setup and this works reliably
        overridePendingTransition(0, 0)
    }
}