package com.resukisu.resukisu.data.webui

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.resukisu.resukisu.domain.model.WebUiCommandResult
import com.resukisu.resukisu.domain.model.WebUiProcess
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Showing a watch module's WebUI on the phone. The watch opens the phone's WebUI activity with
 * RemoteActivityHelper; the phone then sends each file read and command of the page back to the
 * watch over a Data Layer channel, where the watch serves it with its own root access. Every call
 * carries the one-time token of the session the watch started, limited to that module.
 *
 * Frames on a channel are a type byte, a 4 byte length and the payload.
 */
object WearWebUiProtocol {
    const val RPC_PATH = "/resukisu/webui/rpc"
    const val CLOSED_PATH = "/resukisu/webui/closed"
    /** Advertised by the app on every device, through `android_wear_capabilities`. */
    const val CAPABILITY = "resukisu_webui"
    const val SCHEME = "resukisu-webui"
    const val HOST = "open"

    const val FRAME_OK: Byte = 0
    const val FRAME_ERROR: Byte = 1
    const val FRAME_STDOUT: Byte = 2
    const val FRAME_STDERR: Byte = 3
    const val FRAME_EXIT: Byte = 4

    fun DataOutputStream.writeFrame(type: Byte, payload: ByteArray) {
        writeByte(type.toInt())
        writeInt(payload.size)
        write(payload)
        flush()
    }

    fun DataInputStream.readFrame(): Pair<Byte, ByteArray> {
        val type = readByte()
        val payload = ByteArray(readInt())
        readFully(payload)
        return type to payload
    }

    fun WebUiCommandResult.toJson(): String =
        JSONObject().put("code", code).put("stdout", stdout).put("stderr", stderr).toString()

    fun commandResult(json: String): WebUiCommandResult = JSONObject(json).let {
        WebUiCommandResult(it.getInt("code"), it.optString("stdout"), it.optString("stderr"))
    }
}

/** The phone session the watch accepts calls for: one module, identified by a random token. */
object WearWebUiSession {
    private data class Session(val token: String, val moduleId: String)

    @Volatile
    private var current: Session? = null

    fun start(token: String, moduleId: String) {
        current = Session(token, moduleId)
    }

    /** The module of the session [token] belongs to, or null when it is not the current session. */
    fun moduleFor(token: String): String? = current?.takeIf { it.token == token }?.moduleId

    fun end(token: String) {
        if (current?.token == token) current = null
    }
}

/**
 * The phone's [WebUiBackend] for a watch module: each call opens a channel to [nodeId] and is
 * answered by the watch. Calls block, as the WebView calls its bridge and file handler off the main
 * thread.
 */
class RemoteWebUiBackend(context: Context, private val nodeId: String, private val token: String) : WebUiBackend {
    private val client = Wearable.getChannelClient(context)

    private fun <T> call(op: String, arg: String, extra: Int = 0, read: (DataInputStream) -> T): T {
        val channel = Tasks.await(client.openChannel(nodeId, WearWebUiProtocol.RPC_PATH), 15, TimeUnit.SECONDS)
        try {
            val output = DataOutputStream(Tasks.await(client.getOutputStream(channel)).buffered())
            val request = JSONObject().put("token", token).put("op", op).put("arg", arg).put("extra", extra)
            with(WearWebUiProtocol) { output.writeFrame(FRAME_OK, request.toString().toByteArray()) }
            val input = DataInputStream(Tasks.await(client.getInputStream(channel)).buffered())
            return read(input)
        } finally {
            client.close(channel)
        }
    }

    /** A single answer frame: its payload, or null when the watch answered with an error frame. */
    private fun single(op: String, arg: String, extra: Int = 0): ByteArray? = call(op, arg, extra) { input ->
        val (type, payload) = with(WearWebUiProtocol) { input.readFrame() }
        payload.takeIf { type == WearWebUiProtocol.FRAME_OK }
    }

    override fun execute(command: String): WebUiCommandResult = runCatching {
        single("exec", command)?.let { WearWebUiProtocol.commandResult(it.decodeToString()) }
    }.getOrNull() ?: WebUiCommandResult(-1, "", "")

    override fun spawn(command: String): WebUiProcess = object : WebUiProcess {
        override fun start(
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onComplete: (WebUiCommandResult) -> Unit,
        ) {
            thread(name = "remote-webui-spawn") {
                val result = runCatching {
                    call("spawn", command) { input ->
                        var exit: WebUiCommandResult? = null
                        while (exit == null) {
                            val (type, payload) = with(WearWebUiProtocol) { input.readFrame() }
                            when (type) {
                                WearWebUiProtocol.FRAME_STDOUT -> onStdout(payload.decodeToString())
                                WearWebUiProtocol.FRAME_STDERR -> onStderr(payload.decodeToString())
                                WearWebUiProtocol.FRAME_EXIT -> exit = WearWebUiProtocol.commandResult(payload.decodeToString())
                                else -> exit = WebUiCommandResult(-1, "", "")
                            }
                        }
                        exit
                    }
                }.getOrElse { WebUiCommandResult(-1, "", it.message.orEmpty()) }
                onComplete(result)
            }
        }

        override fun close() = Unit
    }

    override fun listModules(): String = runCatching { single("modules", "")?.decodeToString() }.getOrNull() ?: "[]"

    override fun openFile(path: String): InputStream? =
        runCatching { single("file", path)?.inputStream() }.getOrNull()

    override fun listPackages(type: String): String =
        runCatching { single("packages", type)?.decodeToString() }.getOrNull() ?: "[]"

    override fun getPackagesInfo(packageNamesJson: String): String =
        runCatching { single("packagesInfo", packageNamesJson)?.decodeToString() }.getOrNull() ?: "[]"

    override fun iconPng(packageName: String, size: Int): ByteArray? =
        runCatching { single("icon", packageName, size) }.getOrNull()

    /** Tells the watch the page was closed, so it ends the session and reloads its modules. */
    fun close(context: Context) {
        Wearable.getMessageClient(context).sendMessage(nodeId, WearWebUiProtocol.CLOSED_PATH, token.toByteArray())
    }
}

/**
 * The watch's answer to one phone call on [channel], served by [backend]. File reads are limited to
 * the session module's webroot.
 */
internal suspend fun serveWebUiCall(
    client: ChannelClient,
    channel: ChannelClient.Channel,
    backend: WebUiBackend,
    ensurePackages: suspend () -> Unit,
) {
    try {
        val input = DataInputStream(Tasks.await(client.getInputStream(channel)).buffered())
        val output = DataOutputStream(Tasks.await(client.getOutputStream(channel)).buffered())
        with(WearWebUiProtocol) {
            val request = JSONObject(input.readFrame().second.decodeToString())
            val moduleId = WearWebUiSession.moduleFor(request.optString("token"))
            if (moduleId == null) {
                output.writeFrame(FRAME_ERROR, ByteArray(0))
                return
            }
            val arg = request.optString("arg")
            fun answer(payload: ByteArray?) =
                if (payload == null) output.writeFrame(FRAME_ERROR, ByteArray(0)) else output.writeFrame(FRAME_OK, payload)
            when (request.optString("op")) {
                "exec" -> answer(backend.execute(arg).toJson().toByteArray())
                "modules" -> answer(backend.listModules().toByteArray())
                "packages" -> { ensurePackages(); answer(backend.listPackages(arg).toByteArray()) }
                "packagesInfo" -> { ensurePackages(); answer(backend.getPackagesInfo(arg).toByteArray()) }
                "icon" -> answer(backend.iconPng(arg, request.optInt("extra", 128)))
                "file" -> {
                    val webRoot = File("/data/adb/modules/$moduleId/webroot")
                    val file = File(arg).normalize()
                    answer(if (file.path.startsWith(webRoot.path + "/")) backend.openFile(file.path)?.use { it.readBytes() } else null)
                }
                "spawn" -> {
                    val done = java.util.concurrent.CountDownLatch(1)
                    backend.spawn(arg).start(
                        onStdout = { synchronized(output) { output.writeFrame(FRAME_STDOUT, it.toByteArray()) } },
                        onStderr = { synchronized(output) { output.writeFrame(FRAME_STDERR, it.toByteArray()) } },
                        onComplete = { result ->
                            synchronized(output) { output.writeFrame(FRAME_EXIT, result.toJson().toByteArray()) }
                            done.countDown()
                        },
                    )
                    done.await()
                }
                else -> answer(null)
            }
        }
    } catch (_: Exception) {
        // The phone closed the channel or the connection dropped; the call simply ends.
    } finally {
        client.close(channel)
    }
}
