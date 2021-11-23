package com.kevin.alarmapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import java.util.*

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // will trigger at Alarm time
        Log.d("MainActivity", "Receiver : " + Date().toString())
        Toast.makeText(context, "RING RING", Toast.LENGTH_LONG).show()
    }
}