package io.retroshelf

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebsiteReaderTest {
    @SuppressLint("SetJavaScriptEnabled")
    @Test fun readerChangesOnlyFixtureAppearanceAndCanRestoreOriginal() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ready=CountDownLatch(1)
        var view:WebView?=null
        var result=""
        val script=instrumentation.targetContext.assets.open("website-reader.js").bufferedReader().use { it.readText() }
        instrumentation.runOnMainSync {
            view=WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled=true
                settings.allowFileAccess=false
                settings.allowContentAccess=false
                webViewClient=object:WebViewClient() {
                    override fun onPageFinished(web:WebView,url:String?) {
                        if(url?.startsWith("https://steamrip.com/games-list-page/") != true) return
                        web.evaluateJavascript(script+"\nwindow.__retroShelfReader.setEnabled(true,true); document.body.classList.contains('rs-reader-on');") {
                            result=it; ready.countDown()
                        }
                    }
                }
                loadDataWithBaseURL("https://steamrip.com/games-list-page/", "<html><head></head><body><nav>Fixture navigation</nav><ul><li class='az-list-item'><a href='/fixture/'>Fixture game</a><span>Fixture metadata</span></li></ul></body></html>","text/html","UTF-8",null)
            }
        }
        try {
            assertTrue(ready.await(15,TimeUnit.SECONDS));assertEquals("true",result)
            val restored=CountDownLatch(1)
            instrumentation.runOnMainSync {
                view!!.evaluateJavascript("window.__retroShelfReader.setEnabled(false,true); document.body.classList.contains('rs-reader-on');") { result=it;restored.countDown() }
            }
            assertTrue(restored.await(10,TimeUnit.SECONDS));assertEquals("false",result)
        } finally { instrumentation.runOnMainSync { view?.destroy() } }
    }
}
