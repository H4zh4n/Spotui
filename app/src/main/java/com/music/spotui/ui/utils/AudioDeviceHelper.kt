package com.music.spotui.ui.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.music.spotui.di.SongPlayer

enum class AudioDeviceType {
    SPEAKER,
    WIRED_HEADPHONES,
    BLUETOOTH,
    OTHER
}

data class AudioDeviceItem(
    val id: String,
    val name: String,
    val type: AudioDeviceType,
    val isActive: Boolean,
    val isConnected: Boolean,
    val audioDeviceInfo: AudioDeviceInfo? = null,
    val bluetoothAddress: String? = null
)

object AudioDeviceHelper {

    @Volatile
    private var forcedSpeakerMode: Boolean = false

    val currentRouteNameState = androidx.compose.runtime.mutableStateOf("This Phone (Speaker)")

    fun updateRouteName(context: Context) {
        currentRouteNameState.value = getCurrentAudioRouteName(context)
    }

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Retrieves the current active audio route name.
     */
    fun getCurrentAudioRouteName(context: Context): String {
        if (forcedSpeakerMode) {
            return "This Phone (Speaker)"
        }
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val bt = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
            if (bt != null) {
                return bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
            }
            val wired = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (wired != null) "Headphones" else "This Phone (Speaker)"
        } catch (e: Exception) {
            "This Phone (Speaker)"
        }
    }

    /**
     * Returns a deduplicated list of available audio output routes and paired Bluetooth devices.
     */
    fun getAvailableAudioDevices(context: Context): List<AudioDeviceItem> {
        val deviceList = mutableListOf<AudioDeviceItem>()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val activeRouteName = getCurrentAudioRouteName(context)

        try {
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            // 1. Deduplicated Connected Bluetooth devices
            val btOutputs = outputs.filter {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }

            // Group by product name to eliminate duplicates (A2DP vs SCO for same earbud)
            val uniqueBtOutputs = mutableListOf<AudioDeviceInfo>()
            for (bt in btOutputs) {
                val btName = bt.productName?.toString()?.trim().orEmpty()
                val alreadyAdded = uniqueBtOutputs.any {
                    val name = it.productName?.toString()?.trim().orEmpty()
                    name.equals(btName, ignoreCase = true)
                }
                if (!alreadyAdded) {
                    // Prefer A2DP or BLE_HEADSET over SCO
                    val preferred = btOutputs.firstOrNull {
                        val n = it.productName?.toString()?.trim().orEmpty()
                        n.equals(btName, ignoreCase = true) && (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET))
                    } ?: bt
                    uniqueBtOutputs.add(preferred)
                }
            }

            var hasActiveBtSet = false
            for (bt in uniqueBtOutputs) {
                val name = bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Device"
                val isActive = !forcedSpeakerMode && (name.equals(activeRouteName, ignoreCase = true) || (!hasActiveBtSet && uniqueBtOutputs.size == 1 && activeRouteName != "This Phone (Speaker)" && activeRouteName != "Headphones"))
                if (isActive) hasActiveBtSet = true

                deviceList.add(
                    AudioDeviceItem(
                        id = "bt_${bt.id}",
                        name = name,
                        type = AudioDeviceType.BLUETOOTH,
                        isActive = isActive,
                        isConnected = true,
                        audioDeviceInfo = bt
                    )
                )
            }

            // 2. Wired Headphones / USB Headset
            val wiredOutput = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (wiredOutput != null) {
                val name = wiredOutput.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired Headphones"
                val isActive = !forcedSpeakerMode && !hasActiveBtSet && (name.equals(activeRouteName, ignoreCase = true) || activeRouteName == "Headphones")
                deviceList.add(
                    AudioDeviceItem(
                        id = "wired_${wiredOutput.id}",
                        name = name,
                        type = AudioDeviceType.WIRED_HEADPHONES,
                        isActive = isActive,
                        isConnected = true,
                        audioDeviceInfo = wiredOutput
                    )
                )
            }

            // 3. Built-in Phone Speaker
            val speakerOutput = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            val isSpeakerActive = forcedSpeakerMode || deviceList.none { it.isActive }
            deviceList.add(
                AudioDeviceItem(
                    id = "speaker",
                    name = "This Phone (Speaker)",
                    type = AudioDeviceType.SPEAKER,
                    isActive = isSpeakerActive,
                    isConnected = true,
                    audioDeviceInfo = speakerOutput
                )
            )

            // 4. Paired (Offline) Bluetooth Devices from BluetoothAdapter
            if (hasBluetoothPermission(context)) {
                val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
                    ?: android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                val bonded = btAdapter?.bondedDevices
                if (bonded != null) {
                    for (dev in bonded) {
                        try {
                            val devName = dev.name
                            if (!devName.isNullOrBlank()) {
                                val isAlreadyConnectedOrAdded = deviceList.any {
                                    it.name.equals(devName, ignoreCase = true) ||
                                            (it.bluetoothAddress != null && it.bluetoothAddress.equals(dev.address, ignoreCase = true))
                                }
                                if (!isAlreadyConnectedOrAdded) {
                                    deviceList.add(
                                        AudioDeviceItem(
                                            id = "bonded_${dev.address}",
                                            name = devName,
                                            type = AudioDeviceType.BLUETOOTH,
                                            isActive = false,
                                            isConnected = false,
                                            bluetoothAddress = dev.address
                                        )
                                    )
                                }
                            }
                        } catch (e: SecurityException) {
                            // Security exception guard
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (deviceList.isEmpty()) {
                deviceList.add(
                    AudioDeviceItem(
                        id = "speaker",
                        name = "This Phone (Speaker)",
                        type = AudioDeviceType.SPEAKER,
                        isActive = true,
                        isConnected = true
                    )
                )
            }
        }

        return deviceList
    }

    /**
     * Switches the active audio output to the selected device item.
     */
    fun switchAudioOutput(context: Context, item: AudioDeviceItem): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            if (item.type == AudioDeviceType.SPEAKER) {
                forcedSpeakerMode = true
                val speakerOutput = item.audioDeviceInfo ?: am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speakerCommDevice = speakerOutput ?: am.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerCommDevice != null) {
                        am.setCommunicationDevice(speakerCommDevice)
                    } else {
                        am.clearCommunicationDevice()
                    }
                } else {
                    try {
                        am.stopBluetoothSco()
                        am.isBluetoothScoOn = false
                    } catch (e: Exception) {}
                }
                try {
                    am.isSpeakerphoneOn = true
                } catch (e: Exception) {}

                // Force ExoPlayer audio stream to internal speaker
                SongPlayer.setPreferredAudioDevice(speakerOutput)

            } else {
                forcedSpeakerMode = false
                try {
                    am.isSpeakerphoneOn = false
                } catch (e: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (item.audioDeviceInfo != null) {
                        val commDevice = am.availableCommunicationDevices.firstOrNull {
                            it.id == item.audioDeviceInfo.id || it.type == item.audioDeviceInfo.type
                        }
                        if (commDevice != null) {
                            am.setCommunicationDevice(commDevice)
                        } else {
                            am.clearCommunicationDevice()
                        }
                    } else {
                        am.clearCommunicationDevice()
                    }
                } else {
                    if (item.type == AudioDeviceType.BLUETOOTH) {
                        try {
                            am.startBluetoothSco()
                            am.isBluetoothScoOn = true
                        } catch (e: Exception) {}
                    } else {
                        try {
                            am.stopBluetoothSco()
                            am.isBluetoothScoOn = false
                        } catch (e: Exception) {}
                    }
                }

                // Set preferred device on ExoPlayer
                SongPlayer.setPreferredAudioDevice(item.audioDeviceInfo)
            }
            updateRouteName(context)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launches Android's native System Audio Output Switcher panel (Android 11+)
     * or falls back to System Bluetooth Settings.
     */
    fun openSystemAudioSwitcher(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent("android.settings.AUDIO_OUTPUT_SWITCHER_SETTINGS").apply {
                    putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                // Ignore if settings cannot be opened
            }
        }
    }
}
