// Created By Kevin F Garcia
// Log Example: Log.d("Foobar", "Hello World!" )
package com.kevin.alarmapp

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.startActivity
import com.kevin.alarmapp.databinding.ActivityMainBinding
import java.util.*
import android.provider.Settings
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.RingtoneManager.TYPE_NOTIFICATION
import android.os.*
import androidx.core.app.NotificationCompat.GROUP_ALERT_SUMMARY
import android.net.Uri
import com.google.gson.Gson
import kotlin.collections.ArrayList
import com.google.gson.reflect.TypeToken
import android.os.PowerManager
import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo

import android.content.Context.ALARM_SERVICE

import android.os.SystemClock

class MainActivity : AppCompatActivity(), TimePickerDialog.OnTimeSetListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var powerManager: PowerManager
    private lateinit var mainLayout: LinearLayout

    private var singleton = AlarmData.getInstance()

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        context = this
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        mainLayout = binding.myLayout

        // !! APPLICATION CONTEXT MIGHT NOT BE THE CORRECT CONTEXT TO USE IN ALL SITUATIONS !!
        singleton.setup(getApplicationContext(), alarmManager, mainLayout)

        val packageName = getPackageName()
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            // hide 'set alarm' button for now
            binding.setAlarm.setVisibility(View.GONE)
            binding.permissionTextView.setVisibility(View.VISIBLE)
            binding.permissionButton.setVisibility(View.VISIBLE)

            binding.permissionButton.setOnClickListener {
                // asks the user for a permission that makes alarms more accurate
                //miniIntent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                val miniIntent = Intent()
                miniIntent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                miniIntent.setData(Uri.parse("package:" + packageName))
                context.startActivity(miniIntent)

                /*val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.setData(Uri.parse("package:" + this.getPackageName()))
                startActivity(intent)*/

                binding.permissionTextView.setVisibility(View.GONE)
                binding.permissionButton.setVisibility(View.GONE)
                binding.setAlarm.setVisibility(View.VISIBLE)
            }
        }
        /*if (alarmManager.canScheduleExactAlarms()) {
            // hide 'set alarm' button for now
            binding.setAlarm.setVisibility(View.GONE)
            binding.permissionTextView.setVisibility(View.VISIBLE)
            binding.permissionButton.setVisibility(View.VISIBLE)

            binding.permissionButton.setOnClickListener {
                // asks the user for a permission that makes alarms more accurate
                //miniIntent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                val miniIntent = Intent()
                miniIntent.setAction(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                miniIntent.setData(Uri.parse("package:" + packageName))
                context.startActivity(miniIntent)

                binding.permissionTextView.setVisibility(View.GONE)
                binding.permissionButton.setVisibility(View.GONE)
                binding.setAlarm.setVisibility(View.VISIBLE)
            }
        }*/

        val prefs = this.getSharedPreferences("bilbo", Context.MODE_PRIVATE)
        val json = prefs.getString("map", "fail")

        // get alarm data from shared preferences
        if (!json.equals("fail")) {
            val token: TypeToken<HashMap<String, Alarm>> = object : TypeToken<HashMap<String, Alarm>>() {}
            val alarms2: HashMap<String, Alarm> = Gson().fromJson(json, token.type)
            singleton.setAlarmMap(alarms2)
            val sortedMap = singleton.getAlarmMap().toSortedMap()
            for ((key,value) in sortedMap) {
                if (getIntent().getStringExtra("source") == "boot") { singleton.setNextAlarm(key) }
                singleton.createAlarmViews(key)
            }
        }

        val hour = 0
        val minute = 0

        // display the time picker when the set alarm button is clicked
        binding.setAlarm.setOnClickListener {
            if (!singleton.isThereABlock()) {
                TimePickerDialog(this, this, hour, minute, false).show()
            } else {
                Toast.makeText(context, "Alarm Changes Blocked", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // code executes when returning to main activity from another activity
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onResume() {
        super.onResume()
        singleton.resume()
    }

    override fun onPause() {
        super.onPause()
        val alarms = AlarmData.getInstance().getAlarmMap()
        val json: String = Gson().toJson(alarms)
        val prefs = this.getSharedPreferences("bilbo", Context.MODE_PRIVATE)
        prefs.edit().putString("map", json).apply()
    }

    // collect the time from the user
    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        // format the createdID using the user's set time
        val hourString = if (hourOfDay < 10) { "0$hourOfDay" } else { hourOfDay }
        val minuteString = if (minute < 10) { "0$minute" } else { minute }

        // 12:00 am = 10000, 12:00 pm = 11200, 11:59 pm = 12359
        val alarmID = "1$hourString$minuteString"

        // if the alarm does not already exist
        if(!singleton.getAlarmMap().containsKey(alarmID)) {
            singleton.setCreatedID(alarmID)

            // start the activity that records other alarm settings
            val intent = Intent(context, AlarmSettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("alarmID", alarmID)
            startActivity(context, intent, null)
        }
        else {
            Toast.makeText(this, "Alarm Already Exists", Toast.LENGTH_LONG).show()
        }
    }

    class Receiver : BroadcastReceiver() {
        private val singleton = AlarmData.getInstance()
        private val channel_id = "channel_id"
        private var request_code = 1
        private lateinit var notificationManager: NotificationManager
        private lateinit var notificationChannel: NotificationChannel

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceive(context: Context, intent: Intent) {

            if (!(this::notificationManager.isInitialized)) {
                createNotificationChannel(context)
            }

            // if the phone was rebooted, re-schedule the alarms
            if (intent.action == "android.intent.action.BOOT_COMPLETED") {
                Log.d("Alarms", "Boot Completed!")
                val newIntent = Intent(context, MainActivity::class.java)
                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                newIntent.putExtra("source", "boot")
                context.startActivity(newIntent)
            }
            else {
                val alarmID = intent.getStringExtra("alarmID")
                if (alarmID != null) {
                    singleton.setExpiredID(alarmID)
                    // if the intent comes from AlarmResponseActivity, cancel the notification
                    if (intent.action == "com.kevin.alarmapp.removenotif") {
                        notificationManager.cancel(alarmID.toInt())
                    }
                    /*else if (alarmID.take(1) == "2") {
                        Log.d("Alarms", "Nothing To Do: Silent Alarm")
                        val returnToMain = Intent(context, MainActivity::class.java)
                        returnToMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(returnToMain)
                    }*/
                    else {
                        // else start the notification for the alarm
                        val alarmTone = intent.getStringExtra("alarmTone")
                        val snooze = intent.getBooleanExtra("snooze", true)
                        val snoozeTally = intent.getIntExtra("snoozeTally", 999)
                        sendNotification(context, alarmID, alarmTone, snooze, snoozeTally)
                    }
                }
            }
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                val name = "Your Alarm is Ringing"
                val descriptionText = "Click on This Notification to Respond"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val attributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                notificationChannel = NotificationChannel(channel_id, name, importance).apply {
                    description = descriptionText
                    setSound(Settings.System.DEFAULT_NOTIFICATION_URI, attributes)
                    enableVibration(true)
                    enableLights(true)
                    setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)
                }
                notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }

        private fun sendNotification(context: Context, alarmID: String, alarmTone: String?, snooze: Boolean, snoozeTally: Int) {
            val fullScreenIntent = Intent(context, AlarmResponseActivity::class.java)
                fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                fullScreenIntent.putExtra("alarmID", alarmID)
                fullScreenIntent.putExtra("alarmTone", alarmTone)
                fullScreenIntent.putExtra("snooze", snooze)
                fullScreenIntent.putExtra("snoozeTally", snoozeTally)

            val fullScreenPendingIntent = PendingIntent.getActivity(context, request_code,
                fullScreenIntent, PendingIntent.FLAG_ONE_SHOT)

            request_code++

            val notificationBuilder = context.let {
                NotificationCompat.Builder(context, channel_id)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Your Alarm is Ringing")
                    .setContentText("Click on This Notification to Respond")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(fullScreenPendingIntent)
                    .setAutoCancel(true)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
            }

            // silence the notification if the full screen intent will be played
            val pm = context.getSystemService(POWER_SERVICE) as PowerManager
            val isScreenOn: Boolean = pm.isInteractive()
            if (!isScreenOn) {
                notificationBuilder.setGroupAlertBehavior(GROUP_ALERT_SUMMARY)
                notificationBuilder.setGroup("My group")
                notificationBuilder.setGroupSummary(false)
                notificationBuilder.setSound(RingtoneManager.getDefaultUri(TYPE_NOTIFICATION))
            }

            with(NotificationManagerCompat.from(context)) {
                notify(alarmID.toInt(), notificationBuilder.build())
            }
        }
    }

    class Alarm {
        var repeats: Boolean = false
        var blocks: Boolean = false
        var snooze: Boolean = false
        var blockInterval: Int = 20
        var snoozeInterval: Int = 5
        var snoozeTally: Int = 999
        var snoozeLimit: Int = 999
        var nextAlarmDay: Int = 0
        var dayList: ArrayList<Boolean> = arrayListOf(false, false, false, false, false, false, false)
        var displayTime: String = ""
        var alarmTone: String = ""
    }

    class AlarmViews {
        lateinit var timeTextView: TextView
        lateinit var dataTextView: TextView
        lateinit var activeSwitch: Switch
        lateinit var cancelButton: Button
    }

    // singleton class for central storage
    class AlarmData {
        companion object {
            private val HOLDER: AlarmData = AlarmData()
            fun getInstance(): AlarmData {
                return HOLDER
            }
        }
        private lateinit var context: Context
        private lateinit var alarmManager: AlarmManager
        private lateinit var mainLayout: LinearLayout

        private var alarmViews: MutableMap<String, AlarmViews> = mutableMapOf()
        private var alarmMap: MutableMap<String, Alarm> = mutableMapOf()
        private var createdID: String = ""
        private var expiredID: String = ""
        private var alarmCreated: Boolean = false
        private var alarmExpired: Boolean = false
        private var alarmSnoozed: Boolean = false

        fun setAlarmMap(data: MutableMap<String, Alarm>) { alarmMap = data }
        fun getAlarmMap(): MutableMap<String, Alarm> { return alarmMap }
        fun setCreatedID(data: String) { createdID = data }
        fun setExpiredID(data: String) { expiredID = data }
        fun setAlarmCreated(data: Boolean) { alarmCreated = data }
        fun setAlarmExpired(data: Boolean) { alarmExpired = data }
        fun setAlarmSnoozed(data: Boolean) { alarmSnoozed = data }

        fun setup(c: Context, a: AlarmManager, m: LinearLayout) {
            context = c
            alarmManager = a
            mainLayout = m
        }

        @RequiresApi(Build.VERSION_CODES.M)
        fun resume() {
            if (alarmCreated) {
                setNextAlarm(createdID)
                // update the alarm views
                val sortedMap = alarmMap.toSortedMap()
                for ((key,value) in sortedMap) {
                    destroyAlarmViews(key)
                    createAlarmViews(key)
                }
                alarmCreated = false
            }
            else if (alarmExpired) {
                // reset the alarm's snooze tally
                alarmMap.getValue(expiredID).snoozeTally = alarmMap.getValue(expiredID).snoozeLimit

                // if the alarm does not repeat, change the day in the dayList to false
                if (!alarmMap.getValue(expiredID).repeats) {
                    alarmMap.getValue(expiredID).dayList[alarmMap.getValue(expiredID).nextAlarmDay] = false
                    // update the alarm views
                    val sortedMap = alarmMap.toSortedMap()
                    for ((key,value) in sortedMap) {
                        destroyAlarmViews(key)
                        createAlarmViews(key)
                    }
                }

                // if the alarm repeats or has another day to ring
                if (alarmMap.getValue(expiredID).repeats || alarmMap.getValue(expiredID).dayList.count { it } > 0) {
                    setNextAlarm(expiredID)
                }
                else {
                    disableAlarm(expiredID)
                    destroyAlarmViews(expiredID)
                    deleteAlarm(expiredID)
                }
                alarmExpired = false
            }
            else if (alarmSnoozed) {
                if (alarmMap.getValue(expiredID).snoozeLimit != 999) {
                    alarmMap.getValue(expiredID).snoozeTally--
                }

                // set an alarm for x minutes from now
                val intent = Intent(context, Receiver::class.java).putExtra("alarmID", expiredID)
                intent.putExtra("snoozeTally", alarmMap.getValue(expiredID).snoozeTally)
                intent.putExtra("alarmTone", alarmMap.getValue(expiredID).alarmTone)

                val pendingIntent = PendingIntent.getBroadcast(context, 33333, intent, PendingIntent.FLAG_ONE_SHOT)

                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + (60000 * alarmMap.getValue(expiredID).snoozeInterval), pendingIntent)
                /*val alarmTime = System.currentTimeMillis() + (60000 * alarmMap.getValue(expiredID).snoozeInterval)
                val acInfo = AlarmClockInfo(alarmTime, null)
                alarmManager.setAlarmClock(acInfo, pendingIntent)*/
                alarmSnoozed = false
            }
        }

        fun convertIDToMillis(alarmID: String): Long {
            val time = alarmID.takeLast(4)
            var hour = time.take(2)
            var minute = time.takeLast(2)
            if (hour.take(1) == "0") { hour = hour.takeLast(1) }
            if (minute.take(1) == "0") { minute = minute.takeLast(1) }

            // creates a calendar instance to encapsulate the set time
            val calendar: Calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour.toInt())
                set(Calendar.MINUTE, minute.toInt())
            }

            return calendar.timeInMillis
        }

        @RequiresApi(Build.VERSION_CODES.M)
        fun setNextAlarm(alarmID: String) {
            val intent = Intent(context, Receiver::class.java).putExtra("alarmID", alarmID)
            intent.putExtra("alarmTone", alarmMap.getValue(alarmID).alarmTone)
            intent.putExtra("snooze", alarmMap.getValue(alarmID).snooze)
            intent.putExtra("snoozeTally", alarmMap.getValue(alarmID).snoozeTally)
            val pendingIntent = PendingIntent.getBroadcast(context, alarmID.toInt(), intent, PendingIntent.FLAG_ONE_SHOT)

            // Sun = 1, Mon = 2, ... Sat = 7
            val todaysDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val millis: Long = convertIDToMillis(alarmID)

            // if there is an alarm for today and time hasn't passed, set the alarm
            if (alarmMap.getValue(alarmID).dayList[todaysDay - 1] && (System.currentTimeMillis() < millis)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
                /*val acInfo = AlarmClockInfo(millis, null)
                alarmManager.setAlarmClock(acInfo, pendingIntent)*/
                //wakeUp(alarmID, millis)
                alarmMap.getValue(alarmID).nextAlarmDay = todaysDay - 1
                Toast.makeText(context, "Alarm Set For Today", Toast.LENGTH_SHORT).show()
            }
            else { // set the absolute next alarm
                var count = 1
                // var day will be tomorrow because the list indexing is shifted back by one
                var day = if (todaysDay == 7) 0 else todaysDay

                do {
                    if (alarmMap.getValue(alarmID).dayList[day]) {
                        val timeDifference = count * 86400000

                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                            millis + timeDifference, pendingIntent)
                        /*val acInfo = AlarmClockInfo(millis + timeDifference, null)
                        alarmManager.setAlarmClock(acInfo, pendingIntent)*/
                        //wakeUp(alarmID, millis + timeDifference)
                        alarmMap.getValue(alarmID).nextAlarmDay = day
                        if (count == 1) Toast.makeText(context, "Alarm Set For Tomorrow", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(context, "Alarm Set For " + count + " Days From Today", Toast.LENGTH_SHORT).show()
                        break
                    }
                    else {
                        if (day == 6) { day = 0 }
                        else { day++ }
                        count++
                    }
                } while (day != if (todaysDay == 7) 0 else todaysDay)
            }
        }

        fun createAlarmViews(alarmID: String) {
            val myAlarmViews = AlarmViews()

            // creates the text view for the new alarm
            val timeRef = TextView(context)
            timeRef.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            timeRef.setText(alarmMap.getValue(alarmID).displayTime)
            timeRef.setId(alarmID.toInt())
            mainLayout.addView(timeRef)
            myAlarmViews.timeTextView = timeRef

            val dataRef = TextView(context)
            dataRef.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val dayList = listOf(" Sun |", " Mon |", " Tue |", " Wed |", " Thu |", " Fri |", " Sat |")
            var num = 0
            var dataText = if(alarmMap.getValue(alarmID).blocks) "[ Blocks ][ " +
                    alarmMap.getValue(alarmID).blockInterval + " Min ]\n" else ""
            dataText += if(alarmMap.getValue(alarmID).repeats) "[ Repeats ]" else ""
            if (alarmMap.getValue(alarmID).dayList.count { it } == 7) {
                dataText += "[ Everyday ]"
            }
            else {
                dataText += "["
                for (i in alarmMap.getValue(alarmID).dayList) {
                    if (i) {
                        dataText += dayList[num]
                    }
                    num++
                }
                dataText = dataText.dropLast(1)
                dataText += "]"
            }
            val limit = if (alarmMap.getValue(alarmID).snoozeLimit == 999) "∞" else alarmMap.getValue(alarmID).snoozeLimit
            dataText += if(alarmMap.getValue(alarmID).snooze)
                "\n[ Snoozes ][ Limit " + limit +
                " ][ " + alarmMap.getValue(alarmID).snoozeInterval + " Min ]" else ""
            dataRef.setText(dataText)
            dataRef.setId(alarmID.toInt())
            mainLayout.addView(dataRef)
            myAlarmViews.dataTextView = dataRef

            // creates the switch for the new alarm
            val switchRef = Switch(context)
            switchRef.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            switchRef.setChecked(true)
            switchRef.setId(alarmID.toInt())
            mainLayout.addView(switchRef)
            myAlarmViews.activeSwitch = switchRef

            // creates the delete button for the new alarm
            val buttonRef = Button(context)
            buttonRef.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            buttonRef.text = "Delete Alarm"
            buttonRef.setId(alarmID.toInt())
            mainLayout.addView(buttonRef)
            myAlarmViews.cancelButton = buttonRef

            // the delete button is enabled
            buttonRef.setOnClickListener(object : View.OnClickListener {
                override fun onClick(view: View?) {
                    if (view != null && !isThereABlock(alarmID)) {
                        disableAlarm(view.getId().toString())
                        destroyAlarmViews(view.getId().toString())
                        deleteAlarm(view.getId().toString())
                    }
                    else {
                        Toast.makeText(context, "Alarm Changes Blocked", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            // the enable switch is enabled
            switchRef.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
                @RequiresApi(Build.VERSION_CODES.M)
                override fun onCheckedChanged(view: CompoundButton, isChecked: Boolean) {
                    if (!isThereABlock(alarmID)) {
                        if (isChecked) {
                            Toast.makeText(context, "Alarm Enabled", Toast.LENGTH_SHORT).show()
                            setNextAlarm(view.getId().toString())
                        }
                        else {
                            Toast.makeText(context, "Alarm Disabled", Toast.LENGTH_SHORT).show()
                            disableAlarm(view.getId().toString())
                        }
                    }
                    else {
                        switchRef.setChecked(!isChecked)
                        Toast.makeText(context, "Alarm Changes Blocked", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            alarmViews.put(alarmID, myAlarmViews)
        }

        // deletes alarm's pending intent
        fun disableAlarm(alarmID: String) {
            if (alarmMap.containsKey(alarmID)) { // if the alarm exists
                val intent = Intent(context, Receiver::class.java).putExtra("alarmID", alarmID)
                val pendingIntent = PendingIntent.getBroadcast(context, alarmID.toInt(), intent, PendingIntent.FLAG_ONE_SHOT)
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()

                val silentAlarmID = "2" + alarmID.takeLast(4)
                val silentIntent = Intent(context, Receiver::class.java).putExtra("alarmID", silentAlarmID)
                val silentPendingIntent = PendingIntent.getBroadcast(context, silentAlarmID.toInt(), silentIntent, PendingIntent.FLAG_ONE_SHOT)
                alarmManager.cancel(silentPendingIntent)
                silentPendingIntent.cancel()
            }
        }

        // removes alarm's views
        fun destroyAlarmViews (alarmID: String) {
            if (alarmViews.containsKey(alarmID)) {
                mainLayout.removeView(alarmViews.getValue(alarmID).timeTextView)
                mainLayout.removeView(alarmViews.getValue(alarmID).dataTextView)
                mainLayout.removeView(alarmViews.getValue(alarmID).activeSwitch)
                mainLayout.removeView(alarmViews.getValue(alarmID).cancelButton)
                alarmViews.remove(alarmID)
            }
        }

        // deletes all references to alarm
        fun deleteAlarm (alarmID: String) {
            alarmMap.remove(alarmID)
            Toast.makeText(context, "Alarm Deleted", Toast.LENGTH_SHORT).show()
        }

        // returns true if any alarm is blocking right now
        fun isThereABlock(): Boolean {
            for ((alarmID, alarm) in alarmMap) {
                val millis: Long = convertIDToMillis(alarmID)
                val todaysDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

                if (alarm.blocks && alarm.dayList[todaysDay - 1]
                    && System.currentTimeMillis() < (millis + (60000 * alarm.blockInterval))
                    && System.currentTimeMillis() > (millis - (60000 * alarm.blockInterval))) {
                    return true
                }
            }
            return false
        }

        // returns true if the specified alarm is blocking right now
        fun isThereABlock(alarmID: String): Boolean {
            val millis: Long = convertIDToMillis(alarmID)
            val todaysDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val alarm = alarmMap.getValue(alarmID)

            return (alarm.blocks && alarm.dayList[todaysDay - 1]
                    && System.currentTimeMillis() < (millis + (60000 * alarm.blockInterval))
                    && System.currentTimeMillis() > (millis - (60000 * alarm.blockInterval)))
        }

        /*// creates a silent pendingIntent that wakes up the phone from doze or whatever
        @RequiresApi(Build.VERSION_CODES.M)
        fun wakeUp(alarmID: String, millis: Long) {
            val silentAlarmID: String = "2" + alarmID.takeLast(4)
            val intent = Intent(context, Receiver::class.java).putExtra("alarmID", silentAlarmID)
            val pendingIntent = PendingIntent.getBroadcast(context, silentAlarmID.toInt(), intent, PendingIntent.FLAG_ONE_SHOT)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis - 570000, pendingIntent)
            //Toast.makeText(context, "Silent Alarm Set", Toast.LENGTH_SHORT).show()
        }*/
    }
}