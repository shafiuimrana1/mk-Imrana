package com.mkimrana.transfer

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val bg = Color.rgb(5, 11, 25)
    private val panel = Color.rgb(15, 27, 49)
    private val mint = Color.rgb(0, 229, 168)
    private lateinit var status: TextView
    private lateinit var qrImage: ImageView
    private lateinit var addressInput: EditText
    private lateinit var progress: ProgressBar
    private var selectedUri: Uri? = null
    private var server: ServerSocket? = null

    private val chooseFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            status.text = "Selected: ${displayName(uri)}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 24)
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "mk Imrana"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val tagline = TextView(this).apply {
            text = "FAST  •  SECURE  •  FILE TRANSFER"
            textSize = 11f
            setTextColor(mint)
            gravity = Gravity.CENTER
        }
        val intro = TextView(this).apply {
            text = "Share files directly between Android phones on the same local Wi-Fi network."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 14, 8, 18)
        }
        qrImage = ImageView(this).apply {
            visibility = View.GONE
            adjustViewBounds = true
            setBackgroundColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
        }
        status = TextView(this).apply {
            text = "Ready to connect"
            textSize = 13f
            setTextColor(mint)
            gravity = Gravity.CENTER
            setPadding(8, 14, 8, 14)
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        addressInput = EditText(this).apply {
            hint = "Receiver IP address (example: 192.168.1.10)"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            setPadding(14, 10, 14, 10)
            setBackgroundColor(panel)
        }
        content.addView(title, matchWrap())
        content.addView(tagline, matchWrap())
        content.addView(intro, matchWrap())
        content.addView(makeButton("📤  SEND FILES", mint) { chooseFile.launch(arrayOf("*/*")) })
        content.addView(makeButton("📥  RECEIVE FILES", Color.rgb(22, 115, 230)) { startReceiver() })
        content.addView(makeButton("🔳  GENERATE CONNECTION QR", Color.rgb(120, 60, 220)) { showConnectionQr() })
        content.addView(makeButton("📷  SCAN QR / ENTER RECEIVER IP", Color.rgb(240, 145, 20)) {
            status.text = "For this starter build, enter the receiver IP below. QR scanning UI is planned for the next build."
            addressInput.requestFocus()
        })
        content.addView(addressInput, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        content.addView(makeButton("⚡  SEND SELECTED FILE", Color.rgb(0, 145, 110)) { sendSelectedFile() })
        content.addView(qrImage, LinearLayout.LayoutParams(dp(220), dp(220)).apply { topMargin = dp(16) })
        content.addView(progress, matchWrap().apply { topMargin = dp(12) })
        content.addView(status, matchWrap())
        val note = TextView(this).apply {
            text = "Tip: both phones must be on the same Wi-Fi network. Keep the receiver screen open. Use only with people you trust."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 8)
        }
        content.addView(note, matchWrap())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun makeButton(label: String, color: Int, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(if (color == mint) Color.BLACK else Color.WHITE)
            setBackgroundColor(color)
            setOnClickListener { action() }
            val lp = LinearLayout.LayoutParams(-1, dp(54))
            lp.topMargin = dp(9)
            layoutParams = lp
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun showConnectionQr() {
        val ip = localIpAddress() ?: "Connect-to-same-Wi-Fi"
        val data = "MKIMRANA|$ip|8989"
        try {
            val matrix = MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, 420, 420)
            val bmp = Bitmap.createBitmap(420, 420, Bitmap.Config.RGB_565)
            for (x in 0 until 420) for (y in 0 until 420) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
            qrImage.setImageBitmap(bmp)
            qrImage.visibility = View.VISIBLE
            status.text = "Connection QR created. Receiver address: $ip:8989"
        } catch (e: Exception) {
            status.text = "Could not create QR: ${e.message}"
        }
    }

    private fun startReceiver() {
        if (server != null) {
            status.text = "Receiver is already running."
            return
        }
        thread {
            try {
                server = ServerSocket(8989)
                val ip = localIpAddress() ?: "unknown"
                runOnUiThread { status.text = "Receiving on $ip:8989 — keep this screen open." }
                while (!server!!.isClosed) {
                    val socket = server!!.accept()
                    thread { receiveOne(socket) }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Receiver stopped: ${e.message}" }
            }
        }
    }

    private fun receiveOne(socket: Socket) {
        try {
            socket.use { s ->
                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                val nameLength = input.readInt().coerceIn(1, 1024)
                val nameBytes = ByteArray(nameLength)
                input.readFully(nameBytes)
                val name = String(nameBytes, Charsets.UTF_8).replace(Regex("[^A-Za-z0-9._ -]"), "_")
                val size = input.readLong()
                if (size < 0 || size > 2_000_000_000L) throw IOException("Invalid file size")
                val dir = getExternalFilesDir("received") ?: filesDir
                val outFile = File(dir, name)
                FileOutputStream(outFile).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var remaining = size
                    var copied = 0L
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) throw EOFException("Transfer interrupted")
                        out.write(buffer, 0, read)
                        remaining -= read
                        copied += read
                        val percent = if (size == 0L) 100 else (copied * 100 / size).toInt()
                        runOnUiThread { progress.progress = percent }
                    }
                }
                runOnUiThread { status.text = "Received: ${outFile.name} (${size / 1024} KB). Saved in app's received folder." }
            }
        } catch (e: Exception) {
            runOnUiThread { status.text = "Receive error: ${e.message}" }
        }
    }

    private fun sendSelectedFile() {
        val uri = selectedUri
        if (uri == null) {
            status.text = "First tap SEND FILES and choose a file."
            return
        }
        val host = addressInput.text.toString().trim()
        if (host.isBlank()) {
            status.text = "Enter the receiver phone's IP address."
            return
        }
        thread {
            try {
                val name = displayName(uri)
                val size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                if (size < 0) throw IOException("Could not determine file size")
                Socket(host, 8989).use { socket ->
                    val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                    val nameBytes = name.toByteArray(Charsets.UTF_8)
                    out.writeInt(nameBytes.size)
                    out.write(nameBytes)
                    out.writeLong(size)
                    contentResolver.openInputStream(uri).use { input ->
                        if (input == null) throw IOException("Cannot open selected file")
                        val buffer = ByteArray(64 * 1024)
                        var sent = 0L
                        var n: Int
                        while (input.read(buffer).also { n = it } != -1) {
                            out.write(buffer, 0, n)
                            sent += n
                            val percent = if (size == 0L) 100 else (sent * 100 / size).toInt()
                            runOnUiThread {
                                progress.progress = percent
                                status.text = "Sending $name — $percent%"
                            }
                        }
                        out.flush()
                    }
                }
                runOnUiThread { status.text = "Sent successfully: $name"; progress.progress = 100 }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Send failed: ${e.message}. Check Wi-Fi and IP." }
            }
        }
    }

    private fun displayName(uri: Uri): String {
        var name = "shared_file"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        return name
    }

    private fun localIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) return ip
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    override fun onDestroy() {
        try { server?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
