package com.theta.kidshome.tepra

import android.content.Context
import android.graphics.Bitmap

/** SDK が ない ビルド：テプラ は つかわず、ラベル は「共有」で */
object TepraImpl : TepraBridge {
    override val available = false
    override fun startDiscover(context: Context, onFound: (Map<String, String>) -> Unit) = false
    override fun stopDiscover() {}
    override fun print(context: Context, printer: Map<String, String>, copies: Int, render: (Int, Int) -> Bitmap, onStatus: (String, Boolean, Boolean) -> Unit) =
        onStatus("この版 には テプラ の 印刷 が 入って いません（共有 で 印刷 して ね）", true, false)
}
