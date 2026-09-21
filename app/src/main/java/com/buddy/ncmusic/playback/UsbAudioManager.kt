package com.buddy.ncmusic.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager

/**
 * USB 音频独占管理：
 * 枚举 USB Audio Class 设备 → 申请权限 → claim 音频接口实现独占。
 *
 * 说明：claimInterface 成功即表示从系统音频服务手中抢占了该 USB 音频接口，
 * 此时系统混音不再向该设备输出，播放器独占该通路。
 */
object UsbAudioManager {

    const val ACTION_USB_PERMISSION = "com.buddy.ncmusic.USB_PERMISSION"

    private var connection: UsbDeviceConnection? = null
    private var claimedDeviceId: Int = -1

    private fun manager(context: Context): UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** 设备是否支持 USB Host（OTG） */
    fun isSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)

    private fun hasAudioInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) return true
        }
        return false
    }

    /** 当前插入的 USB 音频设备（USB DAC / 声卡） */
    fun audioDevices(context: Context): List<UsbDevice> = runCatching {
        manager(context).deviceList.values.filter { hasAudioInterface(it) }
    }.getOrDefault(emptyList())

    fun displayName(device: UsbDevice): String = runCatching {
        device.productName?.takeIf { it.isNotBlank() }
            ?: "USB 音频设备 (%04x:%04x)".format(device.vendorId, device.productId)
    }.getOrDefault("USB 音频设备")

    fun hasPermission(context: Context, device: UsbDevice): Boolean =
        runCatching { manager(context).hasPermission(device) }.getOrDefault(false)

    /** 弹出系统 USB 权限授权框 */
    fun requestPermission(context: Context, device: UsbDevice) {
        runCatching {
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            manager(context).requestPermission(device, pi)
        }
    }

    /** 释放独占，交还接口给系统 */
    fun release() {
        runCatching { connection?.close() }
        connection = null
        claimedDeviceId = -1
    }

    /** 尝试独占：claim USB 音频接口。成功返回 true。 */
    fun claimExclusive(context: Context, device: UsbDevice): Boolean {
        if (isClaimed(device)) return true
        release()
        return runCatching {
            val mgr = manager(context)
            if (!mgr.hasPermission(device)) return@runCatching false
            val conn = mgr.openDevice(device) ?: return@runCatching false
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
                if (conn.claimInterface(iface, true)) {
                    connection = conn
                    claimedDeviceId = device.deviceId
                    return@runCatching true
                }
            }
            conn.close()
            false
        }.getOrDefault(false)
    }

    fun isClaimed(device: UsbDevice?): Boolean =
        device != null && claimedDeviceId == device.deviceId

    /**
     * 依据当前设置应用 USB 独占：
     * 开启且有可用设备时尝试独占；关闭时释放。
     * @return 结果描述文案
     */
    fun apply(context: Context, enabled: Boolean): String {
        val devices = audioDevices(context)
        if (devices.isEmpty()) {
            release()
            return if (enabled) "未检测到 USB 音频设备" else "已关闭"
        }
        val device = devices.first()
        if (!enabled) {
            release()
            return "已关闭"
        }
        if (!hasPermission(context, device)) {
            requestPermission(context, device)
            return "已请求 USB 权限，请在弹出的对话框中允许"
        }
        return if (claimExclusive(context, device)) {
            "已独占：${displayName(device)}"
        } else {
            "独占失败（接口被系统占用），将使用系统 USB 音频输出"
        }
    }
}
