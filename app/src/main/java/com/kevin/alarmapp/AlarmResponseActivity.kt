// Created By Kevin F Garcia
package com.kevin.alarmapp

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kevin.alarmapp.databinding.ActivityAlarmResponseBinding
import androidx.annotation.RequiresApi
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri

class AlarmResponseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmResponseBinding
    private lateinit var context: Context

    private val singleton = MainActivity.AlarmData.getInstance()

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmResponseBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        context = this

        // !! CHECK THAT THESE ARE NOT NULL !!
        val alarmID = getIntent().getStringExtra("alarmID") as String
        val snooze = getIntent().getBooleanExtra("snooze", true)
        val snoozeTally = getIntent().getIntExtra("snoozeTally", 999)

        val uri =
            if (getIntent().getStringExtra("alarmTone") != "")
                Uri.parse(getIntent().getStringExtra("alarmTone"))
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val ringtone: Ringtone = RingtoneManager.getRingtone(context, uri)

        val hour = (alarmID.takeLast(4)).take(2).toInt()
        val minute = alarmID.takeLast(2)

        // format the hours and am/pm for the textview
        val alarmTime =
            if (hour > 12) { "${hour - 12}:$minute PM" }
            else if (hour == 12) { "12:$minute PM" }
            else if (hour == 0) { "12:$minute AM" }
            else { "$hour:$minute AM" }

        // set the textview for the alarm in this activity
        binding.timeTextResp.setText(alarmTime)

        // this intent is used to remove the notification once the alarm has been stopped
        val intent = Intent(this, MainActivity.Receiver::class.java
        ).setAction("com.kevin.alarmapp.removenotif")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("alarmID", alarmID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            if (keyguardManager.isKeyguardLocked()) {
                keyguardManager.requestDismissKeyguard(this, null)
            }
        }

        // disable the snooze button if necessary
        if (!snooze || snoozeTally == 0) binding.snoozeAlarmButton.setEnabled(false)

        // allow the user to stop the alarm
        binding.stopAlarmButton.setOnClickListener {
            if (ringtone.isPlaying) ringtone.stop()
            singleton.setAlarmExpired(true)
            sendBroadcast(intent)
            val returnToMain = Intent(applicationContext, MainActivity::class.java)
            finish()
            startActivity(returnToMain)
        }

        // allow the user to snooze the alarm
        binding.snoozeAlarmButton.setOnClickListener {
            if (ringtone.isPlaying) ringtone.stop()
            singleton.setAlarmSnoozed(true)
            sendBroadcast(intent)
            val returnToMain = Intent(applicationContext, MainActivity::class.java)
            finish()
            startActivity(returnToMain)
        }

        if (!ringtone.isPlaying) ringtone.play()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}