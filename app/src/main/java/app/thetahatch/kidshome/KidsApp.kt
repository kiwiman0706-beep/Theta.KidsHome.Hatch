package app.thetahatch.kidshome

import android.app.Application
import app.thetahatch.kidshome.data.Store
import app.thetahatch.kidshome.rescue.Rescue

class KidsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 落ちたときの記録はいちばん最初に（レスキュー画面を出すかの判断に使う）
        Rescue.install(this)
        // レスキューのプロセス（":rescue"）では本体の準備をしない（本体が壊れていても開けるように）
        if (getProcessName().endsWith(":rescue")) return
        Store.init(this)
        app.thetahatch.kidshome.data.Widgets.init(this)
        app.thetahatch.kidshome.data.StampLibrary.init(this)
        app.thetahatch.kidshome.data.Atlas.init(this)
        app.thetahatch.kidshome.data.GuidePack.init(this)
        Rescue.watchMainThread(this)
    }
}
