package com.buddy.ncmusic.ui.screens.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.ncmusic.ui.components.BrandMarkTile
import com.buddy.ncmusic.ui.util.Md3Motion
import com.buddy.ncmusic.ui.util.rememberReduceMotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onBack: () -> Unit, vm: LoginViewModel = viewModel()) {
    val qrBitmap by vm.qrBitmap.collectAsState()
    val qrStatus by vm.qrStatus.collectAsState()
    val phone by vm.phone.collectAsState()
    val password by vm.password.collectAsState()
    val captcha by vm.captcha.collectAsState()
    val useCaptcha by vm.useCaptcha.collectAsState()
    val cookieInput by vm.cookieInput.collectAsState()
    val countdown by vm.countdown.collectAsState()
    val loggingIn by vm.loggingIn.collectAsState()
    val message by vm.message.collectAsState()
    val success by vm.success.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    val reduceMotion = rememberReduceMotion()

    LaunchedEffect(success) {
        if (success) onBack()
    }

    Column(Modifier.fillMaxSize()) {
        LoginHero(onBack = onBack)

        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("扫码登录") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("手机号登录") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Cookie 登录") })
        }

        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxWidth().weight(1f),
            transitionSpec = {
                val duration = if (reduceMotion) 0 else Md3Motion.Medium1
                fadeIn(tween(duration, easing = Md3Motion.StandardDecelerate)) togetherWith
                    fadeOut(tween(if (reduceMotion) 0 else Md3Motion.Short3, easing = Md3Motion.StandardAccelerate))
            },
            label = "loginTab",
        ) { targetTab ->
            when (targetTab) {
                0 -> QrLoginContent(
                    qrBitmap = qrBitmap,
                    qrStatus = qrStatus,
                    message = message,
                    onRefresh = vm::startQrLogin,
                )
                1 -> PhoneLoginContent(
                    phone = phone,
                    password = password,
                    captcha = captcha,
                    useCaptcha = useCaptcha,
                    countdown = countdown,
                    loggingIn = loggingIn,
                    message = message,
                    onPhoneChange = vm::onPhoneChange,
                    onPasswordChange = vm::onPasswordChange,
                    onCaptchaChange = vm::onCaptchaChange,
                    onUseCaptchaChange = vm::setUseCaptcha,
                    onSendCaptcha = vm::sendCaptcha,
                    onLogin = { if (useCaptcha) vm.captchaLogin() else vm.phoneLogin() },
                )
                else -> CookieLoginContent(
                    cookie = cookieInput,
                    loggingIn = loggingIn,
                    message = message,
                    onCookieChange = vm::onCookieChange,
                    onLogin = vm::loginWithCookie,
                )
            }
        }
    }
}

@Composable
private fun LoginHero(onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "登录",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandMarkTile(size = 72.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "登录云音",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "同步歌单、喜欢与听歌记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun QrLoginContent(
    qrBitmap: android.graphics.Bitmap?,
    qrStatus: String,
    message: String?,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.White,
            shadowElevation = 1.dp,
        ) {
            Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier.size(216.dp),
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(64.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = qrStatus.ifBlank { "请使用网易云音乐 App 扫码" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        LoginMessage(message)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("刷新二维码")
        }
        Spacer(Modifier.height(24.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "扫码登录不会在客户端保存密码",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhoneLoginContent(
    phone: String,
    password: String,
    captcha: String,
    useCaptcha: Boolean,
    countdown: Int,
    loggingIn: Boolean,
    message: String?,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCaptchaChange: (String) -> Unit,
    onUseCaptchaChange: (Boolean) -> Unit,
    onSendCaptcha: () -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to "密码登录", true to "验证码登录").forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = useCaptcha == value,
                    onClick = { onUseCaptchaChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("手机号") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Spacer(Modifier.height(12.dp))
        if (!useCaptcha) {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = captcha,
                    onValueChange = onCaptchaChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("验证码") },
                    leadingIcon = { Icon(Icons.Default.Sms, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onSendCaptcha,
                    enabled = countdown == 0,
                    modifier = Modifier.height(56.dp),
                ) {
                    Text(if (countdown > 0) "${countdown}s" else "获取验证码")
                }
            }
        }
        LoginMessage(message)
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !loggingIn,
        ) {
            if (loggingIn) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (useCaptcha) "验证码登录" else "登录")
        }
    }
}

@Composable
private fun CookieLoginContent(
    cookie: String,
    loggingIn: Boolean,
    message: String?,
    onCookieChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            text = "从已登录的网易云音乐网页端复制完整 Cookie（必须包含 MUSIC_U）粘贴到下方。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = cookie,
            onValueChange = onCookieChange,
            modifier = Modifier.fillMaxWidth().height(180.dp),
            label = { Text("Cookie") },
            placeholder = { Text("MUSIC_U=xxxx; __csrf=xxxx; ...") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        )
        LoginMessage(message)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !loggingIn,
        ) {
            if (loggingIn) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (loggingIn) "登录中…" else "使用 Cookie 登录")
        }
    }
}

@Composable
private fun LoginMessage(message: String?) {
    AnimatedVisibility(visible = message != null) {
        val text = message.orEmpty()
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
