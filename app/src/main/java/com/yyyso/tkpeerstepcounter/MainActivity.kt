package com.yyyso.tkpeerstepcounter

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.Spanned
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import kotlin.properties.Delegates




class MainActivity : AppCompatActivity() {
    public val CHANNEL_ID = "tkpeerstepcounter001"

    // SharedProfileのキー
    public val PROF_IP1: String = "ip1" // IP Addresss1
    public val PROF_IP2: String = "ip2" // IP Addresss2
    public val PROF_IP3: String = "ip3" // IP Addresss3
    public val PROF_IP4: String = "ip4" // IP Addresss4
    public val PROF_PORT: String = "port" // Port Number

    //
    var mBtnStart: Button by Delegates.notNull<Button>()

    // IP AddressのEditText
    var mEdtIpAddr1: EditText by Delegates.notNull<EditText>()
    var mEdtIpAddr2: EditText by Delegates.notNull<EditText>()
    var mEdtIpAddr3: EditText by Delegates.notNull<EditText>()
    var mEdtIpAddr4: EditText by Delegates.notNull<EditText>()

    // Port NumberのEditText
    var mEdtPortNum: EditText by Delegates.notNull<EditText>()

    // Layout
    var mLayoutNotStart : LinearLayout by Delegates.notNull<LinearLayout>()
    var mLayoutStarted : LinearLayout by Delegates.notNull<LinearLayout>()

    var mTxtInfo : TextView by Delegates.notNull<TextView>()
    var mTxtView : TextView by Delegates.notNull<TextView>()

    // サービスの実装
    val sendorService: SensorService = SensorService(this)

    //通知チャンネルの作成
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "name"
            val descriptionText = "description"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //通知チャンネルを作成します。
        createNotificationChannel()

        // サービスの変更通知のリスナーを登録
        sendorService.setListener(sensorListener)
        sendorService.start()

        // 画面の各コントロールを初期化
        setupViewCtrl()
    }

    override fun onDestroy() {
        Log.d("MainActivity", "onDestroy")
        super.onDestroy()
        //sendorService.stop()
    }

    // 開始可能な状態の画面表示
    fun updateViewStartAble()
    {
        mBtnStart.isEnabled = true
        mBtnStart.isClickable = true
        mBtnStart.text = this.getString(R.string.start)
        mLayoutNotStart.visibility = View.VISIBLE;
        mLayoutStarted.visibility = View.GONE;
    }

    // 停止可能な状態の画面表示
    fun updateViewStopAble()
    {
        mBtnStart.isEnabled = true
        mBtnStart.isClickable = true
        mBtnStart.text = this.getString(R.string.stop)
        mLayoutNotStart.visibility = View.GONE;
        mLayoutStarted.visibility = View.VISIBLE;
    }

    // センサー一覧を表示するアクティビティーを表示
    fun onClickedStart(view: View) {
        // 入力値をチェックします

        if (sendorService.isRunningStepCounter()) {
            //実行中の場合停止する
            sendorService.stop()
            deletePendingIntent()
            // 開始可能な状態の画面表示
            updateViewStartAble()
            return
        }

        // "DataStore"という名前でsharedPreferenceのインスタンスを生成
        val sharedPref = getSharedPreferences(
            getString(R.string.preference_file_key), Context.MODE_PRIVATE
        )
        var str: String?
        var ip1: Int = 0
        var ip2: Int = 0
        var ip3: Int = 0
        var ip4: Int = 0
        var port: Int = 0

        // 入力値を取り出し、チェックします。
        try {
            str = mEdtIpAddr1.text.toString()
            if (str.length == 0) {
                Toast.makeText(this, "invalid ip address.", Toast.LENGTH_LONG).show()
                return
            }
            ip1 = str.toInt()

            str = mEdtIpAddr2.text.toString()
            if (str.length == 0) {
                Toast.makeText(this, "invalid ip address.", Toast.LENGTH_LONG).show()
                return
            }
            ip2 = str.toInt()

            str = mEdtIpAddr3.text.toString()
            if (str.length == 0) {
                Toast.makeText(this, "invalid ip address.", Toast.LENGTH_LONG).show()
                return
            }
            ip3 = str.toInt()

            str = mEdtIpAddr4.text.toString()
            if (str.length == 0) {
                Toast.makeText(this, "invalid ip address.", Toast.LENGTH_LONG).show()
                return
            }
            ip4 = str.toInt()

        } catch (e: Exception) {
            Toast.makeText(this, "invalid ip address.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            str = mEdtPortNum.text.toString()
            if (str.length == 0) {
                Toast.makeText(this, "invalid port number.", Toast.LENGTH_LONG).show()
                return
            }
            port = str.toInt()
        } catch (e: Exception) {
            Toast.makeText(this, "invalid port number.", Toast.LENGTH_LONG).show()
            return
        }

        // 入力値チェックがOKなら、Preferenceに保存
        var editor = sharedPref.edit()
        editor.putInt(PROF_IP1, ip1)
        editor.putInt(PROF_IP2, ip2)
        editor.putInt(PROF_IP3, ip3)
        editor.putInt(PROF_IP4, ip4)
        editor.putInt(PROF_PORT, port)
        editor.apply()


        val sb = StringBuilder()
        sb.append(ip1)
        sb.append(".")
        sb.append(ip2)
        sb.append(".")
        sb.append(ip3)
        sb.append(".")
        sb.append(ip4)

        mBtnStart.isEnabled = false
        mBtnStart.isClickable = false
        sendorService.startStepCounter(sb.toString(), port)

    }


    // リスナーを実装
    private val sensorListener = object : SensorServiceInterface {
        //
        override fun onSensorChanged(sensorType: Int, values: FloatArray) {

            mTxtInfo.text = values[0].toString();
        }

        //
        override fun onAccuracyChanged(sensorType: Int, accuracy: Int) {
        }

        //
        override fun onStatusChanged(context: Context, type: Int, data: Any) {
            if (type == 1) {
                updateViewStopAble()


                // 通知バーに通知を表示する
                var mainAc: MainActivity = context as MainActivity
                mainAc.showPendingIntent()

            } else
                if (type == 2) {
                    // サービスに接続出来たら、実行中かどうかを確認して、ボタンテキストを変更する。
                    if (sendorService.isRunningStepCounter()) {
                        updateViewStopAble()
                        // 通知バーに通知を表示する
                        var mainAc: MainActivity = context as MainActivity
                        mainAc.showPendingIntent()
                    } else {
                        updateViewStartAble()
                    }
                } else
                    if (type == 101 || type == 102) {
                        AlertDialog.Builder(context)
                            .setTitle("ERROR")
                            .setMessage(data.toString())
                            .setPositiveButton("OK") { dialog, which -> }
                            .show()


                        mBtnStart.isEnabled = true
                        mBtnStart.isClickable = true
                    }

        }
    }

    // 通知バーに通知を表示する
    public fun showPendingIntent() {
        // 通知タップ時の遷移先を設定
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        //通知オブジェクトの作成
        var builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Step Counter")
            .setContentText("Running...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)


        //通知の実施
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var notification: Notification = builder.build()
        notification.flags =
            Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT   // 継続的イベント領域に表示 ※「実行中」領域
        notificationManager.notify(0, notification)
    }

    // 通知バーに通知を消去
    public fun deletePendingIntent() {
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(0)
    }
    // 画面の各コントロールを初期化
    private fun setupViewCtrl()
    {
        // 画面の各コントロールを取得
        mBtnStart = findViewById(R.id.button)
        mEdtIpAddr1 = findViewById(R.id.editTextIPAddr1)
        mEdtIpAddr2 = findViewById(R.id.editTextIPAddr2)
        mEdtIpAddr3 = findViewById(R.id.editTextIPAddr3)
        mEdtIpAddr4 = findViewById(R.id.editTextIPAddr4)
        mEdtPortNum = findViewById(R.id.editTextPortNumber)

        mLayoutNotStart = findViewById(R.id.layoutNonStart)
        mLayoutStarted = findViewById(R.id.layoutStarted)

        mTxtInfo = findViewById(R.id.text_info)
        mTxtView = findViewById(R.id.text_view)



        // 数値の入力制限を設定します。
        class InputFilterMinMax : InputFilter {

            var mMin : Int = 0
            var mMax : Int = 255
            constructor(_min: Int, _max: Int) {
                mMin = _min
                mMax = _max
            }
            override fun filter(
                source: CharSequence?,
                start: Int,
                end: Int,
                dest: Spanned?,
                p4: Int,
                p5: Int
            ): CharSequence {
                try {
                    val input: Int = (dest.toString() + source.toString()).toInt()
                    if (isInRange(mMin, mMax, input))
                        return source.toString()
                    else
                        return ""
                }catch (e: Exception){}
                return ""
            }
            private fun isInRange(a: Int, b: Int, c: Int): Boolean {
                return if (b > a) c >= a && c <= b else c >= b && c <= a
            }
        }
        mEdtIpAddr1.filters = arrayOf(InputFilterMinMax(0, 255))
        mEdtIpAddr2.filters = arrayOf(InputFilterMinMax(0, 255))
        mEdtIpAddr3.filters = arrayOf(InputFilterMinMax(0, 255))
        mEdtIpAddr4.filters = arrayOf(InputFilterMinMax(0, 255))
        mEdtPortNum.filters = arrayOf(InputFilterMinMax(0, 65535))

        // "DataStore"という名前でsharedPreferenceのインスタンスを生成
        val sharedPref = getSharedPreferences(
            getString(R.string.preference_file_key), Context.MODE_PRIVATE)
        // Preferenceの値から、画面を初期化します。
        mEdtIpAddr1.setText("" + sharedPref.getInt(PROF_IP1, 192))
        mEdtIpAddr2.setText("" + sharedPref.getInt(PROF_IP2, 168))
        mEdtIpAddr3.setText("" + sharedPref.getInt(PROF_IP3, 1))
        mEdtIpAddr4.setText("" + sharedPref.getInt(PROF_IP4, 1))
        mEdtPortNum.setText("" + sharedPref.getInt(PROF_PORT, 5001))



    }

    // 入力値をチェック
    private fun checkInputValue() : Boolean
    {

        // "DataStore"という名前でsharedPreferenceのインスタンスを生成
        val sharedPref = getSharedPreferences(
            getString(R.string.preference_file_key), Context.MODE_PRIVATE)
        var str : String?
        var ip1 : Int = 0
        var ip2 : Int = 0
        var ip3 : Int = 0
        var ip4 : Int = 0
        var port : Int = 0

        // 入力値を取り出し、チェックします。
        try {
            str = mEdtIpAddr1.text.toString()
            if(str.length == 0)
            {
                Toast.makeText(this, "invalid ip address.", Toast.LENGTH_LONG).show()
                return false
            }
            ip1 = str.toInt()

            str = mEdtIpAddr2.text.toString()
            if(str.length == 0)
            {
                Toast.makeText(this, "invalid ip address.", Toast.LENGTH_LONG).show()
                return false
            }
            ip2 = str.toInt()

            str = mEdtIpAddr3.text.toString()
            if(str.length == 0)
            {
                Toast.makeText(this, "invalid ip address.", Toast.LENGTH_LONG).show()
                return false
            }
            ip3 = str.toInt()

            str = mEdtIpAddr4.text.toString()
            if(str.length == 0)
            {
                Toast.makeText(this, "invalid ip address.", Toast.LENGTH_LONG).show()
                return false
            }
            ip4 = str.toInt()

        }catch (e: Exception){
            Toast.makeText(this, "invalid ip address.", Toast.LENGTH_LONG).show()
            return false
        }
        try {
            str = mEdtPortNum.text.toString()
            if(str.length == 0)
            {
                Toast.makeText(this, "invalid port number.", Toast.LENGTH_LONG).show()
                return false
            }
            port = str.toInt()
        }catch (e: Exception){
            Toast.makeText(this, "invalid port number.", Toast.LENGTH_LONG).show()
            return false
        }

        // 入力値チェックがOKなら、Preferenceに保存
        var editor = sharedPref.edit()
        editor.putInt(PROF_IP1, ip1)
        editor.putInt(PROF_IP2, ip2)
        editor.putInt(PROF_IP3, ip3)
        editor.putInt(PROF_IP4, ip4)
        editor.putInt(PROF_PORT, port)
        editor.apply()
        return true
    }


}