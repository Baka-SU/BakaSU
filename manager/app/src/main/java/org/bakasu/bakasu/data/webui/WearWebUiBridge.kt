package org.bakasu.bakasu.data.webui

import android.os.SystemClock
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.concurrent.thread
import org.bakasu.bakasu.domain.model.WebUiCommandResult
import org.bakasu.bakasu.domain.model.WebUiProcess
import org.json.JSONObject

/** Frames on an authenticated secure RFCOMM connection: type byte, 4 byte length and payload. */
object WearWebUiProtocol {
    const val CLAIM_PATH = "/resukisu/webui/claim"
    const val RPC_PATH = "/resukisu/webui/rpc"
    const val CLOSED_PATH = "/resukisu/webui/closed"

    const val FRAME_OK: Byte = 0
    const val FRAME_ERROR: Byte = 1
    const val FRAME_STDOUT: Byte = 2
    const val FRAME_STDERR: Byte = 3
    const val FRAME_EXIT: Byte = 4

    /** One part of a file read; a zero-length chunk ends the stream. */
    const val FRAME_CHUNK: Byte = 5

    /** Keeps every frame small enough for a watch heap. */
    const val CHUNK_SIZE = 64 * 1024

    fun DataOutputStream.writeFrame(type: Byte, payload: ByteArray) {
        require(payload.size <= MAX_FRAME_SIZE) { "Frame too large" }
        writeByte(type.toInt())
        writeInt(payload.size)
        write(payload)
        flush()
    }

    private const val MAX_FRAME_SIZE = 8 * 1024 * 1024

    fun DataInputStream.readFrame(): Pair<Byte, ByteArray> {
        val type = readByte()
        val size = readInt()
        require(size in 0..MAX_FRAME_SIZE) { "Invalid frame size" }
        val payload = ByteArray(size)
        readFully(payload)
        return type to payload
    }

    fun WebUiCommandResult.toJson(): String = JSONObject().put("code", code).put("stdout", stdout).put("stderr", stderr).toString()

    fun commandResult(json: String): WebUiCommandResult = JSONObject(json).let {
        WebUiCommandResult(it.getInt("code"), it.optString("stdout"), it.optString("stderr"))
    }
}

/**
 * The watch's only WebUI session: one module, offered to one phone Bluetooth peer. That node may claim it once
 * within [CLAIM_WINDOW_MS] of the offer; afterwards only calls from the same peer carrying the
 * session's random token are served. A new offer or the phone closing the page ends it.
 */
object WearWebUiSession {
    private const val CLAIM_WINDOW_MS = 30_000L

    private class Session(
        val token: String,
        val moduleId: String,
        val moduleName: String,
        val phonePeer: String,
        val offeredAt: Long,
    ) {
        var claimed = false
    }

    private var current: Session? = null

    val isActive: Boolean @Synchronized get() = current != null

    @Synchronized
    fun endCurrent() {
        current = null
    }

    /** Offers a session for [moduleId] to [phonePeer]; the returned token withdraws it. */
    @Synchronized
    fun offer(moduleId: String, moduleName: String, phonePeer: String): String = UUID.randomUUID().toString().also {
        current = Session(it, moduleId, moduleName, phonePeer, SystemClock.elapsedRealtime())
    }

    /** The pending session as JSON for the node it was offered to, at most once; otherwise null. */
    @Synchronized
    fun claim(peerAddress: String): JSONObject? {
        val session = current?.takeIf {
            !it.claimed && it.phonePeer == peerAddress &&
                SystemClock.elapsedRealtime() - it.offeredAt < CLAIM_WINDOW_MS
        } ?: return null
        session.claimed = true
        return JSONObject().put("token", session.token).put("module", session.moduleId).put("name", session.moduleName)
    }

    /** The module of the claimed session when [token] and [peerAddress] both match it, otherwise null. */
    @Synchronized
    fun moduleFor(token: String, peerAddress: String): String? = current?.takeIf { it.claimed && it.token == token && it.phonePeer == peerAddress }?.moduleId

    /** Ends the session of [token]; a phone may only end the session it claimed. */
    @Synchronized
    fun end(token: String, peerAddress: String? = null) {
        val session = current ?: return
        if (session.token == token && (peerAddress == null || session.phonePeer == peerAddress)) current = null
    }
}

/**
 * The phone's [WebUiBackend] for a watch module: each call opens an authenticated socket to [peerAddress] and is
 * answered by the watch. Calls block, as the WebView calls its bridge and file handler off the main
 * thread.
 */
class RemoteWebUiBackend private constructor(
    private val transport: CompanionBridgeRepository,
    private val peerAddress: String,
    private val token: String,
    val moduleId: String,
    val moduleName: String,
) : WebUiBackend {

    private fun <T> call(op: String, arg: String, extra: Int = 0, timeout: Boolean = true, onConnection: ((BridgeConnection) -> Unit)? = null, read: (DataInputStream) -> T): T {
        val connection = transport.connect(peerAddress, WearWebUiProtocol.RPC_PATH)
        try {
            onConnection?.invoke(connection)
            val output = connection.output
            val request = JSONObject().put("token", token).put("op", op).put("arg", arg).put("extra", extra)
            with(WearWebUiProtocol) { output.writeFrame(FRAME_OK, request.toString().toByteArray()) }
            val input = connection.input
            return if (timeout) CompanionBridgeRepository.handshake(connection.socket) { read(input) } else read(input)
        } finally {
            connection.close()
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
        private val lock = Any()
        private var connection: BridgeConnection? = null
        private var closed = false

        override fun start(
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onComplete: (WebUiCommandResult) -> Unit,
        ) {
            thread(name = "remote-webui-spawn") {
                val result = runCatching {
                    call("spawn", command, timeout = false, onConnection = { socket ->
                        synchronized(lock) {
                            check(!closed)
                            connection = socket
                        }
                    }) { input ->
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
                synchronized(lock) { connection = null }
                onComplete(result)
            }
        }

        override fun close() {
            synchronized(lock) {
                closed = true
                connection?.close()
            }
        }
    }

    override fun listModules(): String = runCatching { single("modules", "")?.decodeToString() }.getOrNull() ?: "[]"

    override fun openFile(path: String): InputStream? = runCatching {
        val connection = transport.connect(peerAddress, WearWebUiProtocol.RPC_PATH)
        try {
            val output = connection.output
            val request = JSONObject().put("token", token).put("op", "file").put("arg", path).put("extra", 0)
            with(WearWebUiProtocol) { output.writeFrame(FRAME_OK, request.toString().toByteArray()) }
            val input = connection.input
            val (type, payload) = CompanionBridgeRepository.handshake(connection.socket) {
                with(WearWebUiProtocol) { input.readFrame() }
            }
            when {
                type != WearWebUiProtocol.FRAME_CHUNK -> {
                    connection.close()
                    null
                }

                payload.isEmpty() -> {
                    connection.close()
                    ByteArray(0).inputStream()
                }

                else -> RemoteFileInputStream(connection, input, payload)
            }
        } catch (e: Exception) {
            connection.close()
            throw e
        }
    }.getOrNull()

    override fun listPackages(type: String): String = runCatching { single("packages", type)?.decodeToString() }.getOrNull() ?: "[]"

    override fun getPackagesInfo(packageNamesJson: String): String = runCatching { single("packagesInfo", packageNamesJson)?.decodeToString() }.getOrNull() ?: "[]"

    override fun iconPng(packageName: String, size: Int): ByteArray? = runCatching { single("icon", packageName, size) }.getOrNull()

    /** Tells the watch the page was closed, so it ends the session and reloads its modules. */
    fun close() {
        transport.notifyClosed(peerAddress, token)
    }

    companion object {
        /** Claims only the session offered by the explicitly paired watch, off the main thread. */
        fun claim(transport: CompanionBridgeRepository, peerAddress: String): RemoteWebUiBackend? = runCatching {
            transport.connect(peerAddress, WearWebUiProtocol.CLAIM_PATH).use { connection ->
                val (type, payload) = CompanionBridgeRepository.handshake(connection.socket) {
                    with(WearWebUiProtocol) { connection.input.readFrame() }
                }
                if (type != WearWebUiProtocol.FRAME_OK) return null
                val session = JSONObject(payload.decodeToString())
                val module = session.getString("module")
                require(module.isNotEmpty() && '/' !in module && module != "..") { "Invalid module" }
                RemoteWebUiBackend(transport, peerAddress, session.getString("token"), module, session.optString("name").ifEmpty { module })
            }
        }.onFailure { Log.w("RemoteWebUi", "Session claim failed", it) }.getOrNull()
    }
}

/** Streams a [WearWebUiProtocol.FRAME_CHUNK] sequence, pulling the next chunk on demand. */
private class RemoteFileInputStream(
    private val connection: BridgeConnection,
    private val input: DataInputStream,
    initial: ByteArray,
) : InputStream() {
    private var chunk = initial
    private var offset = 0
    private var closed = false

    override fun read(): Int {
        if (!ensure()) return -1
        return chunk[offset++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (!ensure()) return -1
        val count = minOf(len, chunk.size - offset)
        System.arraycopy(chunk, offset, b, off, count)
        offset += count
        return count
    }

    override fun available(): Int = chunk.size - offset

    private fun ensure(): Boolean {
        while (offset >= chunk.size) {
            if (closed) return false
            val (type, payload) = CompanionBridgeRepository.handshake(connection.socket) {
                with(WearWebUiProtocol) { input.readFrame() }
            }
            if (type != WearWebUiProtocol.FRAME_CHUNK || payload.isEmpty()) {
                close()
                return false
            }
            chunk = payload
            offset = 0
        }
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { connection.close() }
    }
}

/** The watch's answer to a phone claiming its pending session on [connection]. */
internal fun serveWebUiClaim(connection: BridgeConnection) {
    try {
        val output = connection.output
        val session = WearWebUiSession.claim(connection.peer)
        with(WearWebUiProtocol) {
            if (session == null) {
                output.writeFrame(FRAME_ERROR, ByteArray(0))
            } else {
                output.writeFrame(FRAME_OK, session.toString().toByteArray())
            }
        }
    } catch (_: Exception) {
        // The phone closed the socket or the connection dropped; the claim simply fails.
    } finally {
        connection.close()
    }
}

/**
 * The watch's answer to one phone call on [connection], served by [backend]. Only the phone Bluetooth peer that
 * claimed the session, presenting its token, is answered; file reads are limited to the session
 * module's webroot.
 */
internal suspend fun serveWebUiCall(
    connection: BridgeConnection,
    backend: WebUiBackend,
    ensurePackages: suspend () -> Unit,
) {
    try {
        val input = connection.input
        val output = connection.output
        with(WearWebUiProtocol) {
            val request = JSONObject(input.readFrame().second.decodeToString())
            val moduleId = WearWebUiSession.moduleFor(request.optString("token"), connection.peer)
            if (moduleId == null) {
                output.writeFrame(FRAME_ERROR, ByteArray(0))
                return
            }
            val arg = request.optString("arg")
            fun answer(payload: ByteArray?) = if (payload == null) output.writeFrame(FRAME_ERROR, ByteArray(0)) else output.writeFrame(FRAME_OK, payload)
            when (request.optString("op")) {
                "exec" -> answer(backend.execute(arg).toJson().toByteArray())

                "modules" -> answer(backend.listModules().toByteArray())

                "packages" -> {
                    ensurePackages()
                    answer(backend.listPackages(arg).toByteArray())
                }

                "packagesInfo" -> {
                    ensurePackages()
                    answer(backend.getPackagesInfo(arg).toByteArray())
                }

                "icon" -> answer(backend.iconPng(arg, request.optInt("extra", 128)))

                "file" -> {
                    val webRoot = File("/data/adb/modules/$moduleId/webroot")
                    val file = File(arg).normalize()
                    val stream = if (file.path.startsWith(webRoot.path + "/")) backend.openFile(file.path) else null
                    if (stream == null) {
                        output.writeFrame(FRAME_ERROR, ByteArray(0))
                    } else {
                        // Chunked, so a page file never needs one watch-sized frame.
                        stream.use { source ->
                            val buffer = ByteArray(CHUNK_SIZE)
                            while (true) {
                                val read = source.read(buffer)
                                if (read < 0) break
                                output.writeFrame(FRAME_CHUNK, buffer.copyOf(read))
                            }
                        }
                        output.writeFrame(FRAME_CHUNK, ByteArray(0))
                    }
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
        // The phone closed the socket or the connection dropped; the call simply ends.
    } finally {
        connection.close()
    }
}
