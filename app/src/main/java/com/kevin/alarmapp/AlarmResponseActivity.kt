// Created By Kevin F Garcia
package com.kevin.alarmapp

import android.R
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface.OnShowListener
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.kevin.alarmapp.databinding.ActivityAlarmResponseBinding

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
        val snooze = getIntent().getBooleanExtra("snooze", false)
        val maths = getIntent().getBooleanExtra("maths", false)
        val snoozeTally = getIntent().getIntExtra("snoozeTally", 999)

        val uri =
            if (getIntent().getStringExtra("alarmTone") != "")
                Uri.parse(getIntent().getStringExtra("alarmTone"))
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val ringtone: Ringtone = RingtoneManager.getRingtone(context, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone.setLooping(true)
        }

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

        var num_correct = 3
        val num_total = num_correct
        var num_id = 1
        var rand01 = 0
        var rand02 = 0
        var useCopies = false

        // allow the user to stop the alarm
        binding.stopAlarmButton.setOnClickListener {
            if(maths){
                if(!useCopies){
                    rand01 = (100..500).random()
                    rand02 = (100..500).random()
                }

                val dialogBuilder = AlertDialog.Builder(this)
                dialogBuilder.setMessage("$rand01 + $rand02 = ?")
                dialogBuilder.setCancelable(false)

                // collect the user's answer
                val input = EditText(this)
                input.inputType =
                    InputType.TYPE_CLASS_NUMBER
                input.requestFocus()
                dialogBuilder.setView(input)

                // positive button text and action
                dialogBuilder.setPositiveButton(android.R.string.yes) { dialog, which ->
                    // !! submitting an empty text will result in error !!
                    if(TextUtils.isEmpty(input.getText())){
                        useCopies = true
                    }
                    else if(input.getText().toString().toInt() == (rand01 + rand02)){
                        num_correct--
                        num_id++
                        useCopies = false
                    }
                    else useCopies = true

                    if(num_correct == 0) {
                        if (ringtone.isPlaying) ringtone.stop()
                        singleton.setAlarmExpired(true)
                        sendBroadcast(intent)
                        val returnToMain =
                            Intent(applicationContext, MainActivity::class.java)
                        finish()
                        startActivity(returnToMain)
                    }
                }

                val alert = dialogBuilder.create()
                alert.setTitle("$num_id of $num_total")

                alert.getWindow()?.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
                alert.getWindow()?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                alert.getWindow()?.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                alert.getWindow()?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

                alert.getWindow()
                    ?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                alert.getWindow()?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

                alert.setOnShowListener(OnShowListener {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                })
                alert.show()
                val messageView: TextView = alert.findViewById(R.id.message)
                messageView.setGravity(Gravity.CENTER)
                messageView.setTextSize(24F)
            }
            else{
                if (ringtone.isPlaying) ringtone.stop()
                singleton.setAlarmExpired(true)
                sendBroadcast(intent)
                val returnToMain =
                    Intent(applicationContext, MainActivity::class.java)
                finish()
                startActivity(returnToMain)
            }
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