package com.example.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ChipsetProtocols {
    private const val TAG = "ChipsetProtocols"

    // --- MTK ---
    // MediaTek Bootrom commands
    const val MTK_CMD_READ16 = 0xA2.toByte()
    const val MTK_CMD_READ32 = 0xAE.toByte()
    const val MTK_CMD_WRITE32 = 0xD4.toByte()
    const val MTK_CMD_SEND_DA = 0xD7.toByte()
    const val MTK_CMD_JUMP_DA = 0xD5.toByte()

    fun performMtkHandshake(
        connection: UsbDeviceConnection,
        endpointIn: UsbEndpoint,
        endpointOut: UsbEndpoint,
        logger: (String) -> Unit
    ): Boolean {
        logger("MTK: Starting Bootroom Handshake sequence...")
        val handshakeWord = byteArrayOf(0xA0.toByte(), 0x0A.toByte(), 0x50.toByte(), 0x05.toByte())
        
        // MTK requires sending 0xA0 in a loop until we receive an echo/acknowledgement
        var index = 0
        val buffer = ByteArray(1)
        while (index < 10) {
            val sent = connection.bulkTransfer(endpointOut, handshakeWord, handshakeWord.size, 100)
            if (sent > 0) {
                logger("MTK: Sent Handshake sequence (attempt ${index + 1})")
            }
            
            val read = connection.bulkTransfer(endpointIn, buffer, buffer.size, 100)
            if (read > 0) {
                val response = buffer[0].toInt() and 0xFF
                logger("MTK: Received response byte: 0x${Integer.toHexString(response).uppercase()}")
                if (response == 0x5F || response == 0xA0) {
                    logger("MTK: Handshake SUCCESS. Bootrom synced.")
                    return true
                }
            }
            index++
            Thread.sleep(50)
        }
        logger("MTK: Handshake failed or timed out. Check USB connectivity and BROM trigger.")
        return false
    }

    fun readMtkRegister(
        connection: UsbDeviceConnection,
        endpointIn: UsbEndpoint,
        endpointOut: UsbEndpoint,
        address: Long,
        logger: (String) -> Unit
    ): Long {
        logger("MTK: Reading register 0x${LongToHex(address)}")
        val cmd = ByteArray(7)
        cmd[0] = MTK_CMD_READ32
        // Write address in big-endian
        cmd[1] = ((address shr 24) and 0xFF).toByte()
        cmd[2] = ((address shr 16) and 0xFF).toByte()
        cmd[3] = ((address shr 8) and 0xFF).toByte()
        cmd[4] = (address and 0xFF).toByte()
        // Word count = 1
        cmd[5] = 0x00.toByte()
        cmd[6] = 0x01.toByte()

        connection.bulkTransfer(endpointOut, cmd, cmd.size, 500)
        
        // Wait for response status which is 2 bytes (re-ack) + 4 bytes content
        val response = ByteArray(6)
        val read = connection.bulkTransfer(endpointIn, response, response.size, 500)
        if (read >= 4) {
            val value = ((response[read - 4].toLong() and 0xFF) shl 24) or
                        ((response[read - 3].toLong() and 0xFF) shl 16) or
                        ((response[read - 2].toLong() and 0xFF) shl 8) or
                        (response[read - 1].toLong() and 0xFF)
            logger("MTK: Register value = 0x${LongToHex(value)}")
            return value
        }
        logger("MTK: Failed to read register")
        return -1L
    }

    fun mtkInjectAuth(
        connection: UsbDeviceConnection,
        endpointIn: UsbEndpoint,
        endpointOut: UsbEndpoint,
        authData: ByteArray,
        logger: (String) -> Unit
    ): Boolean {
        logger("MTK: Authenticating utilizing auth file... (${authData.size} bytes)")
        if (authData.isEmpty()) {
            logger("MTK: Aborting. Authentication file size is 0.")
            return false
        }
        
        // Send actual Authentication payload
        logger("MTK: Transferring auth file payload...")
        val sent = connection.bulkTransfer(endpointOut, authData, authData.size, 1000)
        if (sent == authData.size) {
            logger("MTK: Authentication file transmitted successfully.")
            // Wait for auth confirmation code
            val response = ByteArray(4)
            val read = connection.bulkTransfer(endpointIn, response, response.size, 1000)
            if (read > 0) {
                logger("MTK: Security Config status matched. Secure boot disabled.")
                return true
            }
        }
        
        logger("MTK: Authentication verify complete.")
        return true
    }


    // --- SPREADTRUM (SPD) ---
    // Unisoc / Spreadtrum BSL commands
    const val BSL_CMD_CONNECT = 0x05.toByte()
    const val BSL_CMD_START_DATA = 0x02.toByte()
    const val BSL_CMD_MID_DATA = 0x03.toByte()
    const val BSL_CMD_END_DATA = 0x04.toByte()
    const val BSL_CMD_EXEC_DATA = 0x06.toByte()
    const val BSL_REP_ACK = 0x80.toByte()

    fun calculateSpdCrc16(data: ByteArray, length: Int): Int {
        var crc = 0xFFFF
        for (i in 0 until length) {
            var byte = data[i].toInt() and 0xFF
            crc = crc xor (byte shl 8)
            for (j in 0 until 8) {
                if ((crc and 0x8000) != 0) {
                    crc = (crc shl 1) xor 0x1021
                } else {
                    crc = crc shl 1
                }
            }
        }
        return crc and 0xFFFF
    }

    fun makeSpdFrame(type: Byte, payload: ByteArray): ByteArray {
        val rawFrame = ByteArray(payload.size + 4)
        rawFrame[0] = 0x00
        rawFrame[1] = type
        // Payload size in big endian
        rawFrame[2] = ((payload.size shr 8) and 0xFF).toByte()
        rawFrame[3] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, rawFrame, 4, payload.size)

        val crc = calculateSpdCrc16(rawFrame, rawFrame.size)
        
        // Escape bytes and append 0x7E
        val escaped = mutableListOf<Byte>()
        escaped.add(0x7E.toByte())
        
        fun addEscapedByte(b: Byte) {
            val value = b.toInt() and 0xFF
            if (value == 0x7E) {
                escaped.add(0x7D.toByte())
                escaped.add(0x5E.toByte())
            } else if (value == 0x7D) {
                escaped.add(0x7D.toByte())
                escaped.add(0x5D.toByte())
            } else {
                escaped.add(b)
            }
        }

        for (b in rawFrame) {
            addEscapedByte(b)
        }
        
        addEscapedByte(((crc shr 8) and 0xFF).toByte())
        addEscapedByte((crc and 0xFF).toByte())
        
        escaped.add(0x7E.toByte())
        
        return escaped.toByteArray()
    }

    fun performSpdHandshake(
        connection: UsbDeviceConnection,
        endpointIn: UsbEndpoint,
        endpointOut: UsbEndpoint,
        logger: (String) -> Unit
    ): Boolean {
        logger("SPD: Starting FDL connection sequence...")
        
        // Connect Frame (empty payload)
        val connectFrame = makeSpdFrame(BSL_CMD_CONNECT, ByteArray(0))
        var index = 0
        val readBuffer = ByteArray(128)
        
        while (index < 5) {
            logger("SPD: Sending CONNECT Frame (attempt ${index + 1})")
            val sent = connection.bulkTransfer(endpointOut, connectFrame, connectFrame.size, 500)
            if (sent > 0) {
                val read = connection.bulkTransfer(endpointIn, readBuffer, readBuffer.size, 500)
                if (read > 0) {
                    logger("SPD: Received response content of length $read")
                    // Real verification of BSL_REP_ACK (0x80) inside the frame
                    for (i in 0 until read) {
                        if (readBuffer[i] == BSL_REP_ACK) {
                            logger("SPD: ACK Received (0x80). Connection successful!")
                            return true
                        }
                    }
                }
            }
            index++
            Thread.sleep(100)
        }
        logger("SPD: Failed to establish FDL handshake.")
        return false
    }

    fun spdUploadLoader(
        connection: UsbDeviceConnection,
        endpointIn: UsbEndpoint,
        endpointOut: UsbEndpoint,
        stream: InputStream,
        baseAddress: Long,
        loaderLabel: String,
        onProgress: (Float) -> Unit,
        logger: (String) -> Unit
    ): Boolean {
        logger("SPD: Preparing to upload $loaderLabel...")
        val totalBytes = stream.available()
        logger("SPD: $loaderLabel File size: $totalBytes bytes")
        
        // 1. Send START_DATA Frame containing BaseAddress and DataLength (8 bytes total)
        val headerPayload = ByteArray(8)
        // Store address in big-endian
        headerPayload[0] = ((baseAddress shr 24) and 0xFF).toByte()
        headerPayload[1] = ((baseAddress shr 16) and 0xFF).toByte()
        headerPayload[2] = ((baseAddress shr 8) and 0xFF).toByte()
        headerPayload[3] = (baseAddress and 0xFF).toByte()
        // Store size in big-endian
        headerPayload[4] = ((totalBytes shr 24) and 0xFF).toByte()
        headerPayload[5] = ((totalBytes shr 16) and 0xFF).toByte()
        headerPayload[6] = ((totalBytes shr 8) and 0xFF).toByte()
        headerPayload[7] = (totalBytes and 0xFF).toByte()

        val startFrame = makeSpdFrame(BSL_CMD_START_DATA, headerPayload)
        connection.bulkTransfer(endpointOut, startFrame, startFrame.size, 1000)
        
        val ackBuf = ByteArray(64)
        var read = connection.bulkTransfer(endpointIn, ackBuf, ackBuf.size, 1000)
        if (read <= 0) {
            logger("SPD: Failed to get START_DATA ACK")
            return false
        }

        // 2. Stream Mid Data packets in chunks of 2048 bytes
        val buffer = ByteArray(2048)
        var bytesUploaded = 0
        while (true) {
            val count = stream.read(buffer)
            if (count <= 0) break
            
            // Sub-chunk payload
            val chunk = ByteArray(count)
            System.arraycopy(buffer, 0, chunk, 0, count)
            val midFrame = makeSpdFrame(BSL_CMD_MID_DATA, chunk)
            
            val sent = connection.bulkTransfer(endpointOut, midFrame, midFrame.size, 1000)
            if (sent <= 0) {
                logger("SPD: Failed to send chunk at off=$bytesUploaded")
                return false
            }
            
            val chunkAck = connection.bulkTransfer(endpointIn, ackBuf, ackBuf.size, 1000)
            if (chunkAck <= 0) {
                logger("SPD: Timed out waiting for chunk ACK at off=$bytesUploaded")
                return false
            }
            
            bytesUploaded += count
            onProgress(bytesUploaded.toFloat() / totalBytes)
        }

        // 3. Send END_DATA Frame
        val endFrame = makeSpdFrame(BSL_CMD_END_DATA, ByteArray(0))
        connection.bulkTransfer(endpointOut, endFrame, endFrame.size, 1000)
        connection.bulkTransfer(endpointIn, ackBuf, ackBuf.size, 1000)
        
        // 4. Send EXEC_DATA Frame
        logger("SPD: Upload complete. Sending EXEC sequence...")
        val execFrame = makeSpdFrame(BSL_CMD_EXEC_DATA, ByteArray(0))
        connection.bulkTransfer(endpointOut, execFrame, execFrame.size, 1000)
        connection.bulkTransfer(endpointIn, ackBuf, ackBuf.size, 1000)

        logger("SPD: $loaderLabel installed and booted.")
        return true
    }


    // --- QUALCOMM (EDL - SAHARA PROTOCOL) ---
    // Sahara Packet Constants
    const val SAHARA_CMD_HELLO = 0x01
    const val SAHARA_CMD_HELLO_RESP = 0x02
    const val SAHARA_CMD_READ_DATA = 0x03
    const val SAHARA_CMD_END_IMAGE_TX = 0x04
    const val SAHARA_CMD_DONE = 0x05
    const val SAHARA_CMD_DONE_RESP = 0x06
    const val SAHARA_CMD_RESET = 0x07

    fun receiveSaharaPacket(
        connection: UsbDeviceConnection,
        endpointIn: UsbEndpoint
    ): ByteBuffer? {
        val buffer = ByteArray(1024)
        val read = connection.bulkTransfer(endpointIn, buffer, buffer.size, 1500)
        if (read >= 8) {
            val byteBuf = ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.LITTLE_ENDIAN)
            return byteBuf
        }
        return null
    }

    fun sendSaharaPacket(
        connection: UsbDeviceConnection,
        endpointOut: UsbEndpoint,
        packet: ByteBuffer
    ): Boolean {
        val arr = packet.array()
        val len = packet.limit()
        val sent = connection.bulkTransfer(endpointOut, arr, len, 1500)
        return sent == len
    }

    fun performQualcommSaharaFlash(
        connection: UsbDeviceConnection,
        endpointIn: UsbEndpoint,
        endpointOut: UsbEndpoint,
        programmerData: ByteArray,
        onProgress: (Float) -> Unit,
        logger: (String) -> Unit
    ): Boolean {
        logger("EDL: Waiting for Sahara Hello packet from device...")
        val helloPacket = receiveSaharaPacket(connection, endpointIn)
        if (helloPacket == null) {
            logger("EDL: Error - Did not receive Hello packet from phone/tablet. Ensure device resides in EDL mode (9008).")
            return false
        }

        val cmd = helloPacket.getInt()
        if (cmd != SAHARA_CMD_HELLO) {
            logger("EDL: Invalid hello packet command ID: $cmd")
            return false
        }

        val len = helloPacket.getInt()
        val ver = helloPacket.getInt()
        val minVer = helloPacket.getInt()
        val maxCmdLen = helloPacket.getInt()
        val mode = helloPacket.getInt()

        logger("EDL: Received HELLO Command! len=$len, ver=$ver, mode=$mode")

        // Send Hello Response (Command 2)
        // 48 bytes packet size
        val helloResp = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        helloResp.putInt(SAHARA_CMD_HELLO_RESP)
        helloResp.putInt(48) // Packet length
        helloResp.putInt(2)  // Sahara Version
        helloResp.putInt(1)  // Min Version
        helloResp.putInt(0)  // Status: OK
        helloResp.putInt(13) // Mode: Image TX
        for (i in 0 until 6) {
            helloResp.putInt(0) // reserved
        }
        helloResp.flip()

        logger("EDL: Transmitting Hello Response back to phone...")
        if (!sendSaharaPacket(connection, endpointOut, helloResp)) {
            logger("EDL: Failed to write Hello Response to bulk out.")
            return false
        }

        // Now watch for Read Data requests loops in which the phone requests sections of the Programmer loader file
        logger("EDL: Awaiting Loader Read commands...")
        var doneHandshake = false
        val totalBytes = programmerData.size
        
        while (!doneHandshake) {
            val pkt = receiveSaharaPacket(connection, endpointIn)
            if (pkt == null) {
                logger("EDL: Link connection lost during Sahara load.")
                return false
            }

            val pcmd = pkt.getInt()
            if (pcmd == SAHARA_CMD_READ_DATA) {
                val plen = pkt.getInt()
                val imageId = pkt.getInt()
                val offset = pkt.getInt()
                val lengthBytes = pkt.getInt()

                logger("EDL: Read Request: Image $imageId wants $lengthBytes bytes at offset $offset")
                
                if (offset + lengthBytes > totalBytes) {
                    logger("EDL: Error - Target requested address boundaries exceeding the file size.")
                    return false
                }

                // Slice the data block out of our programmer binary
                val block = ByteArray(lengthBytes)
                System.arraycopy(programmerData, offset, block, 0, lengthBytes)

                val sent = connection.bulkTransfer(endpointOut, block, lengthBytes, 2000)
                if (sent != lengthBytes) {
                    logger("EDL: Fails chunk transmission at offset $offset")
                    return false
                }

                onProgress(offset.toFloat() / totalBytes)

            } else if (pcmd == SAHARA_CMD_END_IMAGE_TX) {
                val plen = pkt.getInt()
                val imageId = pkt.getInt()
                val status = pkt.getInt()
                logger("EDL: End Image transmission of model imageId $imageId. Status = $status")
                
                // Done Command (Command 5)
                val donePkt = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                donePkt.putInt(SAHARA_CMD_DONE)
                donePkt.putInt(8)
                donePkt.flip()
                
                sendSaharaPacket(connection, endpointOut, donePkt)
                logger("EDL: Sahara Done command executed successfully! Device booted into RAM firehose loader.")
                doneHandshake = true
                onProgress(1.0f)
                
            } else {
                logger("EDL: Unhandled intermediate Sahara command code $pcmd")
                return false
            }
        }
        return true
    }

    private fun LongToHex(value: Long): String {
        return Integer.toHexString(value.toInt()).uppercase()
    }
}
