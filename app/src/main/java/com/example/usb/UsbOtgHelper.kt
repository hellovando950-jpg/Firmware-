package com.example.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

data class ConnectedDevice(
    val vendorId: Int,
    val productId: Int,
    val deviceName: String,
    val manufacturerName: String?,
    val productName: String?,
    val detectedChipset: String // "MTK", "SPD", "QUALCOMM", "UNKNOWN"
)

object UsbOtgHelper {
    private const val TAG = "UsbOtgHelper"

    // Supported flashing VIDs
    private val MTK_VIDS = listOf(0x0E8D, 0x0403) // MediaTek Inc, FTDI
    private val QUALCOMM_VIDS = listOf(0x05C6) // Qualcomm Inc
    private val SPD_VIDS = listOf(0x17EF, 0x2116, 0x1D4A) // Spreadtrum, Lenovo, etc.

    fun detectConnectedDevices(context: Context): List<ConnectedDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (usbManager == null) {
            Log.e(TAG, "USB Manager not available")
            return emptyList()
        }

        val deviceList: Map<String, UsbDevice> = usbManager.deviceList
        val detected = mutableListOf<ConnectedDevice>()

        for (device in deviceList.values) {
            val vid = device.vendorId
            val pid = device.productId
            val manufacturer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                device.manufacturerName
            } else {
                null
            }
            val product = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                device.productName
            } else {
                null
            }

            val chipset = when {
                MTK_VIDS.contains(vid) -> "MTK"
                QUALCOMM_VIDS.contains(vid) -> "QUALCOMM"
                SPD_VIDS.contains(vid) -> "SPD"
                device.deviceName.contains("mediatek", ignoreCase = true) || 
                        (manufacturer ?: "").contains("mediatek", ignoreCase = true) -> "MTK"
                device.deviceName.contains("qualcomm", ignoreCase = true) || 
                        (manufacturer ?: "").contains("qualcomm", ignoreCase = true) -> "QUALCOMM"
                device.deviceName.contains("spreadtrum", ignoreCase = true) || 
                        device.deviceName.contains("unisoc", ignoreCase = true) ||
                        (manufacturer ?: "").contains("spreadtrum", ignoreCase = true) ||
                        (manufacturer ?: "").contains("unisoc", ignoreCase = true) -> "SPD"
                else -> "UNKNOWN"
            }

            detected.add(
                ConnectedDevice(
                    vendorId = vid,
                    productId = pid,
                    deviceName = device.deviceName,
                    manufacturerName = manufacturer,
                    productName = product,
                    detectedChipset = chipset
                )
            )
        }

        return detected
    }

    fun getChipsetLabel(vid: Int, pid: Int): String {
        return when {
            MTK_VIDS.contains(vid) -> "MediaTek Inc (BROM/Bootrom)"
            QUALCOMM_VIDS.contains(vid) -> "Qualcomm Inc (EDL Mode)"
            SPD_VIDS.contains(vid) -> "Spreadtrum/Unisoc (FDL Bootloader)"
            else -> "Generic USB (VID: 0x${Integer.toHexString(vid).uppercase()}, PID: 0x${Integer.toHexString(pid).uppercase()})"
        }
    }
}
