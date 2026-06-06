package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FlashingProfile
import com.example.usb.UsbOtgHelper
import com.example.usb.ConnectedDevice
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FlashingScreen(
    viewModel: FlashingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ViewModel Flows
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val mtkAuthFile by viewModel.mtkAuthFile.collectAsStateWithLifecycle()
    val mtkScatterFile by viewModel.mtkScatterFile.collectAsStateWithLifecycle()
    val mtkPreloaderFile by viewModel.mtkPreloaderFile.collectAsStateWithLifecycle()

    val spdFdl1File by viewModel.spdFdl1File.collectAsStateWithLifecycle()
    val spdFdl2File by viewModel.spdFdl2File.collectAsStateWithLifecycle()
    val spdPacFile by viewModel.spdPacFile.collectAsStateWithLifecycle()

    val qcProgrammerFile by viewModel.qcProgrammerFile.collectAsStateWithLifecycle()
    val qcRawProgramFile by viewModel.qcRawProgramFile.collectAsStateWithLifecycle()
    val qcPatchFile by viewModel.qcPatchFile.collectAsStateWithLifecycle()

    val partitions by viewModel.partitions.collectAsStateWithLifecycle()
    val flashProgress by viewModel.flashProgress.collectAsStateWithLifecycle()
    val currentlyFlashingItem by viewModel.currentlyFlashingItem.collectAsStateWithLifecycle()
    val isFlashing by viewModel.isFlashing.collectAsStateWithLifecycle()

    val connectedUsbDevices by viewModel.connectedUsbDevices.collectAsStateWithLifecycle()
    val connectionStatusLabel by viewModel.connectionStatusLabel.collectAsStateWithLifecycle()
    val consoleLogs by viewModel.consoleLogs.collectAsStateWithLifecycle()
    val savedProfiles by viewModel.savedProfiles.collectAsStateWithLifecycle()

    val selectedServicingCpu by viewModel.selectedServicingCpu.collectAsStateWithLifecycle()
    val selectedServicingAction by viewModel.selectedServicingAction.collectAsStateWithLifecycle()
    val imei1Input by viewModel.imei1Input.collectAsStateWithLifecycle()
    val imei2Input by viewModel.imei2Input.collectAsStateWithLifecycle()
    val isServicing by viewModel.isServicing.collectAsStateWithLifecycle()
    val autoBypassSla by viewModel.autoBypassSla.collectAsStateWithLifecycle()

    // Dialog state for loading/saving custom profiles
    var showProfileSaveDialog by remember { mutableStateOf(false) }
    var newProfileNameInput by remember { mutableStateOf("") }
    var showFilePickerForField by remember { mutableStateOf<String?>(null) } // Field key e.g. "MTK_AUTH"

    // Dialog list options for our file pickers
    val mockAuthFiles = listOf("MTK_Auth_v3.auth", "SecureBypass_v5.auth", "MTK_AllInOne_DA_v2.auth", "Custom_OEM.auth")
    val mockScatterFiles = listOf("MT6765_Android_scatter.txt", "MT6739_Scatter_emmc.txt", "MT6877_Dimensity_scatter.txt", "MT6580_Scatter_legacy.txt")
    val mockPreloaders = listOf("preloader_k61v1.bin", "preloader_redmi9a.bin", "preloader_vivo_y12s.bin")
    
    val mockFdls = listOf("fdl1_sc9863a.bin", "fdl1_unisoc_t610.bin", "fdl2_app_sc9863a.bin", "fdl2_unisoc_t610.bin", "generic_spd.bin")
    val mockPacs = listOf("SPD_SC9863A_firmware.pac", "Unisoc_T610_stock.pac", "SPD_Tablet_OS_v2.pac")

    val mockProgrammers = listOf("prog_firehose_8953.mbn", "prog_firehose_ddr_8998.elf", "prog_firehose_lite_sdm450.mbn", "prog_emmc_firehose_generic.mbn")
    val mockRawprograms = listOf("rawprogram0.xml", "rawprogram_unsparse.xml", "rawprogram_upgrade.xml")
    val mockPatches = listOf("patch0.xml", "patch_backup.xml")

    val scrollState = rememberScrollState()
    val consoleListState = rememberLazyListState()

    // Auto scroll console logs to bottom when new logs write
    LaunchedEffect(consoleLogs.size) {
        if (consoleLogs.isNotEmpty()) {
            consoleListState.animateScrollToItem(consoleLogs.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        topBar = {
            // Material 3 Top Navigation App Bar
            Surface(
                color = Color.White,
                tonalElevation = 2.dp,
                modifier = Modifier.shadow(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsInputHdmi,
                            contentDescription = "Flasher icon",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FlashTool Pro",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = SlateTextDark,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            text = "V4.2.0-STABLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SlateTextLight,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    
                    // Rotating system sync status button
                    IconButton(
                        onClick = { viewModel.scanForUsbDevices() },
                        modifier = Modifier.testTag("rescan_devices_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync USB status",
                            tint = Purple40,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Sticky Action Controls Bar
            Surface(
                color = Color.White,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .shadow(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Reset / Clear Options
                    OutlinedButton(
                        onClick = {
                            if (!isFlashing) {
                                viewModel.clearLogs()
                            }
                        },
                        enabled = !isFlashing,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF64748B)
                        ),
                        border = BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear terminal"
                        )
                    }

                    // Primary Action Flashing Execute Control
                    val isMtkReady = currentTab == "MTK" && mtkAuthFile.isNotEmpty() && mtkScatterFile.isNotEmpty()
                    val isSpdReady = currentTab == "SPD" && spdFdl1File.isNotEmpty() && spdFdl2File.isNotEmpty()
                    val isQcReady = currentTab == "QUALCOMM" && qcProgrammerFile.isNotEmpty()

                    val isConfigReady = isMtkReady || isSpdReady || isQcReady

                    Button(
                        onClick = {
                            if (isFlashing) {
                                viewModel.haltFlashing()
                            } else {
                                viewModel.startFlashing()
                            }
                        },
                        enabled = isConfigReady || isFlashing,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFlashing) Color(0xFFDC2626) else Purple40,
                            disabledContainerColor = Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .shadow(
                                elevation = if (isConfigReady) 4.dp else 0.dp,
                                shape = RoundedCornerShape(14.dp),
                                ambientColor = Purple40,
                                spotColor = Purple40
                            )
                            .testTag("action_flash_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isFlashing) Icons.Default.Block else Icons.Default.FlashOn,
                                contentDescription = "Flash command",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isFlashing) "HALT FLASHING" else "START FLASHING",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // SECTION 1: USB Device Diagnostics & Status Bar
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "USB OTG Status Diagnostics",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = SlateTextDark,
                                    fontSize = 13.sp
                                )
                            )
                            if (connectedUsbDevices.isEmpty()) {
                                Text(
                                    text = "Ready to simulate serial bridge",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = SlateTextLight,
                                        fontSize = 11.sp
                                    )
                                )
                            } else {
                                Text(
                                    text = "Detected ${connectedUsbDevices.size} hardware link",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TerminalGreen,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        // State Badges based on Chipset and Handshake Status
                        val badgeBg = when (connectionStatusLabel) {
                            "CONNECTED" -> Color(0xFFDCFCE7)
                            "HANDSHAKING" -> Color(0xFFFEF9C3)
                            else -> Color(0xFFF1F5F9)
                        }
                        val badgeText = when (connectionStatusLabel) {
                            "CONNECTED" -> Color(0xFF15803D)
                            "HANDSHAKING" -> Color(0xFF854D0E)
                            else -> Color(0xFF475569)
                        }

                        Box(
                            modifier = Modifier
                                .background(badgeBg, RoundedCornerShape(8.dp))
                                .border(
                                    1.dp, 
                                    if (connectionStatusLabel == "CONNECTED") Color(0xFFBBF7D0) else Color.Transparent, 
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("connection_badge")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (connectionStatusLabel == "CONNECTED") Color(0xFF22C55E)
                                            else if (connectionStatusLabel == "HANDSHAKING") Color(0xFFEAB308)
                                            else Color(0xFF94A3B8),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (connectionStatusLabel == "CONNECTED") "PORT READY" else connectionStatusLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = badgeText,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    // Connected Hardware List Row (if hardware actual or virtual)
                    if (connectedUsbDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = SlateBorder)
                        Spacer(modifier = Modifier.height(8.dp))
                        connectedUsbDevices.forEach { dev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Usb,
                                    contentDescription = "USB plug",
                                    tint = Purple40,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${dev.manufacturerName ?: "OEM Device"} - ${dev.productName ?: "Mobile Interface"}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = SlateTextDark,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Text(
                                        text = UsbOtgHelper.getChipsetLabel(dev.vendorId, dev.productId),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = SlateTextLight
                                        )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEEF2F6), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "VID: 0x${Integer.toHexString(dev.vendorId).uppercase()}",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateTextLight
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.runHandshake() },
                            enabled = !isFlashing,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF1F5F9),
                                contentColor = SlateTextDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("probe_handshake_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Cable,
                                    contentDescription = "Handshake icon",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PROBE USB HANDSHAKE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: Chipset Navigation Tabs
            Surface(
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("MTK", "SPD", "QUALCOMM").forEach { tab ->
                        val isActive = currentTab == tab
                        val bg = if (isActive) Purple40 else Color.White
                        val textColor = if (isActive) Color.White else SlateTextDark
                        val border = if (isActive) BorderStroke(0.dp, Color.Transparent) else BorderStroke(1.dp, SlateBorder)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .shadow(if (isActive) 2.dp else 0.dp, CircleShape)
                                .background(bg, CircleShape)
                                .border(border, CircleShape)
                                .clip(CircleShape)
                                .clickable(enabled = !isFlashing) { viewModel.setTab(tab) }
                                .testTag("chipset_tab_$tab"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                    color = textColor,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }
                }
            }

            // SECTION 3: Dynamic File Configuration Cards based on Tab Selection
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FILE CONFIGURATION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SlateTextLight,
                                letterSpacing = 1.sp
                            )
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEFF6FF), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = when (currentTab) {
                                    "MTK" -> "BROM PROTOCOL"
                                    "SPD" -> "FDL ENGINE"
                                    else -> "FIREHOSE EDL"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8),
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }

                    // Fields Rendered depending on Active Tab
                    when (currentTab) {
                        "MTK" -> {
                            // MTK Mode Fields: Authentication File, Scatter Layout File, Preloader
                            ConfigFileSelectorField(
                                label = "Authentication File (.auth)",
                                value = mtkAuthFile,
                                onValueChange = { viewModel.updateMtkAuth(it) },
                                onPickSimulated = { showFilePickerForField = "MTK_AUTH" },
                                icon = Icons.Outlined.Key,
                                isEnabled = !isFlashing
                            )

                            ConfigFileSelectorField(
                                label = "Scatter Layout File (.txt)",
                                value = mtkScatterFile,
                                onValueChange = { viewModel.updateMtkScatter(it) },
                                onPickSimulated = { showFilePickerForField = "MTK_SCATTER" },
                                icon = Icons.Outlined.GridOn,
                                isEnabled = !isFlashing
                            )

                            ConfigFileSelectorField(
                                label = "Preloader Core (.bin)",
                                value = mtkPreloaderFile,
                                onValueChange = { viewModel.updateMtkPreloader(it) },
                                onPickSimulated = { showFilePickerForField = "MTK_PRELOADER" },
                                icon = Icons.Outlined.Memory,
                                isEnabled = !isFlashing
                            )
                        }
                        "SPD" -> {
                            // SPD Mode Fields: FDL1, FDL2, Firmware PAC Package
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ConfigFileSelectorField(
                                        label = "FDL 1 Bootloader",
                                        value = spdFdl1File,
                                        onValueChange = { viewModel.updateSpdFdl1(it) },
                                        onPickSimulated = { showFilePickerForField = "SPD_FDL1" },
                                        icon = Icons.Outlined.Memory,
                                        isEnabled = !isFlashing
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ConfigFileSelectorField(
                                        label = "FDL 2 App loader",
                                        value = spdFdl2File,
                                        onValueChange = { viewModel.updateSpdFdl2(it) },
                                        onPickSimulated = { showFilePickerForField = "SPD_FDL2" },
                                        icon = Icons.Outlined.IntegrationInstructions,
                                        isEnabled = !isFlashing
                                    )
                                }
                            }

                            ConfigFileSelectorField(
                                label = "Firmware PAC Package (*.pac)",
                                value = spdPacFile,
                                onValueChange = { viewModel.updateSpdPac(it) },
                                onPickSimulated = { showFilePickerForField = "SPD_PAC" },
                                icon = Icons.Outlined.FolderZip,
                                isEnabled = !isFlashing
                            )
                        }
                        "QUALCOMM" -> {
                            // Qualcomm EDL Fields: Firehose Programmer, RawProgram, Patch
                            ConfigFileSelectorField(
                                label = "Firehose Programmer (.mbn/.elf)",
                                value = qcProgrammerFile,
                                onValueChange = { viewModel.updateQcProgrammer(it) },
                                onPickSimulated = { showFilePickerForField = "QC_PROGRAMMER" },
                                icon = Icons.Outlined.Terminal,
                                isEnabled = !isFlashing
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ConfigFileSelectorField(
                                        label = "RawProgram Layout XML",
                                        value = qcRawProgramFile,
                                        onValueChange = { viewModel.updateQcRawProgram(it) },
                                        onPickSimulated = { showFilePickerForField = "QC_RAWPROGRAM" },
                                        icon = Icons.Outlined.Code,
                                        isEnabled = !isFlashing
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ConfigFileSelectorField(
                                        label = "Patch Points XML",
                                        value = qcPatchFile,
                                        onValueChange = { viewModel.updateQcPatch(it) },
                                        onPickSimulated = { showFilePickerForField = "QC_PATCH" },
                                        icon = Icons.Outlined.Architecture,
                                        isEnabled = !isFlashing
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 4: Saved Profiles Manager (Offline Persistence via Room Database)
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "FLASHER PROFILES (ROOM ARCHIVE)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = SlateTextLight,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (savedProfiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No profiles saved yet.\nEnter name below to save configuration.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = SlateTextLight,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    } else {
                        Text(
                            text = "Restore saved target blocks directly:",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = SlateTextDark,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            savedProfiles.forEach { profile ->
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = !isFlashing) { viewModel.loadProfile(profile) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = profile.profileName,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E40AF)
                                            )
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(Purple40, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = profile.chipsetType,
                                                color = Color.White,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete config",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable { viewModel.deleteProfile(profile) }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Save Profile form input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newProfileNameInput,
                            onValueChange = { newProfileNameInput = it },
                            placeholder = { Text("e.g. Redmi 9A Stack", fontSize = 11.sp) },
                            singleLine = true,
                            enabled = !isFlashing,
                            textStyle = TextStyle(fontSize = 12.sp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("profile_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Purple40,
                                unfocusedBorderColor = SlateBorder,
                                disabledBorderColor = SlateBorder
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = {
                                if (newProfileNameInput.isNotBlank()) {
                                    viewModel.saveProfile(newProfileNameInput)
                                    newProfileNameInput = ""
                                }
                            },
                            enabled = !isFlashing && newProfileNameInput.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("save_profile_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple40
                            )
                        ) {
                            Text("Save Active", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (currentTab == "MTK" || currentTab == "SPD") {
                // KEYPAD PHONE SERVICING & PASSWORD ENGINE (HIGH DENSITY PANEL)
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keypad_servicing_panel")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFFEFF6FF), RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = "Handy wrench icon",
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "KEYPAD PHONE SERVICING",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = SlateTextLight,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFFCD34D), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "MTK & SPD LOCK BYPASS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309),
                                        fontSize = 8.sp
                                    )
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Direct boot-ROM service tool to format userdata memory, bypass locks/passwords (Pattern/PIN), dump firmware .BIN backups, and fix blank IMEIs.",
                            style = MaterialTheme.typography.bodySmall.copy(
                               color = SlateTextLight,
                               fontSize = 11.sp,
                               lineHeight = 15.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = SlateBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        // 1. CPU SELECTOR CHIPS
                        Text(
                            text = "SELECT KEYPAD CPU PROFILE",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SlateTextDark,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        val keypadCpus = listOf(
                            "MT6261 (MediaTek Keypad)",
                            "MT6260D / MT2503",
                            "SC6531E (Spreadtrum Keypad)",
                            "SC6530 / SC7731"
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            keypadCpus.forEach { cpu ->
                                val isSelected = selectedServicingCpu == cpu
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (isSelected) Purple40 else Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Purple40 else SlateBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !isServicing && !isFlashing) {
                                            viewModel.setServicingCpu(cpu)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("keypad_cpu_${cpu.replace(" ", "_").replace("(", "").replace(")", "").replace("/", "")}")
                                ) {
                                    Text(
                                        text = cpu,
                                        color = if (isSelected) Color.White else SlateTextDark,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. SERVICING ACTIONS GRID (2x2)
                        Text(
                            text = "SELECT ACTION COMMAND",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SlateTextDark,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val servicingActions = listOf(
                            Triple("Safe Format (Reset Password)", Icons.Default.LockOpen, "Format FAT data area to wipe locks"),
                            Triple("Read Password Codes", Icons.Default.VpnKey, "Decrypt stored security PIN locks"),
                            Triple("Read Flash (Firmware Dump)", Icons.Default.Download, "Backup full stock .BIN firmware"),
                            Triple("Repair IMEI", Icons.Default.Dialpad, "Restore dual-IMEI parameters")
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val rows = servicingActions.chunked(2)
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowItems.forEach { actionTriple ->
                                        val actName = actionTriple.first
                                        val isActSelected = selectedServicingAction == actName
                                        
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    color = if (isActSelected) Color(0xFFEFF6FF) else Color.White,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .border(
                                                    width = if (isActSelected) 1.5.dp else 1.dp,
                                                    color = if (isActSelected) Color(0xFF3B82F6) else SlateBorder,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable(enabled = !isServicing && !isFlashing) {
                                                    viewModel.setServicingAction(actName)
                                                }
                                                .padding(10.dp)
                                                .height(58.dp)
                                                .testTag("keypad_action_${actName.replace(" ", "_").replace("(", "").replace(")", "")}")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .background(
                                                            color = if (isActSelected) Color(0xFF3B82F6).copy(alpha = 0.15f) else Color(0xFFF1F5F9),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = actionTriple.second,
                                                        contentDescription = null,
                                                        tint = if (isActSelected) Color(0xFF2563EB) else SlateTextDark,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = actName.split(" ").take(2).joinToString(" "),
                                                        color = SlateTextDark,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = if (isActSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = actionTriple.third,
                                                        color = SlateTextLight,
                                                        fontSize = 8.sp,
                                                        maxLines = 2,
                                                        lineHeight = 10.sp,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. SECURE BYPASS SIGNATURES Option
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isServicing && !isFlashing) { viewModel.toggleAutoBypassSla() }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = autoBypassSla,
                                onCheckedChange = { viewModel.toggleAutoBypassSla() },
                                enabled = !isServicing && !isFlashing,
                                colors = CheckboxDefaults.colors(checkedColor = Purple40),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Bypass SLA/DAA Secure Boot (Autoref-Exploit)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = SlateTextDark,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = "Allows lock-resets and diagnostics on secured platforms",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = SlateTextLight,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }

                        // 4. IMEI WORKFIELDS (Visible if Repair IMEI is active)
                        if (selectedServicingAction == "Repair IMEI") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFEF2F2), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Warning Logo",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "REGULATORY COMPLIANCE WARNING",
                                            color = Color(0xFF991B1B),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        text = "IMEI repair is only for restoring the original manufacturer serial codes when empty or corrupted. Changing global network bands is purely banned.",
                                        color = Color(0xFFB91C1C),
                                        fontSize = 8.5.sp,
                                        lineHeight = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "IMEI Slot 1 (15-Digit)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateTextLight
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = imei1Input,
                                        onValueChange = { viewModel.updateImei1(it) },
                                        placeholder = { Text("e.g. 359XXXXXXXXXXXX", fontSize = 11.sp) },
                                        singleLine = true,
                                        enabled = !isServicing && !isFlashing,
                                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("imei1_input_box"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Purple40,
                                            unfocusedBorderColor = SlateBorder
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "IMEI Slot 2 (Optional)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SlateTextLight
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = imei2Input,
                                        onValueChange = { viewModel.updateImei2(it) },
                                        placeholder = { Text("Optional second Sim", fontSize = 11.sp) },
                                        singleLine = true,
                                        enabled = !isServicing && !isFlashing,
                                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("imei2_input_box"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Purple40,
                                            unfocusedBorderColor = SlateBorder
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 5. SERVICE EMULATOR TRIGGER BUTTON
                        Button(
                            onClick = {
                                if (isServicing) {
                                    viewModel.cancelServicing()
                                } else {
                                    viewModel.executeServicingCommand()
                                }
                            },
                            enabled = !isFlashing,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServicing) Color(0xFFDC2626) else Color(0xFF2563EB),
                                disabledContainerColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("action_service_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isServicing) Icons.Default.Cancel else Icons.Default.PlayArrow,
                                    contentDescription = "Execute tool",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isServicing) "ABORT SERVICE JOB" else "RUN VALUE-ADDED SERVICING TASK",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 5: Partition Layout Checklists (Interactive table)
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PARTITION MAP (SECTORS)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = SlateTextLight,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Table headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Name / Select",
                            modifier = Modifier.weight(1.5f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SlateTextLight,
                                fontSize = 9.sp
                            )
                        )
                        Text(
                            text = "Address",
                            modifier = Modifier.weight(1.2f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SlateTextLight,
                                fontSize = 9.sp
                            )
                        )
                        Text(
                            text = "Size",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SlateTextLight,
                                fontSize = 9.sp
                            )
                        )
                        Text(
                            text = "State",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SlateTextLight,
                                fontSize = 9.sp
                            ),
                            textAlign = TextAlign.End
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Partition rows list
                    partitions.forEachIndexed { idx, partition ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (currentlyFlashingItem == partition.name) 1.5.dp else 0.dp,
                                    color = if (currentlyFlashingItem == partition.name) Purple80 else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(
                                    color = if (currentlyFlashingItem == partition.name) Color(0xFFF3E8FF) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = !isFlashing) { viewModel.togglePartition(idx) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Column 1: Checkbox + Partition Name
                            Row(
                                modifier = Modifier.weight(1.5f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = partition.isChecked,
                                    onCheckedChange = { viewModel.togglePartition(idx) },
                                    enabled = !isFlashing,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Purple40
                                    ),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .testTag("partition_select_${partition.name}")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = partition.name,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (partition.isChecked) SlateTextDark else SlateTextLight,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Column 2: Memory sector Hex offset
                            Text(
                                        text = partition.startAddress,
                                        modifier = Modifier.weight(1.2f),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = SlateTextLight
                                        )
                                    )

                            // Column 3: Disk storage capacity MB
                            Text(
                                text = "${partition.sizeMb} MB",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = SlateTextDark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )

                            // Column 4: High-density Write status badges
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                val textHex: Color
                                val textLabel: String
                                when (partition.writeStatus) {
                                    "WRITING" -> {
                                        textHex = Color(0xFFD97706)
                                        textLabel = "WRITING..."
                                    }
                                    "SUCCESS" -> {
                                        textHex = Color(0xFF16A34A)
                                        textLabel = "OK"
                                    }
                                    "FAILED" -> {
                                        textHex = Color(0xFFDC2626)
                                        textLabel = "ERROR"
                                    }
                                    else -> {
                                        textHex = if (partition.isChecked) SlateTextLight else SlateTextLight.copy(alpha = 0.4f)
                                        textLabel = "READY"
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (partition.writeStatus == "WRITING") {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 1.5.dp,
                                            color = textHex
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = textLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = textHex,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 6: High Density Interactive Terminal Console (Black-Slate Theme)
            Surface(
                color = TerminalBackground,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Log Terminal Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(TerminalGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "[ SYSTEM LOGS ]",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TerminalGreen,
                                    fontSize = 10.sp
                                )
                            )
                        }
                        Text(
                            text = "${consoleLogs.size} logs cached",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF475569),
                                fontSize = 9.sp
                            )
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // Scrollable Stream Logs block
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        LazyColumn(
                            state = consoleListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(consoleLogs) { log ->
                                val texColor = when (log.type) {
                                    LogType.SUCCESS -> TerminalGreen
                                    LogType.WARNING -> TerminalYellow
                                    LogType.ERROR -> Color(0xFFEF4444)
                                    LogType.DEBUG -> TerminalBlue
                                    else -> Color.White
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = log.timestamp,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF475569),
                                            fontSize = 9.sp
                                        ),
                                        modifier = Modifier.width(52.dp)
                                    )
                                    Text(
                                        text = log.text,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            color = texColor,
                                            fontSize = 9.5.sp
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 7: Simulated Progress writing bar
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isFlashing) "WRITING: ${currentlyFlashingItem.uppercase()}" else "FLASHING CHANNEL STATUS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isFlashing) Purple40 else SlateTextLight,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = "${(flashProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Purple40,
                                fontSize = 10.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { flashProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .testTag("flashing_progress_bar"),
                        color = Purple40,
                        trackColor = Color(0xFFF1F5F9),
                    )
                }
            }
        }
    }

    // Modal Simulation Pick File dialogs
    showFilePickerForField?.let { fieldCode ->
        val fileOptions = when (fieldCode) {
            "MTK_AUTH" -> mockAuthFiles
            "MTK_SCATTER" -> mockScatterFiles
            "MTK_PRELOADER" -> mockPreloaders
            "SPD_FDL1", "SPD_FDL2" -> mockFdls
            "SPD_PAC" -> mockPacs
            "QC_PROGRAMMER" -> mockProgrammers
            "QC_RAWPROGRAM" -> mockRawprograms
            else -> mockPatches
        }

        Dialog(onDismissRequest = { showFilePickerForField = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, SlateBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Simulate File Selection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SlateTextDark
                    )
                    Text(
                        text = "Select from local storage assets matching directory structure:",
                        fontSize = 11.sp,
                        color = SlateTextLight
                    )

                    HorizontalDivider(color = SlateBorder)

                    // Options list
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        fileOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        when (fieldCode) {
                                            "MTK_AUTH" -> viewModel.updateMtkAuth(option)
                                            "MTK_SCATTER" -> viewModel.updateMtkScatter(option)
                                            "MTK_PRELOADER" -> viewModel.updateMtkPreloader(option)
                                            "SPD_FDL1" -> viewModel.updateSpdFdl1(option)
                                            "SPD_FDL2" -> viewModel.updateSpdFdl2(option)
                                            "SPD_PAC" -> viewModel.updateSpdPac(option)
                                            "QC_PROGRAMMER" -> viewModel.updateQcProgrammer(option)
                                            "QC_RAWPROGRAM" -> viewModel.updateQcRawProgram(option)
                                            "QC_PATCH" -> viewModel.updateQcPatch(option)
                                        }
                                        viewModel.addLog("Configured input resource: path/sdcard0/$option", LogType.INFO)
                                        showFilePickerForField = null
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = "file icon",
                                    tint = Purple40,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option,
                                    fontSize = 12.sp,
                                    color = SlateTextDark,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Cancel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showFilePickerForField = null }) {
                            Text("Cancel", color = SlateTextLight)
                        }
                    }
                }
            }
        }
    }
}

// Subcomponent: Custom Form File picker configuration layout
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigFileSelectorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onPickSimulated: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = SlateTextLight,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(start = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isEnabled) Color(0xFFF1F5F9) else Color(0xFFE2E8F0).copy(alpha = 0.5f),
                    RoundedCornerShape(12.dp)
                )
                .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isEnabled) Color(0xFF64748B) else Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = isEnabled,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (isEnabled) SlateTextDark else SlateTextLight
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("file_input_${label.replace(" ", "_")}")
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { onPickSimulated() },
                enabled = isEnabled,
                modifier = Modifier
                    .size(24.dp)
                    .testTag("file_picker_${label.replace(" ", "_")}")
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Pick file option",
                    tint = Purple40,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
