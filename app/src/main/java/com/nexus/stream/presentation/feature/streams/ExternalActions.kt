package com.nexus.stream.presentation.feature.streams

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nexus.stream.R

object ExternalActions {
    fun openVideo(
        context: Context,
        url: String,
        title: String,
        headers: Map<String, String>,
        onError: (Int) -> Unit,
    ) {
        try {
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                    putExtra("title", title)
                    if (headers.isNotEmpty()) {
                        val headerList = mutableListOf<String>()
                        headers.forEach { (k, v) ->
                            headerList.add(k)
                            headerList.add(v)
                        }
                        putExtra("headers", headerList.toTypedArray())
                    }
                }
            val chooser =
                Intent.createChooser(intent, title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            onError(R.string.streams_no_video_player)
        }
    }

    fun openMagnet(
        context: Context,
        magnet: String,
        onError: (Int) -> Unit,
    ) {
        try {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(magnet)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val chooser =
                Intent.createChooser(intent, null).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            onError(R.string.streams_no_magnet_app)
        }
    }

    fun openLink(
        context: Context,
        url: String,
        onError: (Int) -> Unit,
    ) {
        try {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val chooser =
                Intent.createChooser(intent, null).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            onError(R.string.streams_no_browser)
        }
    }

    fun copy(
        context: Context,
        text: String,
        onSuccess: (Int) -> Unit,
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("stream", text)
        clipboard?.setPrimaryClip(clip)
        onSuccess(R.string.streams_copied)
    }
}
