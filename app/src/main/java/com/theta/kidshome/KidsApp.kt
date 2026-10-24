package com.theta.kidshome

import android.app.Application
import com.theta.kidshome.data.Store
import com.theta.kidshome.rescue.Rescue

class KidsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 落ちたときの記録はいちばん最初に（レスキュー画面を出すかの判断に使う）
        Rescue.install(this)
        // レスキューのプロセス（":rescue"）では本体の準備をしない（本体が壊れていても開けるように）
        if (getProcessName().endsWith(":rescue")) return
        Store.init(this)
        Rescue.watchMainThread(this)
    }
}
