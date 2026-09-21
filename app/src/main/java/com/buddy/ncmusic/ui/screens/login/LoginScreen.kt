package com.buddy.ncmusic.ui.screens.login

import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginScreen(onBack: () -> Unit, vm: LoginViewModel = viewModel()) {
    val qrBitmap by vm.qrBitmap.collectAsState()
    val qrStatus by vm.qrStatus.collectAsState()
    val phone by vm.phone.collectAsState()
    val password by vm.password.collectAsState()
    val captcha by vm.captcha.collectAsState()
    val useCaptcha by vm.useCaptcha.collectAsState()
    val showCookieDialog by vm.showCookieDialog.collectAsState()
    val cookieInput by vm.cookieInput.collectAsState()
    val countdown by vm.countdown.collectAsState()
    val loggingIn by vm.loggingIn.collectAsState()
    val message by vm.message.collectAsState()
    val success by vm.success.collectAsState()

    LaunchedEffect(success) {
        if (success) onBack()
    }

    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("登录", style = MaterialTheme.typography.titleMedium)
        }

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("扫码登录") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("手机号登录") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Cookie 登录") })
        }

        if (tab == 0) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))
                val bmp = qrBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier.size(220.dp),
                    )
                } else {
                    CircularProgressIndicator()
                }
                Spacer(Modifier.height(24.dp))
                Text(qrStatus, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = vm::startQrLogin) { Text("刷新二维码") }
            }
        } else if (tab == 1) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = vm::onPhoneChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("手机号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("登录方式", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { vm.setUseCaptcha(false) }) {
                        Text(
                            "密码登录",
                            color = if (!useCaptcha) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.setUseCaptcha(true) }) {
                        Text(
                            "验证码登录",
                            color = if (useCaptcha) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                if (!useCaptcha) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = vm::onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = captcha,
                            onValueChange = vm::onCaptchaChange,
                            modifier = Modifier.weight(1f),
                            label = { Text("验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = vm::sendCaptcha,
                            enabled = countdown == 0,
                        ) {
                            Text(if (countdown > 0) "${countdown}s" else "获取验证码")
                        }
                    }
                }

                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { if (useCaptcha) vm.captchaLogin() else vm.phoneLogin() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loggingIn,
                ) {
                    if (loggingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (useCaptcha) "验证码登录" else "登录")
                }

            }
        } else {
            // ---------- Cookie 登录 ----------
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "从已登录的网易云音乐网页端复制完整 Cookie 粘贴到下方（必须包含 MUSIC_U）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = cookieInput,
                    onValueChange = vm::onCookieChange,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    label = { Text("Cookie") },
                    placeholder = { Text("MUSIC_U=xxxx; __csrf=xxxx; ...") },
                )
                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { vm.loginWithCookie() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loggingIn,
                ) {
                    if (loggingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (loggingIn) "登录中…" else "登录")
                }
            }
        }
    }

    // Cookie 登录对话框
    if (showCookieDialog) {
        AlertDialog(
            onDismissRequest = { vm.setShowCookieDialog(false) },
            title = { Text("使用 Cookie 登录") },
            text = {
                Column {
                    Text(
                        text = "从浏览器或已登录的客户端复制完整 Cookie（必须包含 MUSIC_U），粘贴到下方即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = cookieInput,
                        onValueChange = vm::onCookieChange,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        label = { Text("Cookie") },
                        placeholder = { Text("MUSIC_U=xxxx; __csrf=xxxx; ...") },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.loginWithCookie() },
                    enabled = !loggingIn,
                ) { Text(if (loggingIn) "登录中…" else "登录") }
            },
            dismissButton = {
                TextButton(onClick = { vm.setShowCookieDialog(false) }) { Text("取消") }
            },
        )
    }
}
