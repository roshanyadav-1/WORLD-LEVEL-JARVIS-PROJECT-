package com.aris.voice.utilities

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.aris.voice.R

object ThemeManager {
    const val PREFS_NAME = "ArisSettings"
    const val KEY_THEME = "app_theme"
    
    const val THEME_DEFAULT = "default" // Silver Dark - Sleek obsidian carbon with glowing platinum chrome accents
    const val THEME_DARK = "dark"       // Carbon Obsidian - Absolute velvet dark with brushed graphite accents
    const val THEME_LIGHT = "light"     // Cyber Titanium - Premium high-contrast silver plate layout
    
    fun getSelectedTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, THEME_DEFAULT) ?: THEME_DEFAULT
    }

    fun setSelectedTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme).apply()
    }
    
    class ThemeColors(
        val isThemeDark: Boolean,
        val bgGradStart: Int,
        val bgGradEnd: Int,
        val panelColor: Int,
        val textColor: Int,
        val subTextColor: Int,
        val accentColor: Int,
        val borderColor: Int
    )

    fun getThemeColors(themeName: String): ThemeColors {
        return when (themeName) {
            THEME_DARK -> ThemeColors(
                isThemeDark = true,
                bgGradStart = 0xFF121212.toInt(), // Deep charcoal backgrounds (#121212)
                bgGradEnd = 0xFF18181B.toInt(),
                panelColor = 0xF21C1C1F.toInt(),
                textColor = 0xFFF1F5F9.toInt(), // Soft white text
                subTextColor = 0xFF94A3B8.toInt(),
                accentColor = 0xFFB0B0B0.toInt(), // Metallic silver accents (#B0B0B0)
                borderColor = 0xAA475569.toInt()
            )
            THEME_LIGHT -> ThemeColors(
                isThemeDark = true, // Maintain futuristic dark visibility but lighter plates
                bgGradStart = 0xFF1E1E24.toInt(),
                bgGradEnd = 0xFF2D2D35.toInt(),
                panelColor = 0xD8272730.toInt(), // Translucent silver steel
                textColor = 0xFFFFFFFF.toInt(),
                subTextColor = 0xFFCFD8DC.toInt(),
                accentColor = 0xFFB0B0B0.toInt(), // Metallic silver accents (#B0B0B0)
                borderColor = 0xCCD1D5DB.toInt()
            )
            else -> ThemeColors( // THEME_DEFAULT: Silver Dark
                isThemeDark = true,
                bgGradStart = 0xFF121212.toInt(), // Deep charcoal background (#121212)
                bgGradEnd = 0xFF1C1E24.toInt(),   // Slate-silver rich metallic
                panelColor = 0xEE18181C.toInt(),  // Dark carbon panel
                textColor = 0xFFF1F5F9.toInt(),   // Brilliant slate-white text
                subTextColor = 0xFF94A3B8.toInt(),// Charcoal silver subtext
                accentColor = 0xFFB0B0B0.toInt(), // Metallic silver accents (#B0B0B0)
                borderColor = 0x808E9AAB.toInt()  // Metallic slate border
            )
        }
    }
    
    fun applyTheme(activity: Activity) {
        val theme = getSelectedTheme(activity)
        val colors = getThemeColors(theme)
        
        // Render rich gradient background on the window
        val bgDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(colors.bgGradStart, colors.bgGradEnd)
        )
        activity.window.setBackgroundDrawable(bgDrawable)
        
        // Recursively theme the view hierarchy
        val rootLayout = activity.findViewById<ViewGroup>(android.R.id.content)
        if (rootLayout != null) {
            themeViewHierarchy(rootLayout, colors)
        }
        
        // Theme the status and navigation bar
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = colors.bgGradStart
            activity.window.navigationBarColor = colors.bgGradEnd
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val decorView = activity.window.decorView
                @Suppress("DEPRECATION")
                var flags = decorView.systemUiVisibility
                if (!colors.isThemeDark) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                } else {
                    flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                    }
                }
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = flags
            }
        }
    }

    private fun themeViewHierarchy(view: View, colors: ThemeColors) {
        val density = view.resources.displayMetrics.density

        if (view is TextView) {
            try {
                val typeface = androidx.core.content.res.ResourcesCompat.getFont(view.context, R.font.oxanium)
                view.typeface = typeface
            } catch (e: Exception) {}

            when (view.id) {
                R.id.buttonSignOut, R.id.buttonSaveGeminiKeys -> {
                    val btnBg = GradientDrawable().apply {
                        cornerRadius = 24f * density
                        setColor(Color.TRANSPARENT)
                        setStroke(4, colors.accentColor)
                    }
                    view.background = btnBg
                    view.setTextColor(colors.textColor)
                }
                
                R.id.textGetPicovoiceKeyLink, 
                R.id.permissionsInfoButton, 
                R.id.batteryOptimizationHelpButton -> {
                    view.setTextColor(colors.accentColor)
                }
                
                else -> {
                    val textCol = view.currentTextColor
                    if (textCol == Color.WHITE || textCol == Color.BLACK || textCol == 0xFFCECECE.toInt() || textCol == 0xFFBDBDBD.toInt()) {
                        view.setTextColor(colors.textColor)
                    } else if (textCol == 0xFF888888.toInt() || textCol == 0xFF666666.toInt()) {
                        view.setTextColor(colors.subTextColor)
                    } else {
                        view.setTextColor(colors.textColor)
                    }
                }
            }
        }
        
        if (view is LinearLayout || view is androidx.cardview.widget.CardView) {
            val bg = view.background
            if (bg != null || view.id == R.id.pro_upgrade_banner || view is androidx.cardview.widget.CardView) {
                if (view is androidx.cardview.widget.CardView) {
                    view.setCardBackgroundColor(Color.TRANSPARENT)
                    view.cardElevation = 0f
                }
                val panelBg = GradientDrawable().apply {
                    cornerRadius = 24f * density
                    setColor(colors.panelColor)
                    setStroke(2, colors.borderColor)
                }
                view.background = panelBg
            }
        }
        
        if (view is EditText) {
            val fieldBg = GradientDrawable().apply {
                cornerRadius = 16f * density
                setColor(colors.panelColor)
                setStroke(2, colors.borderColor)
            }
            view.background = fieldBg
            view.setTextColor(colors.textColor)
            view.setHintTextColor(colors.subTextColor)
        }
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                themeViewHierarchy(view.getChildAt(i), colors)
            }
        }
    }
}
