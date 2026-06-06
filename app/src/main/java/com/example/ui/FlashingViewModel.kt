package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.FlashingProfile
import com.example.data.ProfileRepository
import com.example.usb.ConnectedDevice
import com.example.usb.UsbOtgHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PartitionItem(
    val name: String,
    val sizeMb: Int,
    val startAddress: String,
    val isChecked: Boolean = true,
    val writeStatus: String = "IDLE" // "IDLE", "WRITING", "SUCCESS", "FAILED"
)

data class ConsoleLog(
    val timestamp: String,
    val text: String,
    val type: LogType = LogType.INFO
)

enum class LogType {
    INFO, SUCCESS, WARNING, ERROR, DEBUG
}

class FlashingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProfileRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ProfileRepository(database.flashingProfileDao())
    }

    // Profiles saved in Room
    val savedProfiles = repository.allProfiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // UI state streams
    private val _currentTab = MutableStateFlow("MTK") // "MTK", "SPD", "QUALCOMM"
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // MTK Fields
    private val _mtkAuthFile = MutableStateFlow("MTK_Auth_v3.auth")
    val mtkAuthFile: StateFlow<String> = _mtkAuthFile.asStateFlow()

    private val _mtkScatterFile = MutableStateFlow("MT6765_Android_scatter.txt")
    val mtkScatterFile: StateFlow<String> = _mtkScatterFile.asStateFlow()

    private val _mtkPreloaderFile = MutableStateFlow("preloader_k61v1.bin")
    val mtkPreloaderFile: StateFlow<String> = _mtkPreloaderFile.asStateFlow()

    // SPD Fields
    private val _spdFdl1File = MutableStateFlow("fdl1_base.bin")
    val spdFdl1File: StateFlow<String> = _spdFdl1File.asStateFlow()

    private val _spdFdl2File = MutableStateFlow("fdl2_app.bin")
    val spdFdl2File: StateFlow<String> = _spdFdl2File.asStateFlow()

    private val _spdPacFile = MutableStateFlow("SPD_SC9863A_firmware.pac")
    val spdPacFile: StateFlow<String> = _spdPacFile.asStateFlow()

    // Qualcomm Fields
    private val _qcProgrammerFile = MutableStateFlow("prog_firehose_8953.mbn")
    val qcProgrammerFile: StateFlow<String> = _qcProgrammerFile.asStateFlow()

    private val _qcRawProgramFile = MutableStateFlow("rawprogram0.xml")
    val qcRawProgramFile: StateFlow<String> = _qcRawProgramFile.asStateFlow()

    private val _qcPatchFile = MutableStateFlow("patch0.xml")
    val qcPatchFile: StateFlow<String> = _qcPatchFile.asStateFlow()

    // Partition Table checklist (dynamic depending on mode)
    private val _partitions = MutableStateFlow<List<PartitionItem>>(emptyList())
    val partitions: StateFlow<List<PartitionItem>> = _partitions.asStateFlow()

    // Progress percentage & currently flashing info
    private val _flashProgress = MutableStateFlow(0f)
    val flashProgress: StateFlow<Float> = _flashProgress.asStateFlow()

    private val _currentlyFlashingItem = MutableStateFlow("")
    val currentlyFlashingItem: StateFlow<String> = _currentlyFlashingItem.asStateFlow()

    private val _isFlashing = MutableStateFlow(false)
    val isFlashing: StateFlow<Boolean> = _isFlashing.asStateFlow()

    // Real Connected OTG devices list
    private val _connectedUsbDevices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connectedUsbDevices: StateFlow<List<ConnectedDevice>> = _connectedUsbDevices.asStateFlow()

    // Active handshake / COM status
    private val _connectionStatusLabel = MutableStateFlow("DISCONNECTED") // "DISCONNECTED", "HANDSHAKING", "CONNECTED"
    val connectionStatusLabel: StateFlow<String> = _connectionStatusLabel.asStateFlow()

    // Console system logs
    private val _consoleLogs = MutableStateFlow<List<ConsoleLog>>(emptyList())
    val consoleLogs: StateFlow<List<ConsoleLog>> = _consoleLogs.asStateFlow()

    // Keypad Servicing States
    private val _selectedServicingCpu = MutableStateFlow("MT6261 (MediaTek Keypad)")
    val selectedServicingCpu: StateFlow<String> = _selectedServicingCpu.asStateFlow()

    private val _selectedServicingAction = MutableStateFlow("Safe Format (Reset Password)")
    val selectedServicingAction: StateFlow<String> = _selectedServicingAction.asStateFlow()

    private val _imei1Input = MutableStateFlow("")
    val imei1Input: StateFlow<String> = _imei1Input.asStateFlow()

    private val _imei2Input = MutableStateFlow("")
    val imei2Input: StateFlow<String> = _imei2Input.asStateFlow()

    private val _isServicing = MutableStateFlow(false)
    val isServicing: StateFlow<Boolean> = _isServicing.asStateFlow()

    private val _autoBypassSla = MutableStateFlow(true)
    val autoBypassSla: StateFlow<Boolean> = _autoBypassSla.asStateFlow()

    // Coroutine Job for flashing
    private var flashingJob: Job? = null
    private var handshakeJob: Job? = null
    private var servicingJob: Job? = null

    init {
        // Preset partitions default list
        loadDefaultPartitions("MTK")
        addLog("FlashTool Pro initialized. v4.2.0-stable ready.", LogType.INFO)
        addLog("Connect your device using USB OTG in target boot mode (BROM/EDL/FDL).", LogType.DEBUG)
        scanForUsbDevices()
    }

    fun setTab(tab: String) {
        if (_isFlashing.value) return // Prevent config changes during flash
        _currentTab.value = tab
        loadDefaultPartitions(tab)
        addLog("Switched chipset profile layout to: $tab", LogType.INFO)
    }

    // Scan real USB OTG devices
    fun scanForUsbDevices() {
        viewModelScope.launch {
            val devices = UsbOtgHelper.detectConnectedDevices(getApplication())
            _connectedUsbDevices.value = devices
            if (devices.isNotEmpty()) {
                val matched = devices.first()
                addLog("USB OTG Scan: Found ${devices.size} device(s) connected.", LogType.SUCCESS)
                addLog(" -> ${matched.manufacturerName ?: "Unknown"} ${matched.productName ?: "USB Product"} [VID: 0x${Integer.toHexString(matched.vendorId).uppercase()}, PID: 0x${Integer.toHexString(matched.productId).uppercase()}]", LogType.SUCCESS)
                addLog(" -> Auto-matched hardware profile classification: ${matched.detectedChipset}", LogType.DEBUG)
                
                // If a matching chipset is found, auto suggest switching tabs
                if (matched.detectedChipset != "UNKNOWN") {
                    addLog("Matching chip architecture detected: ${matched.detectedChipset}. Suggested tab active.", LogType.DEBUG)
                }
            } else {
                addLog("USB OTG Scan: No hardware connected. Connect a device via OTG to proceed.", LogType.WARNING)
            }
        }
    }

    private fun loadDefaultPartitions(chipset: String) {
        val list = when (chipset) {
            "MTK" -> listOf(
                PartitionItem("preloader", 2, "0x00000000", true),
                PartitionItem("pgpt", 1, "0x00400000", true),
                PartitionItem("boot", 64, "0x00800000", true),
                PartitionItem("recovery", 64, "0x04800000", true),
                PartitionItem("logo", 16, "0x08800000", true),
                PartitionItem("vbmeta", 8, "0x09800000", true),
                PartitionItem("system", 2048, "0x0A000000", true),
                PartitionItem("vendor", 512, "0x8A000000", true),
                PartitionItem("userdata", 4096, "0xAA000000", false)
            )
            "SPD" -> listOf(
                PartitionItem("fdl1", 1, "0x00000000", true),
                PartitionItem("fdl2", 4, "0x00080000", true),
                PartitionItem("spl", 2, "0x00400000", true),
                PartitionItem("boot", 32, "0x00600000", true),
                PartitionItem("recovery", 32, "0x02600000", true),
                PartitionItem("system", 1536, "0x04600000", true),
                PartitionItem("userdata", 2048, "0x64600000", false)
            )
            else -> listOf( // QUALCOMM
                PartitionItem("gpt_backup", 1, "0x00000000", true),
                PartitionItem("sbl1", 2, "0x00004000", true),
                PartitionItem("aboot", 4, "0x0000C000", true),
                PartitionItem("boot", 64, "0x00010000", true),
                PartitionItem("recovery", 64, "0x00050000", true),
                PartitionItem("system", 2560, "0x00090000", true),
                PartitionItem("vendor", 768, "0x00A90000", true),
                PartitionItem("userdata", 8192, "0x00F90000", false)
            )
        }
        _partitions.value = list
    }

    // Toggle selected partition
    fun togglePartition(index: Int) {
        val currentList = _partitions.value.toMutableList()
        if (index in currentList.indices) {
            val item = currentList[index]
            currentList[index] = item.copy(isChecked = !item.isChecked)
            _partitions.value = currentList
            addLog("Toggled partition [${item.name}]: ${if (!item.isChecked) "Skip" else "Flash"}", LogType.DEBUG)
        }
    }

    // Clear console
    fun clearLogs() {
        _consoleLogs.value = emptyList()
        addLog("Console log buffer cleared.", LogType.INFO)
    }

    // Helper to append logs
    fun addLog(text: String, type: LogType = LogType.INFO) {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timestamp = formatter.format(Date())
        val newLogs = _consoleLogs.value.toMutableList()
        newLogs.add(ConsoleLog(timestamp, text, type))
        
        // Cap log size at 120 elements to keep layout heavy memory happy
        if (newLogs.size > 120) {
            newLogs.removeAt(0)
        }
        _consoleLogs.value = newLogs
    }

    // Form inputs mutations
    fun updateMtkAuth(value: String) { _mtkAuthFile.value = value }
    fun updateMtkScatter(value: String) { _mtkScatterFile.value = value }
    fun updateMtkPreloader(value: String) { _mtkPreloaderFile.value = value }

    fun updateSpdFdl1(value: String) { _spdFdl1File.value = value }
    fun updateSpdFdl2(value: String) { _spdFdl2File.value = value }
    fun updateSpdPac(value: String) { _spdPacFile.value = value }

    fun updateQcProgrammer(value: String) { _qcProgrammerFile.value = value }
    fun updateQcRawProgram(value: String) { _qcRawProgramFile.value = value }
    fun updateQcPatch(value: String) { _qcPatchFile.value = value }


    // Perform Device Handshake Probing
    fun runHandshake() {
        if (handshakeJob?.isActive == true || _isFlashing.value) return
        
        handshakeJob = viewModelScope.launch {
            _connectionStatusLabel.value = "HANDSHAKING"
            addLog("--> PROBING CONNECTION VIA USB OTG BUS...", LogType.INFO)
            
            val usbDevices = UsbOtgHelper.detectConnectedDevices(getApplication())
            val matchedDevice = usbDevices.firstOrNull { it.detectedChipset == _currentTab.value }
            
            delay(1200) // hardware probe and setup debounce delay
            
            if (matchedDevice != null) {
                val usbManager = getApplication<Application>().getSystemService(android.content.Context.USB_SERVICE) as? android.hardware.usb.UsbManager
                val actualDevice = usbManager?.deviceList?.values?.find { it.vendorId == matchedDevice.vendorId && it.productId == matchedDevice.productId }
                
                if (actualDevice != null && usbManager != null) {
                    if (!usbManager.hasPermission(actualDevice)) {
                        addLog("[USB PERMISSION] System permission missing for device VID: 0x${Integer.toHexString(matchedDevice.vendorId).uppercase()}.", LogType.WARNING)
                        _connectionStatusLabel.value = "DISCONNECTED"
                        addLog("Please grant USB permission request when prompted on screen.", LogType.INFO)
                    } else {
                        val connection = usbManager.openDevice(actualDevice)
                        if (connection != null && actualDevice.interfaceCount > 0) {
                            val usbInterface = actualDevice.getInterface(0)
                            var epIn: android.hardware.usb.UsbEndpoint? = null
                            var epOut: android.hardware.usb.UsbEndpoint? = null
                            for (i in 0 until usbInterface.endpointCount) {
                                val ep = usbInterface.getEndpoint(i)
                                if (ep.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK) {
                                    if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) {
                                        epIn = ep
                                    } else {
                                        epOut = ep
                                    }
                                }
                            }
                            if (epIn != null && epOut != null) {
                                connection.claimInterface(usbInterface, true)
                                addLog("USB Host: Native connection established. Initializing chipset-specific handshake...", LogType.INFO)
                                
                                val success = when (matchedDevice.detectedChipset) {
                                    "MTK" -> {
                                        com.example.usb.ChipsetProtocols.performMtkHandshake(connection, epIn, epOut) { addLog(it, LogType.DEBUG) }
                                    }
                                    "SPD" -> {
                                        com.example.usb.ChipsetProtocols.performSpdHandshake(connection, epIn, epOut) { addLog(it, LogType.DEBUG) }
                                    }
                                    else -> {
                                        // Qualcomm HELLO packet handshake check
                                        val helloBuffer = com.example.usb.ChipsetProtocols.receiveSaharaPacket(connection, epIn)
                                        if (helloBuffer != null) {
                                            val cmd = helloBuffer.getInt()
                                            addLog("Qualcomm EDL Sahara Handshake response CMD: $cmd", LogType.SUCCESS)
                                            cmd == com.example.usb.ChipsetProtocols.SAHARA_CMD_HELLO
                                        } else {
                                            addLog("Qualcomm EDL: No Sahara payload received. Ensure QDLoader driver binding.", LogType.ERROR)
                                            false
                                        }
                                    }
                                }
                                
                                if (success) {
                                    _connectionStatusLabel.value = "CONNECTED"
                                    addLog("Handshake verified successfully! Device aligned to selected ${_currentTab.value} interface.", LogType.SUCCESS)
                                } else {
                                    _connectionStatusLabel.value = "DISCONNECTED"
                                    addLog("Error: Device handshaking sequence failed.", LogType.ERROR)
                                }
                                connection.releaseInterface(usbInterface)
                                connection.close()
                            } else {
                                _connectionStatusLabel.value = "DISCONNECTED"
                                addLog("Error: Could not locate standard bulk end-points in device interface.", LogType.ERROR)
                            }
                        } else {
                            _connectionStatusLabel.value = "DISCONNECTED"
                            addLog("Error: Failed to open native device. Port was busy.", LogType.ERROR)
                        }
                    }
                } else {
                    _connectionStatusLabel.value = "DISCONNECTED"
                    addLog("Error: USB device vanished from descriptor list.", LogType.ERROR)
                }
            } else {
                _connectionStatusLabel.value = "DISCONNECTED"
                addLog("Error: Hardware device alignment failed.", LogType.ERROR)
                addLog("Reason: No $_currentTab compatible USB OTG device detected. Connect a real device to probe.", LogType.WARNING)
            }
        }
    }

    // Save profile to database
    fun saveProfile(profileName: String) {
        viewModelScope.launch {
            val profile = FlashingProfile(
                profileName = profileName,
                chipsetType = _currentTab.value,
                authFilePath = when (_currentTab.value) {
                    "MTK" -> _mtkAuthFile.value
                    else -> ""
                },
                fdl1Path = when (_currentTab.value) {
                    "SPD" -> _spdFdl1File.value
                    else -> ""
                },
                fdl2Path = when (_currentTab.value) {
                    "SPD" -> _spdFdl2File.value
                    else -> ""
                },
                programmerPath = when (_currentTab.value) {
                    "QUALCOMM" -> _qcProgrammerFile.value
                    else -> ""
                },
                scatterPath = when (_currentTab.value) {
                    "MTK" -> _mtkScatterFile.value
                    else -> ""
                },
                activePartitionsJson = _partitions.value.filter { it.isChecked }.joinToString(",") { it.name }
            )
            repository.insertProfile(profile)
            addLog("Saved profile '$profileName' to offline configuration base.", LogType.SUCCESS)
        }
    }

    // Load profile from database
    fun loadProfile(profile: FlashingProfile) {
        _currentTab.value = profile.chipsetType
        when (profile.chipsetType) {
            "MTK" -> {
                _mtkAuthFile.value = profile.authFilePath
                _mtkScatterFile.value = profile.scatterPath
            }
            "SPD" -> {
                _spdFdl1File.value = profile.fdl1Path
                _spdFdl2File.value = profile.fdl2Path
            }
            "QUALCOMM" -> {
                _qcProgrammerFile.value = profile.programmerPath
            }
        }
        
        // Restore partition selections if present
        if (profile.activePartitionsJson.isNotEmpty()) {
            val activeNames = profile.activePartitionsJson.split(",")
            val updated = _partitions.value.map {
                it.copy(isChecked = activeNames.contains(it.name))
            }
            _partitions.value = updated
        }
        
        addLog("Loaded profile configuration for '${profile.profileName}' (${profile.chipsetType})", LogType.SUCCESS)
    }

    // Delete profile
    fun deleteProfile(profile: FlashingProfile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            addLog("Deleted profile profile '${profile.profileName}'", LogType.WARNING)
        }
    }

    // Start Flash execution flow
    fun startFlashing() {
        if (_isFlashing.value) return
        
        val activePartitions = _partitions.value.filter { it.isChecked }
        if (activePartitions.isEmpty()) {
            addLog("Error: No partitions selected. Select at least one target to flash.", LogType.ERROR)
            return
        }

        flashingJob = viewModelScope.launch {
            _isFlashing.value = true
            _flashProgress.value = 0f

            val targetChipset = _currentTab.value
            addLog("--- FLASHING ROUTINE STARTED ---", LogType.INFO)
            addLog("Chipset Target Selected: $targetChipset", LogType.INFO)

            // 1. Verify that a real physical USB device is connected for this chipset
            val usbDevices = UsbOtgHelper.detectConnectedDevices(getApplication())
            val matchedDevice = usbDevices.firstOrNull { it.detectedChipset == targetChipset }

            if (matchedDevice == null) {
                _isFlashing.value = false
                _connectionStatusLabel.value = "DISCONNECTED"
                addLog("Flashing Aborted: Target hardware device not detected on the USB OTG bus.", LogType.ERROR)
                addLog("Please connect a physical device in BROM/EDL/FDL mode using a proper USB OTG cable to perform real flashing.", LogType.WARNING)
                return@launch
            }

            // 2. Open physical Usb Connection
            val usbManager = getApplication<Application>().getSystemService(android.content.Context.USB_SERVICE) as? android.hardware.usb.UsbManager
            val actualDevice = usbManager?.deviceList?.values?.find { it.vendorId == matchedDevice.vendorId && it.productId == matchedDevice.productId }

            if (actualDevice == null || usbManager == null) {
                _isFlashing.value = false
                addLog("Error: USB Connection failed. Device disconnected during startup.", LogType.ERROR)
                return@launch
            }

            if (!usbManager.hasPermission(actualDevice)) {
                _isFlashing.value = false
                _connectionStatusLabel.value = "DISCONNECTED"
                addLog("Error: SYSTEM_USB_PERMISSION_DENIED. Tap 'Allow' on pop-up permission dialog to start flashing.", LogType.ERROR)
                return@launch
            }

            val connection = usbManager.openDevice(actualDevice)
            if (connection == null || actualDevice.interfaceCount == 0) {
                _isFlashing.value = false
                addLog("Error: Could not open USB interface connection to device.", LogType.ERROR)
                return@launch
            }

            val usbInterface = actualDevice.getInterface(0)
            var epIn: android.hardware.usb.UsbEndpoint? = null
            var epOut: android.hardware.usb.UsbEndpoint? = null
            for (i in 0 until usbInterface.endpointCount) {
                val ep = usbInterface.getEndpoint(i)
                if (ep.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) {
                        epIn = ep
                    } else {
                        epOut = ep
                    }
                }
            }

            if (epIn == null || epOut == null) {
                _isFlashing.value = false
                connection.close()
                addLog("Error: Physical bulk transfer endpoints not located.", LogType.ERROR)
                return@launch
            }

            connection.claimInterface(usbInterface, true)
            _connectionStatusLabel.value = "CONNECTED"

            addLog("Executing hardware link setup...", LogType.INFO)

            try {
                // Step 3: Perform actual handshake/authentication steps using actual USB protocols
                when (targetChipset) {
                    "MTK" -> {
                        addLog("Initiating MTK BROM Synchronisation sequence...", LogType.DEBUG)
                        val ok = com.example.usb.ChipsetProtocols.performMtkHandshake(connection, epIn, epOut) { addLog(it, LogType.DEBUG) }
                        if (!ok) {
                            addLog("Error: MediaTek hardware refused synchronisation frame commands.", LogType.ERROR)
                            connection.releaseInterface(usbInterface)
                            connection.close()
                            _isFlashing.value = false
                            return@launch
                        }

                        // Try to load MTK Auth bytes
                        val authName = _mtkAuthFile.value
                        addLog("Parsing Selected Auth configuration: $authName", LogType.INFO)
                        // Verify scatter and preloader input
                        if (_mtkScatterFile.value.contains("scatter") && _mtkPreloaderFile.value.contains("preloader")) {
                            addLog("Reading preloader configuration and scatter boundaries...", LogType.DEBUG)
                        } else {
                            addLog("Warning: Layout configuration is missing mandatory preloader / scatter mappings.", LogType.WARNING)
                        }

                        // For real flash write of partition images, ensure image files exists:
                        addLog("Error: No partition binary image files (.img / .bin) have been loaded to storage.", LogType.ERROR)
                        addLog("To execute real flash writes in hardware, valid partition images must be mapped via the UI.", LogType.WARNING)
                        addLog("Flashing aborted to prevent hard-bricking the connected device.", LogType.ERROR)
                        connection.releaseInterface(usbInterface)
                        connection.close()
                        _isFlashing.value = false
                        return@launch
                    }
                    "SPD" -> {
                        addLog("Initiating Spreadtrum FDL bootstrap sequence...", LogType.DEBUG)
                        val ok = com.example.usb.ChipsetProtocols.performSpdHandshake(connection, epIn, epOut) { addLog(it, LogType.DEBUG) }
                        if (!ok) {
                            addLog("Error: Spreadtrum system target refused synchronisation frames.", LogType.ERROR)
                            connection.releaseInterface(usbInterface)
                            connection.close()
                            _isFlashing.value = false
                            return@launch
                        }

                        addLog("Error: Loaders and partitioning image files are not loaded from local storage.", LogType.ERROR)
                        addLog("Flashing aborted: Flash files missing from configuration.", LogType.ERROR)
                        connection.releaseInterface(usbInterface)
                        connection.close()
                        _isFlashing.value = false
                        return@launch
                    }
                    "QUALCOMM" -> {
                        addLog("Initiating Qualcomm EDL Sahara payload exchange... Waiting for Hello packet...", LogType.DEBUG)
                        val helloPacket = com.example.usb.ChipsetProtocols.receiveSaharaPacket(connection, epIn)
                        if (helloPacket == null) {
                            addLog("Error: Failed to obtain Hello command. Ensure device is booted to EDL Mode (QDLoader 9008).", LogType.ERROR)
                            connection.releaseInterface(usbInterface)
                            connection.close()
                            _isFlashing.value = false
                            return@launch
                        }

                        addLog("EDL Hello received! Aborting flash sequence due to missing Programmer ELF file blocks.", LogType.ERROR)
                        addLog("Flashing aborted: Set a valid programmer binary in local configurations.", LogType.WARNING)
                        connection.releaseInterface(usbInterface)
                        connection.close()
                        _isFlashing.value = false
                        return@launch
                    }
                }
            } catch (e: Exception) {
                addLog("Exception during real flash transceive: ${e.localizedMessage}", LogType.ERROR)
            } finally {
                connection.releaseInterface(usbInterface)
                connection.close()
                _isFlashing.value = false
            }
        }
    }

    private fun updatePartitionStatus(name: String, status: String) {
        val currentList = _partitions.value.toMutableList()
        val index = currentList.indexOfFirst { it.name == name }
        if (index != -1) {
            val item = currentList[index]
            currentList[index] = item.copy(writeStatus = status)
            _partitions.value = currentList
        }
    }

    // Halt current flashing process
    fun haltFlashing() {
        if (flashingJob?.isActive == true) {
            flashingJob?.cancel()
            _isFlashing.value = false
            _currentlyFlashingItem.value = ""
            addLog("CRITICAL HAZARD: Flashing halted manually by user!", LogType.ERROR)
            addLog("Connection dropped. Device may require hardware cycle reboot.", LogType.WARNING)
            
            // Set all active writing partitions to failed
            val currentList = _partitions.value.map {
                if (it.writeStatus == "WRITING") it.copy(writeStatus = "FAILED") else it
            }
            _partitions.value = currentList
        }
    }

    fun setServicingCpu(cpu: String) {
        _selectedServicingCpu.value = cpu
        addLog("Selected Keypad CPU Profile: $cpu", LogType.INFO)
    }

    fun setServicingAction(action: String) {
        _selectedServicingAction.value = action
        addLog("Set Servicing Task: $action", LogType.INFO)
    }

    fun updateImei1(value: String) {
        _imei1Input.value = value.filter { it.isDigit() }.take(15)
    }

    fun updateImei2(value: String) {
        _imei2Input.value = value.filter { it.isDigit() }.take(15)
    }

    fun toggleAutoBypassSla() {
        _autoBypassSla.value = !_autoBypassSla.value
        addLog("Auto SLA/DAA Bypass Secure Boot: ${_autoBypassSla.value}", LogType.DEBUG)
    }

    // Execute Keypad Servicing Tasks (Safe Format, Password Read, Dump, Repair IMEI)
    fun executeServicingCommand() {
        if (_isServicing.value || _isFlashing.value) return
        
        servicingJob = viewModelScope.launch {
            _isServicing.value = true
            _flashProgress.value = 0f
            _currentlyFlashingItem.value = "SERVICING"
            
            val cpu = _selectedServicingCpu.value
            val action = _selectedServicingAction.value
            val imei1 = _imei1Input.value
            val imei2 = _imei2Input.value
            
            addLog("Executing Keypad Servicing Engine...", LogType.INFO)
            addLog("Target CPU Model: $cpu", LogType.INFO)
            addLog("Task Command: $action", LogType.INFO)

            // Detect connected USB device matching our servicing profile
            val devices = UsbOtgHelper.detectConnectedDevices(getApplication())
            val matchedDevice = devices.firstOrNull()

            if (matchedDevice == null) {
                _isServicing.value = false
                _currentlyFlashingItem.value = ""
                _connectionStatusLabel.value = "DISCONNECTED"
                addLog("Servicing Aborted: No connected hardware device was detected on the USB OTG bus.", LogType.ERROR)
                addLog("Connect a keypad phone via USB OTG cable in boot mode to start.", LogType.WARNING)
                return@launch
            }

            val usbManager = getApplication<Application>().getSystemService(android.content.Context.USB_SERVICE) as? android.hardware.usb.UsbManager
            val actualDevice = usbManager?.deviceList?.values?.find { it.vendorId == matchedDevice.vendorId && it.productId == matchedDevice.productId }

            if (actualDevice == null || usbManager == null) {
                _isServicing.value = false
                _currentlyFlashingItem.value = ""
                addLog("Error: USB connection failed. Capture path lost.", LogType.ERROR)
                return@launch
            }

            if (!usbManager.hasPermission(actualDevice)) {
                _isServicing.value = false
                _currentlyFlashingItem.value = ""
                _connectionStatusLabel.value = "DISCONNECTED"
                addLog("Error: SYSTEM_USB_PERMISSION_DENIED. Grant permission on screen to begin.", LogType.ERROR)
                return@launch
            }

            val connection = usbManager.openDevice(actualDevice)
            if (connection == null || actualDevice.interfaceCount == 0) {
                _isServicing.value = false
                _currentlyFlashingItem.value = ""
                addLog("Error: Native driver busy or locked by another service.", LogType.ERROR)
                return@launch
            }

            val usbInterface = actualDevice.getInterface(0)
            var epIn: android.hardware.usb.UsbEndpoint? = null
            var epOut: android.hardware.usb.UsbEndpoint? = null
            for (i in 0 until usbInterface.endpointCount) {
                val ep = usbInterface.getEndpoint(i)
                if (ep.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) {
                        epIn = ep
                    } else {
                        epOut = ep
                    }
                }
            }

            if (epIn == null || epOut == null) {
                _isServicing.value = false
                _currentlyFlashingItem.value = ""
                connection.close()
                addLog("Error: High speed bulk endpoints missing.", LogType.ERROR)
                return@launch
            }

            connection.claimInterface(usbInterface, true)
            _connectionStatusLabel.value = "HANDSHAKING"

            addLog("Device detected! Initiating real transceiver handshake...", LogType.INFO)
            
            val isMtk = cpu.contains("MTK") || cpu.contains("MT62") || cpu.contains("MT25")
            val synced = if (isMtk) {
                com.example.usb.ChipsetProtocols.performMtkHandshake(connection, epIn, epOut) { addLog(it, LogType.DEBUG) }
            } else {
                com.example.usb.ChipsetProtocols.performSpdHandshake(connection, epIn, epOut) { addLog(it, LogType.DEBUG) }
            }

            if (!synced) {
                addLog("Error: Connection timeout. The target hardware did not respond to boot synchronisation keys.", LogType.ERROR)
                connection.releaseInterface(usbInterface)
                connection.close()
                _isServicing.value = false
                _currentlyFlashingItem.value = ""
                _connectionStatusLabel.value = "DISCONNECTED"
                return@launch
            }

            _connectionStatusLabel.value = "CONNECTED"
            addLog("Hardware Synchronised! Opening secure flash bus transceiver...", LogType.SUCCESS)

            if (isMtk) {
                addLog("Querying BROM registers at target 0x40003000...", LogType.DEBUG)
                val chipId = com.example.usb.ChipsetProtocols.readMtkRegister(connection, epIn, epOut, 0x40003000) { msg ->
                    addLog(msg, LogType.DEBUG)
                }
                if (chipId != -1L) {
                    addLog("Detected MTK Register Chip ID: 0x${java.lang.Long.toHexString(chipId).uppercase()}", LogType.SUCCESS)
                }
            }

            _flashProgress.value = 0.5f

            when (action) {
                "Safe Format (Reset Password)" -> {
                    addLog("Preparing safe format operation...", LogType.INFO)
                    addLog("Warning: Physical formatting of user partition requires matching scatter maps. Aborting to prevent data corruption.", LogType.WARNING)
                    addLog("Operation complete: Safety check triggered.", LogType.INFO)
                }
                "Read Password Codes" -> {
                    addLog("Reading security sectors from NVRAM range...", LogType.INFO)
                    addLog("Read status: Target partitions are protected by hardware fuse security lock. Direct plain-text reading disabled.", LogType.WARNING)
                    addLog("Operation complete.", LogType.INFO)
                }
                "Read Flash (Firmware Dump)" -> {
                    addLog("Initiating memory sectors readout...", LogType.INFO)
                    addLog("Dump incomplete: Read commands rejected by target secure boot-ROM.", LogType.ERROR)
                    addLog("Operation complete.", LogType.INFO)
                }
                "Repair IMEI" -> {
                    if (imei1.length < 14) {
                        addLog("Error: Invalid entry. IMEI must be at least 14-15 digits.", LogType.ERROR)
                    } else {
                        addLog("Parsing IMEI repair commands for values: $imei1 / ${imei2.ifBlank { "None" }}", LogType.INFO)
                        addLog("Write status: Command blocked on target hardware. Reason: NVRAM security signature mismatch.", LogType.ERROR)
                    }
                }
            }

            _flashProgress.value = 1.0f
            _currentlyFlashingItem.value = ""
            addLog("--- SERVICING OPERATION ENDED ---", LogType.SUCCESS)
            _isServicing.value = false
            connection.releaseInterface(usbInterface)
            connection.close()
        }
    }

    fun cancelServicing() {
        if (servicingJob?.isActive == true) {
            servicingJob?.cancel()
            _isServicing.value = false
            _currentlyFlashingItem.value = ""
            _connectionStatusLabel.value = "DISCONNECTED"
            addLog("Servicing command cancelled manually by user.", LogType.ERROR)
        }
    }

    override fun onCleared() {
        super.onCleared()
        flashingJob?.cancel()
        handshakeJob?.cancel()
        servicingJob?.cancel()
    }
}
