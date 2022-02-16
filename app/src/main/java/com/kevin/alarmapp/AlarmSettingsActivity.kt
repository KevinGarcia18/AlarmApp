// Created By Kevin F Garcia
package com.kevin.alarmapp

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.kevin.alarmapp.databinding.ActivityAlarmSettingsBinding
import android.media.RingtoneManager
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
import androidx.annotation.RequiresApi

class AlarmSettingsActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private lateinit var binding: ActivityAlarmSettingsBinding
    private lateinit var context: Context

    private val singleton = MainActivity.AlarmData.getInstance()
    private var blockInterval = 20
    private var snoozeInterval = 5
    private var snoozeLimit = 999
    private var alarmToneUri = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmSettingsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        context = this

        val idDaysList = listOf(R.id.SunBox, R.id.MonBox,
            R.id.TueBox, R.id.WedBox, R.id.ThuBox, R.id.FriBox, R.id.SatBox)

        // disable the save button
        binding.saveButton.setEnabled(false)

        val alarmID = getIntent().getStringExtra("alarmID") as String
        val hour = (alarmID.takeLast(4)).take(2).toInt()
        val minute = alarmID.takeLast(2)

        // format the hours and am/pm for the textview
        val alarmTime =
            if (hour > 12) { "${hour - 12}:$minute PM" }
            else if (hour == 12) { "12:$minute PM" }
            else if (hour == 0) { "12:$minute AM" }
            else { "$hour:$minute AM" }

        // set the textview for the alarm in this activity
        binding.timeText.setText(alarmTime)

        var numberOfDays = 0
        for (i in 0..6) {
            val checkBox = findViewById<CheckBox>(idDaysList[i])
            checkBox.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
                override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                    if (isChecked) {
                        numberOfDays++
                        binding.saveButton.setEnabled(true)
                        if (numberOfDays == 7) { binding.everydayBox.setChecked(true) }
                    }
                    else {
                        numberOfDays--
                        binding.everydayBox.setChecked(false)
                        if (numberOfDays == 0) { binding.saveButton.setEnabled(false) }
                    }
                }
            })
        }

        binding.everydayBox.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                if (!isChecked) {
                    var tally = 0
                    for (i in 0..6) {
                        val checkBox = findViewById<CheckBox>(idDaysList[i])
                        if (checkBox.isChecked) { tally++ }
                    }
                    // if the 'everyday' checkbox was directly unchecked, uncheck them all
                    // else, leave them be
                    if (tally == 7) {
                        for (i in 0..6) {
                            val checkBox = findViewById<CheckBox>(idDaysList[i])
                            checkBox.setChecked(isChecked)
                        }
                    }
                }
                else {
                    // if the 'everyday' checkbox was directly checked, check them all
                    for (i in 0..6) {
                        val checkBox = findViewById<CheckBox>(idDaysList[i])
                        checkBox.setChecked(isChecked)
                    }
                }
            }
        })

        val blockIntervalSpinner = binding.blockSpinner
        ArrayAdapter.createFromResource(this, R.array.blockInterval, android.R.layout.simple_spinner_item).also {
                adapter1 ->
            adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            blockIntervalSpinner.adapter = adapter1
        }
        blockIntervalSpinner.setOnItemSelectedListener(this)
        blockIntervalSpinner.setSelection(29)

        val snoozeIntervalSpinner = binding.snoozeSpinner1
        ArrayAdapter.createFromResource(this, R.array.snoozeInterval, android.R.layout.simple_spinner_item).also {
                adapter2 ->
            adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            snoozeIntervalSpinner.adapter = adapter2
        }
        snoozeIntervalSpinner.setOnItemSelectedListener(this)
        snoozeIntervalSpinner.setSelection(4)

        val snoozeLimitSpinner = binding.snoozeSpinner2
        ArrayAdapter.createFromResource(this, R.array.snoozeLimit, android.R.layout.simple_spinner_item).also {
                adapter3 ->
            adapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            snoozeLimitSpinner.adapter = adapter3
        }
        snoozeLimitSpinner.setOnItemSelectedListener(this)
        snoozeLimitSpinner.setSelection(10)

        binding.toneButton.setOnClickListener(object : View.OnClickListener {
            @RequiresApi(Build.VERSION_CODES.M)
            override fun onClick(arg0: View?) {
                val intent3 = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                intent3.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"Select Alarm Tone")
                intent3.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                intent3.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                intent3.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, DEFAULT_ALARM_ALERT_URI)
                startActivityForResult(intent3, 333)
            }
        })

        // the save button is enabled
        binding.saveButton.setOnClickListener {
            val dayList = arrayListOf(false, false, false, false, false, false, false)
            for (i in 0..6) {
                val checkBox = findViewById<CheckBox>(idDaysList[i])
                if (checkBox.isChecked) { dayList[i] = true }
            }

            val alarm = MainActivity.Alarm()
            alarm.dayList = dayList
            alarm.repeats = binding.repeatBox.isChecked
            alarm.blocks = binding.blockSwitch.isChecked
            alarm.snooze = binding.snoozeSwitch.isChecked
            alarm.maths = binding.mathSwitch.isChecked
            alarm.blockInterval = blockInterval
            alarm.snoozeInterval = snoozeInterval
            alarm.snoozeLimit = snoozeLimit
            alarm.snoozeTally = snoozeLimit
            alarm.displayTime = alarmTime
            alarm.alarmTone = alarmToneUri
            singleton.getAlarmMap().put(alarmID, alarm)
            singleton.setAlarmCreated(true)

            finish()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        val str: String = parent.getItemAtPosition(pos) as String
        when (parent.getId()) {
            R.id.blockSpinner -> { blockInterval = str.replace(" min", "").toInt() }
            R.id.snoozeSpinner1 -> { snoozeInterval = str.replace(" min", "").toInt() }
            R.id.snoozeSpinner2 -> {
                snoozeLimit = if (str.replace(" snoozes", "") == "∞") { 999 }
                else { str.replace(" snoozes", "").toInt() }
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == 333) {
                alarmToneUri = data?.getParcelableExtra<Parcelable>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI).toString()
            }
        }
    }
}