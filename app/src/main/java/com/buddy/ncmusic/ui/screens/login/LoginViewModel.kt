package com.buddy.ncmusic.ui.screens.login

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.util.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    private val repo get() = NCMusicApp.instance.musicRepository

    // 二维码登录
    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _qrStatus = MutableStateFlow("正在获取二维码...")
    val qrStatus: StateFlow<String> = _qrStatus.asStateFlow()

    // 账号登录（密码 / 验证码）
    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _captcha = MutableStateFlow("")
    val captcha: StateFlow<String> = _captcha.asStateFlow()

    /** 账号登录方式：false = 密码，true = 验证码 */
    private val _useCaptcha = MutableStateFlow(false)
    val useCaptcha: StateFlow<Boolean> = _useCaptcha.asStateFlow()

    private val _countdown = MutableStateFlow(0)
    val countdown: StateFlow<Int> = _countdown.asStateFlow()

    private val _loggingIn = MutableStateFlow(false)
    val loggingIn: StateFlow<Boolean> = _loggingIn.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    private var pollJob: Job? = null
    private var countdownJob: Job? = null

    init {
        startQrLogin()
    }

    // ---------------- 二维码登录 ----------------

    fun startQrLogin() {
        pollJob?.cancel()
        viewModelScope.launch {
            _qrStatus.value = "正在获取二维码..."
            _qrBitmap.value = null
            when (val r = repo.createQrKey()) {
                is ApiResult.Error -> {
                    _qrStatus.value = r.message
                    return@launch
                }
                is ApiResult.Success -> {
                    val key = r.data
                    val bmp = repo.createQrBitmap(key)
                    if (bmp != null) {
                        _qrBitmap.value = bmp
                        startPolling(key)
                    } else {
                        _qrStatus.value = "二维码生成失败，请点击刷新"
                    }
                }
            }
        }
    }

    private fun startPolling(key: String) {
        pollJob = viewModelScope.launch {
            var failCount = 0
            while (isActive) {
                when (val r = repo.checkQr(key)) {
                    is ApiResult.Error -> {
                        // 网络抖动不应中断扫码：连续失败 5 次才提示手动刷新
                        failCount++
                        if (failCount >= 5) {
                            _qrStatus.value = "轮询失败，请点击刷新"
                            return@launch
                        }
                        _qrStatus.value = "网络不稳定，正在重试…（$failCount/5）"
                        delay(2000)
                        continue
                    }
                    is ApiResult.Success -> when (r.data.code) {
                        800 -> {
                            _qrStatus.value = "二维码已过期，请点击刷新"
                            return@launch
                        }
                        801 -> {
                            failCount = 0
                            _qrStatus.value = "请使用网易云音乐 App 扫码"
                        }
                        802 -> _qrStatus.value = "已扫码，请在手机上确认登录"
                        803 -> {
                            _qrStatus.value = "登录成功"
                            _success.value = true
                            return@launch
                        }
                        900 -> {
                            _qrStatus.value = "需要安全验证，请改用手机号或 Cookie 登录"
                            return@launch
                        }
                        else -> {
                            val msg = r.data.message.orEmpty()
                            _qrStatus.value = if (
                                msg.contains("切换其他登录方式") || msg.contains("升级版本")
                            ) {
                                "扫码登录被官方限制：$msg\n请改用「手机号登录」或「Cookie 登录」"
                            } else {
                                msg.ifBlank { "未知状态（${r.data.code}）" }
                            }
                            // 终态：停止轮询，避免持续触发风控
                            return@launch
                        }
                    }
                }
                delay(2000)
            }
        }
    }

    // ---------------- 账号登录 ----------------

    fun onPhoneChange(value: String) {
        _phone.value = value
    }

    fun onPasswordChange(value: String) {
        _password.value = value
    }

    fun onCaptchaChange(value: String) {
        _captcha.value = value
    }

    fun setUseCaptcha(use: Boolean) {
        _useCaptcha.value = use
        _message.value = null
    }

    fun phoneLogin() {
        if (_phone.value.isBlank() || _password.value.isBlank()) {
            _message.value = "请输入手机号和密码"
            return
        }
        viewModelScope.launch {
            _loggingIn.value = true
            _message.value = null
            when (val r = repo.cellphoneLogin(_phone.value, _password.value)) {
                is ApiResult.Success -> _success.value = true
                is ApiResult.Error -> _message.value = r.message
            }
            _loggingIn.value = false
        }
    }

    fun sendCaptcha() {
        val phone = _phone.value
        if (phone.isBlank() || phone.length != 11) {
            _message.value = "请输入正确的手机号"
            return
        }
        viewModelScope.launch {
            when (val r = repo.sendCaptcha(phone)) {
                is ApiResult.Success -> {
                    _message.value = "验证码已发送"
                    startCountdown()
                }
                is ApiResult.Error -> _message.value = r.message
            }
        }
    }

    fun captchaLogin() {
        if (_phone.value.isBlank() || _captcha.value.isBlank()) {
            _message.value = "请输入手机号和验证码"
            return
        }
        viewModelScope.launch {
            _loggingIn.value = true
            _message.value = null
            when (val r = repo.captchaLogin(_phone.value, _captcha.value)) {
                is ApiResult.Success -> _success.value = true
                is ApiResult.Error -> _message.value = r.message
            }
            _loggingIn.value = false
        }
    }

    // ---------------- Cookie 登录 ----------------

    private val _showCookieDialog = MutableStateFlow(false)
    val showCookieDialog: StateFlow<Boolean> = _showCookieDialog.asStateFlow()

    private val _cookieInput = MutableStateFlow("")
    val cookieInput: StateFlow<String> = _cookieInput.asStateFlow()

    fun setShowCookieDialog(show: Boolean) {
        _showCookieDialog.value = show
    }

    fun onCookieChange(text: String) {
        _cookieInput.value = text
    }

    /** 使用 Cookie 登录（需包含 MUSIC_U） */
    fun loginWithCookie() {
        val ck = _cookieInput.value.trim()
        if (ck.isBlank()) {
            _message.value = "请粘贴 Cookie"
            return
        }
        viewModelScope.launch {
            _loggingIn.value = true
            when (val r = repo.loginWithCookie(ck)) {
                is ApiResult.Success -> {
                    _success.value = true
                    _showCookieDialog.value = false
                }
                is ApiResult.Error -> _message.value = r.message
            }
            _loggingIn.value = false
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        _countdown.value = 60
        countdownJob = viewModelScope.launch {
            while (_countdown.value > 0) {
                delay(1000)
                _countdown.value -= 1
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        countdownJob?.cancel()
        super.onCleared()
    }
}
