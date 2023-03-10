package com.yyyso.tkpeerstepcounter


import android.app.Service
import android.content.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.provider.Settings
import android.util.Log
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

// https://qiita.com/AMiDa_38/items/c578bf75824a2a57d9eb
// https://qiita.com/vram/items/77c4cbaa42c20eaad548

//
//  サービスリスナーIFの定義
//
interface SensorServiceInterface : SensorService.Listener {         // 独自のイベントリスナー
    fun onSensorChanged(sensorType : Int,values : FloatArray)       // センサー値取得イベント
    fun onAccuracyChanged(sensorType : Int,accuracy : Int)          // センサーレンジ変更イベント
    // 状態変化の通知
    //  type
    //      1: Start Complete
    //      2: Connected Service
    //      101: サーバーに接続失敗
    //      102: センサーが使用できない。
    fun onStatusChanged(context: Context, type : Int,data : Any )          // センサーレンジ変更イベント
}


///
/// サービス管理クラス
///
class SensorService(private val context: Context) : Handler(){      // 様々なセンサーの情報を管理するクラス

    private lateinit var mService: SensorServiceListener            // センサー取得サービスの参照用
    private var listener: SensorServiceInterface? = null            // 外部へのリスナー仲介
    interface Listener {}

    //var sensors: List<Sensor> = listOf()                            // センサー情報

    // 開始
    fun start(){
        val intent = Intent(context,SensorServiceListener::class.java)
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        context.startService(intent)
    }
    // 停止
    fun stop(){
        val intent = Intent(context,SensorServiceListener::class.java)
        context.unbindService(mConnection)
        context.stopService(intent)
    }

    override fun handleMessage(msg: Message) {                  // スレッド間通信用メッセージクラス

        if (msg.arg1 == 1){                                     // センサーの値取得時
            val values = msg.obj as FloatArray      //センサーの値を参照
            listener?.onSensorChanged(msg.arg2,values)          // センサー値取得イベント
        }
        if (msg.arg1 == 2){                                                     // センサーのレンジ変更時
            listener?.onAccuracyChanged(msg.arg2,msg.obj.toString().toInt())    // レンジ変更イベント
        }
        if (msg.arg1 == 3){                                                     // センサーのレンジ変更時
            listener?.onStatusChanged(context, msg.arg2, msg.obj)    // レンジ変更イベント
        }
    }

    fun setListener(listener: Listener?) {         // イベント受け取り先を設定
        if (listener is SensorServiceInterface) {
            this.listener = listener
        }
    }

    private  fun setHandler(){
        mService.setHandler(this)
    }

    // サービスのコネクション実装
    private val mConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d("ServiceConnection", "onServiceConnected")
            val localBinder = binder as SensorServiceListener.LocalBinder
            mService = localBinder.getService()
            //sensors =  mService.sensors
            setHandler()


            val msg = Message.obtain()
            msg.arg1 = 3
            msg.arg2 = 2
            msg.obj = "Start."
            if (listener  != null) listener?.onStatusChanged(context, 2, "")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("ServiceConnection", "onServiceDisconnected")
        }
    }
    public fun startStepCounter(ipaddr : String, port : Int){
        mService.startStepCounter(ipaddr,port)
    }
    public fun stopStepCounter(){
        mService.stopStepCounter()
    }
    // 実行中
    public fun isRunningStepCounter() : Boolean{
        return mService.isRunningStepCounter()
    }

}



//
//  サービス実装の本体
//
class SensorServiceListener : Service(), SensorEventListener {

    private var handler : Handler? = null
    private var mSoket : Socket? = null
    private val binder = LocalBinder()
    private var mIsRunningStepCounter : Boolean = false
    private var mWakeLock: PowerManager.WakeLock? = null
    private var mWifiNeverPolocy : Int = 0

    // 実行中
    public fun isRunningStepCounter() : Boolean{
        return mIsRunningStepCounter
    }

    inner class LocalBinder : Binder() {
        fun getService(): SensorServiceListener = this@SensorServiceListener
    }
    fun setHandler(handler  : Handler){
        this.handler = handler
    }
    override fun onCreate() {
        super.onCreate()
    }
    override fun onDestroy() {
        Log.d("SensorServiceListener", "onDestroy")

        super.onDestroy()

        stopStepCounter()
    }

    ///
    override fun onBind(intent: Intent): IBinder? {
        return binder
    }
    //
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    //
    override fun onSensorChanged(event: SensorEvent?) {

        if (event == null) return

        if (event!!.sensor.type === Sensor.TYPE_STEP_COUNTER )
        {
            if( mIsRunningStepCounter == false)
            {
                return
            }
            try {
                for( ff in event!!.values )
                {
                    if( ff != 0f ) {

                        val bytes: ByteArray = ByteBuffer.allocate(4).putInt(ff.toInt()).array()

                        Log.d("TAG", "f:" + ff)
                        Thread {
                            val writer: OutputStream? = mSoket!!.getOutputStream()
                            if (writer != null) {
                                writer.write(bytes)
                            } else {
                            }
                        }.start()
                    }
                }

                // 画面に通知
                val msg = Message.obtain()               // センサー値取得イベントを発生させる
                msg.arg1 = 1                                        // センサー値取得イベントを示す値
                msg.arg2 = event.sensor.type                        // センサーの種類を渡す
                msg.obj = event.values.clone()                      // センサーの値をコピーして渡す
                if (handler != null) handler?.sendMessage(msg)      // メッセージ送信

            }
            catch(e : Exception)
            {
                Log.e("onSensorChanged", e.toString())
                e.printStackTrace()
            }
        }
    }

    override fun onAccuracyChanged(event: Sensor?, p1: Int) {
        //TODO("Not yet implemented")
    }

    //  ステップカウンターを開始します。
    public fun startStepCounter(ipaddr : String, port : Int)
    {
        Thread {
            // メインアクティビティーに通知するメッセージ
            val msg = Message.obtain()
            msg.arg1 = 3

            val address = InetSocketAddress(ipaddr, port)
            do {
                try {
                    // サーバーに接続
                    mSoket = Socket()
                    if (mSoket != null) {
                        mSoket?.connect(address, 3000)
                    }
                } catch (e: Exception) {
                    Log.e("SensorServiceListener", "fail to connect peer.")
                    e.printStackTrace()
                    // メインアクティビティーに通知
                    msg.arg2 = 101 // Soket Connect Error
                    msg.obj = "fail to connect Server."
                    if (handler != null) handler?.sendMessage(msg)
                    break;
                }

                // センサーサービスマネージャーを取得
                val sensorManager: SensorManager =
                    getSystemService(Context.SENSOR_SERVICE) as SensorManager
                // ステップカウンターセンサーを取得
                val accel: Sensor? = sensorManager.getDefaultSensor(
                    Sensor.TYPE_STEP_COUNTER
                )
                if (accel == null) {
                    // メインアクティビティーに通知
                    msg.arg2 = 102// no support Step Counter.
                    msg.obj = "no support Step Counter."
                    if (handler != null) handler?.sendMessage(msg)

                    mSoket?.close()
                    mSoket = null
                    break
                } else {
                    // ステップカウンターセンサーのリスナーをセット
                    sensorManager.registerListener(
                        this,
                        accel,
                        SensorManager.SENSOR_DELAY_NORMAL
                    )
                }

                var pm : PowerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag")
                mWakeLock?.acquire()


                // スリープ時にWifiの接続を維持する
                try {
                    val contentResolver: ContentResolver = getContentResolver()

                    mWifiNeverPolocy =
                        Settings.System.getInt(contentResolver, Settings.Global.WIFI_SLEEP_POLICY)

                    Log.i("WIFISetting", "policy=" + mWifiNeverPolocy)
                    if (mWifiNeverPolocy != Settings.Global.WIFI_SLEEP_POLICY_NEVER) {
                        Settings.System.putInt(contentResolver, Settings.Global.WIFI_SLEEP_POLICY, Settings.Global.WIFI_SLEEP_POLICY_NEVER);
                    }
                } catch (e: Exception) {
                    Log.e("WIFISetting", e.toString())
                }


                // メインアクティビティーに通知
                mIsRunningStepCounter = true
                msg.arg2 = 1//
                msg.obj = "Start."
                if (handler != null) handler?.sendMessage(msg)
            }while( false )
        }.start()
    }

    //  ステップカウンターを停止します。
    public fun stopStepCounter(){
        try {
            try {
                // センサーサービスマネージャーを取得
                val sensorManager: SensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                // リスナーを解除します。
                sensorManager.unregisterListener(this)
            } catch (e: Exception) {
                Log.e("WIFISetting", e.toString())
            }


            // スリープ時にWifiの接続を維持の情報を元に戻す
            try {
                val contentResolver: ContentResolver = getContentResolver()
                Settings.System.putInt(contentResolver, Settings.Global.WIFI_SLEEP_POLICY, mWifiNeverPolocy);
            } catch (e: Exception) {
                Log.e("WIFISetting", e.toString())
            }

            // ソケットを閉じる
            try {
                mSoket?.close()
                mSoket = null
            } catch (e: Exception) {
                Log.e("WIFISetting", e.toString())
            }

            // Wake Lockの解放
            try {
                mWakeLock?.release()
                mWakeLock = null
            } catch (e: Exception) {
                Log.e("WIFISetting", e.toString())
            }

            mIsRunningStepCounter = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}