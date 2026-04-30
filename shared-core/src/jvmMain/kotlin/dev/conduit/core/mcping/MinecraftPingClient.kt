package dev.conduit.core.mcping

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

data class PingResult(
    val onlinePlayers: Int,
    val maxPlayers: Int,
    val sample: List<String>,
    val versionName: String?,
    val protocolVersion: Int?,
    val latencyMs: Long,
)

class MinecraftPingClient(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    suspend fun ping(host: String, port: Int, timeoutMs: Int = 5_000): PingResult? =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    socket.soTimeout = timeoutMs
                    val out = DataOutputStream(socket.getOutputStream())
                    val input = DataInputStream(socket.getInputStream())

                    writeHandshake(out, host, port)
                    writeStatusRequest(out)

                    readStatusResponse(input)
                }
            }.getOrNull()?.let { responseJson ->
                parsePingResponse(responseJson, latencyMs = System.currentTimeMillis() - startedAt)
            }
        }

    private fun writeHandshake(out: OutputStream, host: String, port: Int) {
        val payload = ByteArrayOutputStream()
        val payloadOut = DataOutputStream(payload)
        writeVarInt(payloadOut, 0x00) // Packet ID
        writeVarInt(payloadOut, -1)   // Protocol version (-1 = unknown, servers tolerate)
        writeString(payloadOut, host)
        payloadOut.writeShort(port)   // Big-endian unsigned short
        writeVarInt(payloadOut, 1)    // Next state: 1 = status
        writePacket(out, payload.toByteArray())
    }

    private fun writeStatusRequest(out: OutputStream) {
        val payload = ByteArrayOutputStream()
        writeVarInt(DataOutputStream(payload), 0x00) // Empty body, packet id only
        writePacket(out, payload.toByteArray())
    }

    private fun readStatusResponse(input: DataInputStream): String {
        readVarInt(input) // Total packet length — not needed once we're length-delimited below
        val packetId = readVarInt(input)
        if (packetId != 0x00) throw IOException("Unexpected packet id $packetId in status response")
        val jsonLength = readVarInt(input)
        val bytes = ByteArray(jsonLength)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun parsePingResponse(jsonText: String, latencyMs: Long): PingResult? = runCatching {
        val parsed = json.decodeFromString<PingResponseDto>(jsonText)
        PingResult(
            onlinePlayers = parsed.players?.online ?: 0,
            maxPlayers = parsed.players?.max ?: 0,
            sample = parsed.players?.sample?.map { it.name } ?: emptyList(),
            versionName = parsed.version?.name,
            protocolVersion = parsed.version?.protocol,
            latencyMs = latencyMs,
        )
    }.getOrNull()

    private fun writePacket(out: OutputStream, payload: ByteArray) {
        val wrapper = ByteArrayOutputStream()
        writeVarInt(DataOutputStream(wrapper), payload.size)
        wrapper.write(payload)
        out.write(wrapper.toByteArray())
        out.flush()
    }

    internal fun writeVarInt(out: DataOutputStream, value: Int) {
        var v = value
        while (true) {
            if ((v and 0x7F.inv()) == 0) {
                out.writeByte(v)
                return
            }
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    internal fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }

    internal fun readVarInt(input: InputStream): Int {
        var value = 0
        var position = 0
        while (true) {
            val byte = input.read()
            if (byte == -1) throw IOException("Unexpected EOF reading varint")
            value = value or ((byte and 0x7F) shl position)
            if ((byte and 0x80) == 0) return value
            position += 7
            if (position >= 32) throw IOException("Varint too long")
        }
    }
}

@Serializable
private data class PingResponseDto(
    val version: Version? = null,
    val players: Players? = null,
) {
    @Serializable
    data class Version(val name: String? = null, val protocol: Int? = null)

    @Serializable
    data class Players(
        val max: Int = 0,
        val online: Int = 0,
        val sample: List<SamplePlayer>? = null,
    )

    @Serializable
    data class SamplePlayer(val name: String = "", val id: String = "")
}
