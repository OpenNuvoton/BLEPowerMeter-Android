package com.example.smartpowermeasurement

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.updateListItems
import com.example.smartpowermeasurement.Bluetooth.BluetoothLeData
import com.example.smartpowermeasurement.Bluetooth.BluetoothLeDataManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.mikhaellopez.circularprogressbar.CircularProgressBar

class MainActivity : AppCompatActivity() {
    private  val TAG = javaClass.name

    //BLE
    private var _Notify_BC: BluetoothLeData.CharacteristicData? = null
//    private var _Write_BC: BluetoothLeData.CharacteristicData? = null
    private var _BleData: BluetoothLeData? = null
    private val _bdm = BluetoothLeDataManager.getInstance()
    private val REQUEST_CODE_BLUETOOTH = 666
    private var isBleConnected = false // 跟蹤藍牙連接狀態

    //SCAN
    private var _scanResultArray: ArrayList<ScanResult> = ArrayList<ScanResult>()
    private var _scanResultDeviceArray: ArrayList<String> = ArrayList<String>()
    private lateinit var _alertDialog: MaterialDialog

    //UI
    private var _bleButton: ImageButton? = null
    private lateinit var voltageButton: ImageButton
    private lateinit var currentButton: ImageButton
    private lateinit var powerButton: ImageButton
    private  lateinit  var _voltage_text: TextView
    private  lateinit  var _current_text: TextView
    private  lateinit  var _frequency_text: TextView
    private  lateinit  var _power_text: TextView
    private lateinit var displayProgressBar: CircularProgressBar


    //Data LineChart
    private lateinit var lineChart: LineChart
    private val voltageEntries = ArrayList<Entry>()
    private val currentEntries = ArrayList<Entry>()
    private val powerEntries = ArrayList<Entry>()
    private val frequencyEntries = ArrayList<Entry>()
    private var selectedEntries: ArrayList<Entry> = voltageEntries
    private var selectedLabel = "Voltage (V)"
    private var selectedColor = Color.parseColor("#9ABDC4")




    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // 設置狀態欄為透明
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = Color.TRANSPARENT


        _voltage_text = findViewById(R.id.voltage_text)
        _current_text = findViewById(R.id.current_text)
        _frequency_text = findViewById(R.id.frequency_text)
        _power_text = findViewById(R.id.power_text)

        _bleButton = findViewById(R.id.ble_button)
        _bleButton!!.setOnClickListener(onClickBleButton)
        _alertDialog = MaterialDialog(this)
        // 找到 LineChart
        lineChart = findViewById(R.id.lineChart)

        // 初始化按鈕
        voltageButton = findViewById(R.id.voltage_button)
        currentButton = findViewById(R.id.current_button)
        powerButton = findViewById(R.id.power_button)

        // 設置按鈕點擊事件
        voltageButton.setOnClickListener {
//    highlightButton(voltageButton)
            selectedEntries = voltageEntries
            selectedLabel = "Voltage (V)"
            selectedColor = Color.parseColor("#9ABDC4")  // 改成 #9ABDC4
            updateChart(selectedEntries, selectedLabel, selectedColor)
        }

        currentButton.setOnClickListener {
//    highlightButton(currentButton)
            selectedEntries = currentEntries
            selectedLabel = "Current (A)"
            selectedColor = Color.parseColor("#94B07E")  // 改成 #94B07E
            updateChart(selectedEntries, selectedLabel, selectedColor)
        }

        powerButton.setOnClickListener {
//    highlightButton(powerButton)
            selectedEntries = powerEntries
            selectedLabel = "Power (W)"
            selectedColor = Color.parseColor("#C1AE82")  // 改成 #C1AE82
            updateChart(selectedEntries, selectedLabel, selectedColor)
        }

        val circularProgressBar = findViewById<CircularProgressBar>(R.id.circularProgressBar)
        circularProgressBar.apply {
            // 設置進度和動畫
            progress = 0f
            setProgressWithAnimation(75f, 1000) // 1秒動畫設置進度到50%

            // 設置進度最大值為100（左0，右100）
            progressMax = 100f

            progressBarColor = Color.parseColor("#8AA4B6")

            // 設置背景進度條顏色為透明
            backgroundProgressBarColor = Color.TRANSPARENT

            // 設置進度條寬度
            progressBarWidth = 15f // 設置進度條寬度
            backgroundProgressBarWidth = 5f // 設置背景進度條寬度

            // 設置其他屬性
            roundBorder = true // 圓角邊緣
            startAngle = 225f // 從180度開始，形成半圓
            progressDirection = CircularProgressBar.ProgressDirection.TO_RIGHT // 進度方向從左到右

            // 設置進度條為不確定模式
//            indeterminateMode = true

            // 調整儀表的外觀，這裡是設置為一個下方有缺口的半圓形
        }

        displayProgressBar = findViewById<CircularProgressBar>(R.id.displayProgressBar)
        displayProgressBar.apply {
            // 設置進度和動畫
            progress = 0f
            setProgressWithAnimation(0f, 1000) // 1秒動畫設置進度到50%

            // 設置進度最大值為100（左0，右100）
            progressMax = 100f

            // 設置漸變的進度條顏色為綠色漸變
            progressBarColorStart = Color.parseColor("#BFD959") // 淺綠色
            progressBarColorEnd = Color.parseColor("#008000")   // 深綠色
            progressBarColorDirection = CircularProgressBar.GradientDirection.LEFT_TO_RIGHT // 從左到右的漸變

            // 設置背景進度條顏色為透明
            backgroundProgressBarColor = Color.TRANSPARENT

            // 設置進度條寬度
            progressBarWidth = 15f // 設置進度條寬度
            backgroundProgressBarWidth = 5f // 設置背景進度條寬度

            // 設置其他屬性
            roundBorder = true // 圓角邊緣
            startAngle = 225f // 從180度開始，形成半圓
            progressDirection = CircularProgressBar.ProgressDirection.TO_RIGHT // 進度方向從左到右

            // 設置進度條為不確定模式
//            indeterminateMode = true

            // 調整儀表的外觀，這裡是設置為一個下方有缺口的半圓形
        }

        // 設置數據
        setupLineChart()

        this.checkAndRequestBluetoothPermission()
    }

//    private fun highlightButton(selectedButton: ImageButton) {
//        // 重置所有按鈕的背景顏色
//        voltageButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
//        currentButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
//        powerButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
//
//        // 設置選中按鈕的背景顏色
//        selectedButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
//    }

    /**
     * 點擊藍牙連線
     */
    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private val onClickBleButton = View.OnClickListener {

        if (isBleConnected) {
            // 如果已經連接，則執行斷開操作
            _BleData?.setDisConnect()// 斷開藍牙連接
            isBleConnected = false
            Log.d(TAG, "藍牙連接已中斷")
        } else {
            // 如果尚未連接，則執行連接操作
            _alertDialog = MaterialDialog(this)
                .cancelOnTouchOutside(false)
                .cancelable(false)
                .title(text = "BLE Device Scanning...")
                .listItems(items = _scanResultDeviceArray) { dialog, index, text ->
                    // 點擊 BLE 裝置事件
                    if (index >= _scanResultArray.size) return@listItems // 空指針返回

                    _BleData = _bdm.getBluetoothLeData(this, _scanResultArray[index].device.address)

                    _bdm.scanLeDevice(false, this, scanCallback) // 停止搜尋
                    this.connectBle(bleData = _BleData!!) // 藍牙連線
//                    isBleConnected = true // 設置連接狀態為 true

                    runOnUiThread {
                        _bleButton!!.setImageDrawable(getDrawable(R.drawable.connecting))
                    }

                    _alertDialog.dismiss()
                }
                .negativeButton(null, "cancel") { materialDialog: MaterialDialog? ->
                    Log.d(TAG, "ScanBleDevice Cancel")
                    _bdm.scanLeDevice(false, this, scanCallback) // 停止搜尋
                    _alertDialog.dismiss()
                }

            _alertDialog.show()

            this.checkAndRequestBluetoothPermission()

            _bdm.scanLeDevice(true, this, scanCallback)
        }
    }

    /**
     * ＢＬＥ藍芽連線
     */
    private fun connectBle(bleData: BluetoothLeData) {

        bleData.setOnStateChangeListener(onStateChangeListener)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            return
        }

        bleData.connectLeDevice {
            Log.i("MainActivity", "connectLeDevice:$it")
            if (it != true) {
                onStateChangeListener.onStateChange(bleData.getbleMacAddress(), 0, 0)
                return@connectLeDevice
            }
            for (bs in bleData.servicesDataArray) {
                for (bc in bs.characteristicDataArray) {
                    Log.i("MainActivity", "characteristic:" + bc.uuid)

//                    //專門用來寫入之特徵
//                    if (bc.uuid.indexOf("50515253-5455-5657-5859-5a5b5c5d5e5f") > -1) {
//                        _Write_BC = bc
//                    }

                    //專門用來監聽之特徵
                    //0000ffe1-0000-1000-8000-00805f9b34fb
                    if (bc.uuid.indexOf("0000ffe1-0000-1000-8000-00805f9b34fb") > -1) {
                        _Notify_BC = bc
                        bc.setNotify(true, myNotifyListener)
                        Log.i("MainActivity", "setNotify:" + bc.uuid)
                    }
//                    if (bc.uuid.indexOf("30313233-3435-3637-3839-3a3b3c3d3e3f") > -1) {
//                        _Notify_BC = bc
//                        bc.setNotify(true, myNotifyListener)
//                        Log.i("MainActivity", "setNotify:" + bc.uuid)
//                    }
                }
            }
        }
    }

    /**
     * ＢＬＥ搜尋結果
     */
    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d(TAG, "onScanResult  callbackType:$callbackType   result:$result")

            if(result.scanRecord==null){
                return
            }

            result.scanRecord
            var displayName = result.scanRecord!!.deviceName +"\n"+result.device.address

            if (!_scanResultArray.contains(result) && !_scanResultDeviceArray.contains(displayName)) {
                if (result.scanRecord!!.deviceName != null) {

                    _scanResultArray.add(result)
                    Log.d(TAG, "onScanResult  deviceName:" + result.scanRecord!!.deviceName)
                    _scanResultDeviceArray.add(displayName)
                    _alertDialog.updateListItems(items = _scanResultDeviceArray )
                }
            }

        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            Log.d(TAG, "results:$results")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d(TAG, "errorCode:$errorCode")
        }
    }
    /**
     * 監聽ＢＬＥ連線變化
     */
    private var onStateChangeListener = BluetoothLeData.onStateChange { MAC, status, newState ->

        Log.i(
            "onStateChangeListener",
            "MAC:" + MAC + "  status:" + status + "  newState:" + newState
        )

        // 根據 status 更新連接狀態
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                // 當 status 為 GATT_SUCCESS 時，表示連接成功
                isBleConnected = newState == BluetoothProfile.STATE_CONNECTED
                Thread.sleep(500)
                _bleButton!!.setImageDrawable(getDrawable(R.drawable.connected))
            }
            BluetoothProfile.STATE_DISCONNECTED-> {
                // 當 status 為 GATT_FAILURE 時，表示連接失敗
                isBleConnected = false
                _bleButton!!.setImageDrawable(getDrawable(R.drawable.csan))
            }
            else -> {
//                // 其他狀態處理（如果需要）
                _bleButton!!.setImageDrawable(getDrawable(R.drawable.csan))
                isBleConnected = newState == BluetoothProfile.STATE_CONNECTED
            }
        }

    }

    private var xCounter = 0f
    /***
     * 收通知的地方
     */
    private val myNotifyListener = BluetoothLeData.notifCallBack { bleMAC, UUID, value ->
        if (value.size >= 14) {
            runOnUiThread {
            val voltageValue = parseVoltage(value)
            val currentValue = parseCurrent(value)
            val powerValue = parsePower(value)
            val frequencyValue = parseFrequency(value)

            val xValue = xCounter
            xCounter += 1


                // 限制小數點位數後再加入到 Entries
                val formattedVoltage = String.format("%.2f", voltageValue).toFloat()
                val formattedCurrent = String.format("%.4f", currentValue).toFloat()
                val formattedPower = String.format("%.2f", powerValue).toFloat()
                val formattedFrequency = String.format("%.0f", frequencyValue).toFloat()

                // 添加格式化後的數據到 Entries
                voltageEntries.add(Entry(xValue, formattedVoltage))
                currentEntries.add(Entry(xValue, formattedCurrent))
                powerEntries.add(Entry(xValue, formattedPower))
                frequencyEntries.add(Entry(xValue, formattedFrequency))

                // 更新圖表，顯示當前選中的資料類型
                updateChart(selectedEntries, selectedLabel, selectedColor)

                // 更新 TextView 顯示數據
                _voltage_text.text = "$formattedVoltage V"  // 限制到小數點後兩位
                _current_text.text = "$formattedCurrent A"  // 限制到小數點後四位
                _power_text.text = "$formattedPower W"     // 限制到小數點後兩位
                _frequency_text.text = "$formattedFrequency Hz"  // 不顯示小數點


                // 更新 CircularProgressBar 根據功率變化
                displayProgressBar.setProgressWithAnimation((powerValue.toFloat() * 0.75f), 150)  // 將 powerValue 乘以 0.75 並使用動畫更新進度條

            }

        } else {
            println("資料長度不足")
        }
    }

    private fun setupLineChart() {
        // 設置圖表屬性
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM // 設置 X 軸位置
        lineChart.xAxis.isEnabled = false // 取消顯示 X 軸
        lineChart.axisRight.isEnabled = false // 不顯示右側 Y 軸
        lineChart.description.isEnabled = false // 取消圖表的描述（標題）
        lineChart.setTouchEnabled(false) // 啟用觸摸事件
        lineChart.isDragEnabled = false // 啟用拖動
        lineChart.setScaleEnabled(false) // 啟用縮放
//        lineChart.xAxis.setLabelCount(5, true) // 可選：設置X軸標籤數量
        lineChart.setVisibleXRangeMaximum(400f) // 設置可見X軸範圍的最大值
        lineChart.xAxis.setDrawGridLines(false) // 取消 X 軸的格線
        // 設置 Y 軸文字顏色為白色
        lineChart.axisLeft.textColor = Color.WHITE
        // 設置左下角的標題顏色為白色
        lineChart.legend.textColor = Color.WHITE

    }

    private fun updateChart(entries: ArrayList<Entry>, label: String, color: Int) {
        // 限制最大數據點數量為400
        while (entries.size > 400) {
            entries.removeAt(0)
        }

        val dataSet = LineDataSet(entries, label)
        dataSet.color = color
        dataSet.setDrawCircles(false)
        dataSet.setDrawCircleHole(false)
        dataSet.setDrawFilled(true)
        dataSet.setDrawValues(false) // 取消每個點的數字標籤
        dataSet.fillColor = color
        dataSet.fillAlpha = 110

        val lineData = LineData(dataSet)
        lineChart.data = lineData

        // 移動視口到最新的數據點
        lineChart.moveViewToX(lineData.entryCount.toFloat())

        // 刷新圖表
        lineChart.invalidate()
    }

    private fun parseVoltage(value: ByteArray): Double {
        val voltage = ((value[4].toInt() and 0xFF) shl 16) or
                ((value[3].toInt() and 0xFF) shl 8) or
                (value[2].toInt() and 0xFF)
        return voltage / 1000.0
    }

    private fun parseCurrent(value: ByteArray): Double {
        val current = ((value[7].toInt() and 0xFF) shl 16) or
                ((value[6].toInt() and 0xFF) shl 8) or
                (value[5].toInt() and 0xFF)
        return current / 128000.0
    }

    private fun parsePower(value: ByteArray): Double {
        val power = ((value[10].toInt() and 0xFF) shl 16) or
                ((value[9].toInt() and 0xFF) shl 8) or
                (value[8].toInt() and 0xFF)
        return power / 1000.0
    }

    private fun parseFrequency(value: ByteArray): Double {
        val frequency = ((value[13].toInt() and 0xFF) shl 16) or
                ((value[12].toInt() and 0xFF) shl 8) or
                (value[11].toInt() and 0xFF)
        return frequency / 1000.0
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkAndRequestBluetoothPermission() {
        // 檢查是否已經獲得 BLUETOOTH_SCAN 和 ACCESS_FINE_LOCATION 權限
        val bluetoothScanPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
        val fineLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val bluetoothConnectPermission = ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)
        if (bluetoothScanPermission != PackageManager.PERMISSION_GRANTED
            || fineLocationPermission != PackageManager.PERMISSION_GRANTED
            || bluetoothConnectPermission != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "checkAndRequestBluetoothPermission:requestPermissions")
            // 權限未授予，請求權限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_CODE_BLUETOOTH
            )
        } else {
            // 已經擁有權限，直接調用需要權限的代碼
            Log.d(TAG, "checkAndRequestBluetoothPermission:OK")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_BLUETOOTH -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onRequestPermissionsResult:用戶授予了權限")
                    // 用戶授予了權限
                } else {
                    Log.d(TAG, "onRequestPermissionsResult:用戶拒絕了權限")
                    // 用戶拒絕了權限
                }
            }
        }
    }
}

