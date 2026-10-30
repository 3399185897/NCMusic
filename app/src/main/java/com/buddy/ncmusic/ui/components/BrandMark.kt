package com.buddy.ncmusic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.R

/** 云音统一的品牌符号。 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onPrimary,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(R.drawable.ic_brand_mark),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

/** 带 MD3 容器底色的品牌图标块，用于启动页、登录页和关于页。 */
@Composable
fun BrandMarkTile(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            BrandMark(
                modifier = Modifier.size(size * 0.64f),
                tint = contentColor,
                contentDescription = "云音",
            )
        }
    }
}
