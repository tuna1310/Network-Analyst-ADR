package com.networkanalyst.app.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Lớp đại diện văn bản UI có khả năng mang tài nguyên đa ngôn ngữ (@StringRes)
 * hoặc chuỗi động không thể biên dịch trước (DynamicString).
 */
sealed class UiText {
    data class DynamicString(val value: String) : UiText()

    data class StringResource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText() {
        constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList())
    }

    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> stringResource(resId, *args.toTypedArray())
        }
    }

    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args.toTypedArray())
        }
    }
}
