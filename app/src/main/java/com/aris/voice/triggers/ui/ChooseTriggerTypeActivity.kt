package com.aris.voice.triggers.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.aris.voice.R
import com.aris.voice.triggers.TriggerType

class ChooseTriggerTypeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_trigger_type)
        com.aris.voice.utilities.ThemeManager.applyTheme(this)

        findViewById<android.widget.ImageView>(R.id.btn_back).setOnClickListener {
            onBackPressed()
        }

        findViewById<android.widget.LinearLayout>(R.id.scheduledTimeCard).setOnClickListener {
            launchCreateTriggerActivity(TriggerType.SCHEDULED_TIME)
        }

        findViewById<android.widget.LinearLayout>(R.id.notificationCard).setOnClickListener {
            launchCreateTriggerActivity(TriggerType.NOTIFICATION)
        }

        findViewById<android.widget.LinearLayout>(R.id.chargingStateCard).setOnClickListener {
            launchCreateTriggerActivity(TriggerType.CHARGING_STATE)
        }
    }

    private fun launchCreateTriggerActivity(triggerType: TriggerType) {
        val intent = Intent(this, CreateTriggerActivity::class.java).apply {
            putExtra("EXTRA_TRIGGER_TYPE", triggerType)
        }
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
