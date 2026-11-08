package io.retroshelf

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView

private class WebsiteSession(val saved: Bundle = Bundle()) {
    var view: WebView? = null
    fun snapshot(): Bundle = saved.also { view?.saveState(it) }
}
private val WebsiteSessionSaver = Saver<WebsiteSession, Bundle>(save = { it.snapshot() }, restore = { WebsiteSession(it) })

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebsiteScreen(provider: GameProvider) {
    val context = LocalContext.current
    if(provider.nativeBrowsing || !WebsitePolicy.embedded(provider.id, provider.website)) {
        Text("This source does not support the embedded website view.", Modifier.padding(24.dp)); return
    }
    val available = remember { runCatching { WebView.getCurrentWebViewPackage() != null }.getOrDefault(false) }
    if(!available) {
        Column(Modifier.padding(24.dp)) {
            Text("Android System WebView is unavailable. Open this website in your browser.")
            TextButton(onClick = { openWebsite(context, provider.website) }) { Text("Open in browser") }
        }
        return
    }
    val session = rememberSaveable(provider.id, saver = WebsiteSessionSaver) { WebsiteSession() }
    var reader by rememberSaveable(provider.id) { mutableStateOf(true) }
    var address by rememberSaveable(provider.id) { mutableStateOf(provider.website) }
    var back by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Loading original website…") }
    val script = remember(context) { context.assets.open("website-reader.js").bufferedReader().use { it.readText() } }
    val currentReader = rememberUpdatedState(reader)
    val currentDark = rememberUpdatedState(MaterialTheme.colorScheme.background.luminance() < 0.5f)
    fun browser(url: String) { openWebsite(context, url) }
    fun apply(view: WebView) {
        if(!WebsitePolicy.embedded(provider.id, view.url ?: return)) return
        view.evaluateJavascript(script + "\nwindow.__retroShelfReader.setEnabled(${currentReader.value},${currentDark.value});") { result ->
            status = if(result == "\"reader\"") "Names + pictures · current page only" else if(currentReader.value) "Original page · reader unavailable for this layout" else "Original website"
        }
    }
    LaunchedEffect(reader, currentDark.value) { session.view?.let(::apply) }
    BackHandler(enabled = back) { session.view?.goBack() }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(provider.name, Modifier.weight(1f).padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FilterChip(selected = reader, onClick = { reader = !reader }, label = { Text("Names + pics") })
            }
            Text(Uri.parse(address).host.orEmpty(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Row {
                IconButton(onClick = { session.view?.goBack() }, enabled = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Previous webpage") }
                IconButton(onClick = { error = null; session.view?.reload() }) { Icon(Icons.Default.Refresh,"Reload webpage") }
                TextButton(onClick = { browser(address) }) { Text("Browser") }
                if(provider.id == "steamrip") TextButton(onClick = { session.view?.loadUrl("https://steamrip.com/") }) { Text("Latest games") }
            }
            Text(error ?: status, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if(error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Only existing page images are shown. Toggle Names + pics off for the original page.", style = MaterialTheme.typography.bodySmall)
        }
        if(progress in 0..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { ctx ->
            WebView(ctx).apply {
                session.view = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.safeBrowsingEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, value: Int) { progress = value }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if(!request.isForMainFrame) return false
                        val target = request.url.toString()
                        if(WebsitePolicy.embedded(provider.id,target)) return false
                        if(request.hasGesture() && UrlSafety.valid(target)) browser(target)
                        else { error = "Use Browser to follow this website link."; progress = 100 }
                        return true
                    }
                    override fun onPageStarted(view: WebView, url: String?, icon: android.graphics.Bitmap?) {
                        error = null; progress = 0; status = "Loading original website…"; address = url ?: address; back = view.canGoBack()
                    }
                    override fun onPageFinished(view: WebView, url: String?) {
                        address = url ?: address; back = view.canGoBack(); progress = 100; apply(view)
                    }
                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        address = url ?: address; back = view.canGoBack()
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                        if(request.isForMainFrame) { error = "Could not load this page. Retry or open Browser."; progress = 100 }
                    }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if(request.isForMainFrame) error = "Website returned HTTP ${response.statusCode}."
                    }
                }
                setDownloadListener { _, _, _, _, _ -> Toast.makeText(ctx,"Use Browser to download from the original website",Toast.LENGTH_LONG).show() }
                if(restoreState(session.saved) == null) loadUrl(provider.website)
            }
        }, onRelease = { view ->
            view.saveState(session.saved); session.view = null; view.stopLoading(); view.destroy()
        }, update = {})
    }
}
