package net.streamdek.mobile.nativeapp

import android.util.Log
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import com.lagradost.cloudstream3.utils.JsUnpacker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A plugin answered `{success: false}`, or threw. [code] is the plugin's own `errorCode`. */
class SkyPluginException(val code: String, message: String) : IllegalStateException(message)

/** Where a plugin's `getPreference`/`setPreference` and storage calls read and write. */
internal interface SkyPluginStorage {
  fun get(key: String): String?
  fun set(key: String, value: String?)
}

/**
 * Runs one entry point of a `.sky` bundle's `plugin.js` in QuickJS, with the host API the SkyStream
 * app gives its plugins.
 *
 * The contract is SkyStream's own (akashdh11/skystream, `js_engine_worker.dart` and
 * `js_based_provider.dart`), reimplemented rather than approximated: plugins are written and
 * tested against that app and nothing else, so every difference here is a plugin that works there
 * and fails here. In particular:
 *
 *  - `http_get`/`http_post` return promises of `{status, statusCode, code, body, headers,
 *    finalUrl}`, take an optional trailing callback, and `http_post(url, {headers, body})` is
 *    accepted. Scripts chain `.then` on them, so a plain object is not enough.
 *  - `MultimediaItem`, `Episode`, `StreamResult`, `Actor`, `Trailer` and `NextAiring` exist with the
 *    host's defaults. `load()` news up `Episode` for every title — without it every load threw.
 *  - `parseHtml`/`JSDOM` hand back a queryable document (jsoup here, `package:html` there);
 *    `crypto.decryptAES`/`pbkdf2`, `getAndUnpack`, `atob`/`btoa` and the `native*` helpers exist.
 *  - `getPreference` answers synchronously. The host's does too — it returns null there, so
 *    `getPreference(k) || default` is the idiom scripts use — and answering with the stored value
 *    keeps that idiom working while letting the settings a user saved actually reach the plugin.
 *  - An entry point answers through a trailing callback or by resolving its promise; whichever
 *    comes first wins, and a `{success, data}` reply is unwrapped the way the host unwraps it.
 */
internal object SkyStreamRuntime {
  private const val TAG = "SkyStreamRuntime"
  private const val MAX_BODY_BYTES = 16L * 1024 * 1024
  private const val MAX_DOM_NODES = 20_000

  /** The user agent the host sends when a plugin names none; CDNs tie signed URLs to it. */
  const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

  /**
   * Calls `globalThis[function](...args, callback)` and returns the unwrapped reply as JSON text
   * (`null` for a plugin that answered with nothing).
   *
   * @param manifestJson the plugin.json the script sees as `manifest` — with a sub-provider's
   * `providerId`/`baseUrl` already applied by the caller.
   * @param args JSON-compatible values: strings, numbers, maps, lists.
   */
  suspend fun invoke(
    bundle: SkyBundle,
    manifestJson: String,
    function: String,
    args: List<Any?>,
    storage: SkyPluginStorage,
    http: OkHttpClient,
    timeoutMs: Long,
    log: (String) -> Unit,
  ): String? = withContext(Dispatchers.IO) {
    withTimeout(timeoutMs) {
      val reply = CompletableDeferred<String>()
      val dom = DomRegistry()
      quickJs(Dispatchers.Default) {
        function("__sky_log") { values ->
          log(values.joinToString(" ") { it?.toString().orEmpty() }.take(2000))
          null
        }
        function("__sky_finished") { reply.isCompleted }
        function("__sky_reply") { values ->
          reply.complete(values.getOrNull(0)?.toString() ?: """{"ok":true,"value":null}""")
          null
        }
        function("__sky_send") { values ->
          val channel = values.getOrNull(0)?.toString().orEmpty()
          val payload = values.getOrNull(1)?.toString().orEmpty()
          runCatching { syncBridge(channel, payload, storage, dom) }
            .onFailure { Log.d(TAG, "sendMessage($channel) failed: ${it.message}") }
            .getOrNull()
        }
        asyncFunction("__sky_async") { values ->
          val channel = values.getOrNull(0)?.toString().orEmpty()
          val payload = values.getOrNull(1)?.toString().orEmpty()
          asyncBridge(channel, payload, storage, dom, http, reply)
        }
        // A timer that has not fired by the time the plugin answers fires at once instead: a
        // script that races its work against a 15s setTimeout would otherwise hold the reply for
        // the full 15s, since the runtime waits for every pending job before it returns.
        asyncFunction("__sky_sleep") { values ->
          val ms = (values.getOrNull(0) as? Number)?.toLong()?.coerceIn(0L, timeoutMs) ?: 0L
          if (ms > 0L) withTimeoutOrNull(ms) { reply.await() }
          null
        }

        // Each evaluate ends in `void 0`: a script's completion value is marshalled back to
        // Kotlin, and a bundle ends in `Object.assign(globalThis, …)`, whose globalThis refers to
        // itself through window/self — "circular reference" before the plugin is asked anything.
        evaluate<Any?>(HOST_SHIM + "\nglobalThis.manifest=" + manifestJson + ";void 0;")
        evaluate<Any?>(bundle.script + "\n;void 0;")
        evaluate<Any?>(
          INVOKE_DRIVER
            .replace("__SKY_FUNCTION__", JSONObject.quote(function))
            .replace("__SKY_ARGS__", JSONObject.quote(JSONArray(args).toString())),
        )
        reply.await()
      }
      unwrap(reply.await())
    }
  }

  /** `{ok, value}` from the driver, or `{ok:false, code, message}`. */
  private fun unwrap(raw: String): String? {
    val root = JSONObject(raw)
    if (!root.optBoolean("ok", false)) {
      throw SkyPluginException(
        root.optString("code").ifBlank { "UNKNOWN_ERROR" },
        root.optString("message").ifBlank { "The plugin failed without saying why." },
      )
    }
    if (root.isNull("value")) return null
    return when (val value = root.get("value")) {
      is JSONObject, is JSONArray -> value.toString()
      else -> JSONObject.quote(value.toString())
    }
  }

  // ── Bridges ──────────────────────────────────────────────────────────────────────────────────

  /** `sendMessage(channel, json)`: the host's synchronous channels. Answers JSON text or null. */
  private fun syncBridge(channel: String, payload: String, storage: SkyPluginStorage, dom: DomRegistry): String? = when (channel) {
    "base64_decode" -> JSONObject.quote(String(decodeBase64(payload), Charsets.UTF_8))
    "base64_encode" -> JSONObject.quote(Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8)))
    "crypto_md5" -> JSONObject.quote(digestHex("MD5", payload))
    "crypto_sha256" -> JSONObject.quote(digestHex("SHA-256", payload))
    "js_unpack" -> JSONObject.quote(runCatching { JsUnpacker(payload).unpack() }.getOrNull() ?: payload)
    "dom_query" -> {
      val args = JSONObject(payload)
      val node = dom.node(args.optString("nodeId"))
      val query = args.optString("query")
      when {
        node == null -> null
        args.optBoolean("multi") -> JSONArray().apply { node.select(query).forEach { put(dom.serialize(it)) } }.toString()
        else -> node.selectFirst(query)?.let { dom.serialize(it).toString() }
      }
    }
    "dom_query_batch" -> {
      val args = JSONObject(payload)
      val node = dom.node(args.optString("nodeId"))
      if (node == null) null else JSONArray().apply {
        val queries = args.optJSONArray("queries") ?: JSONArray()
        for (index in 0 until queries.length()) {
          val query = queries.optJSONObject(index) ?: JSONObject()
          val elements = node.select(query.optString("query").ifBlank { "*" })
          val attr = query.optString("attr").ifBlank { "textContent" }
          if (query.optBoolean("first")) put(elements.firstOrNull()?.let { attributeOf(it, attr) } ?: JSONObject.NULL)
          else put(JSONArray(elements.map { attributeOf(it, attr) }))
        }
      }.toString()
    }
    "dom_parse" -> JSONObject.quote(dom.parse(JSONObject(payload).optString("html")))
    "dom_dispose" -> { dom.dispose(payload); JSONObject.quote("OK") }
    "regex_match_all" -> {
      val args = JSONObject(payload)
      val options = if (args.optBoolean("caseSensitive", true)) emptySet() else setOf(RegexOption.IGNORE_CASE)
      val group = args.optInt("group", 0)
      JSONArray(Regex(args.optString("pattern"), options).findAll(args.optString("text")).mapNotNull { it.groups[group]?.value }.toList()).toString()
    }
    "json_extract" -> {
      val args = JSONObject(payload)
      val parsed = JSONArray("[" + args.optString("json", "{}") + "]").opt(0)
      JSONObject().apply {
        val paths = args.optJSONArray("paths") ?: JSONArray()
        for (index in 0 until paths.length()) {
          val path = paths.optString(index)
          put(path, extractJsonPath(parsed, path) ?: JSONObject.NULL)
        }
      }.toString()
    }
    "set_storage", "set_preference" -> {
      val args = JSONObject(payload)
      storage.set(args.optString("key"), if (args.isNull("value")) null else args.opt("value")?.toString())
      null
    }
    "get_preference", "get_storage" -> storage.get(JSONObject(payload).optString("key"))?.let(JSONObject::quote)
    else -> null
  }

  /** `_dartAsyncCall(channel, params)`: answers JSON text, or throws to reject the promise. */
  private suspend fun asyncBridge(
    channel: String,
    payload: String,
    storage: SkyPluginStorage,
    dom: DomRegistry,
    http: OkHttpClient,
    finished: CompletableDeferred<String>,
  ): String? {
    val args = runCatching { JSONObject(payload) }.getOrDefault(JSONObject())
    return when (channel) {
      "http_request" -> httpRequest(args, http, finished).toString()
      "http_parallel" -> coroutineScope {
        val requests = args.optJSONArray("requests") ?: JSONArray()
        val results = (0 until requests.length()).map { index ->
          async { httpRequest(requests.optJSONObject(index) ?: JSONObject(), http, finished) }
        }.awaitAll()
        JSONArray(results).toString()
      }
      "get_storage", "get_preference" -> storage.get(args.optString("key"))?.let(JSONObject::quote)
      "set_storage", "set_preference" -> {
        storage.set(args.optString("key"), if (args.isNull("value")) null else args.opt("value")?.toString())
        null
      }
      "dom_parse" -> JSONObject.quote(dom.parse(args.optString("html")))
      "parse_html" -> {
        val document = parseDocument(args.optString("html"))
        val attr = if (args.isNull("attr")) null else args.optString("attr").ifBlank { null }
        JSONArray().apply {
          document.select(args.optString("selector").ifBlank { "*" }).forEach { element ->
            put(JSONObject().put("text", element.wholeText()).put("html", element.html()).put("attr", attr?.let { attributeOf(element, it) }.orEmpty()))
          }
        }.toString()
      }
      "dom_parse_and_extract" -> {
        val document = parseDocument(args.optString("html"))
        val extract = args.optJSONObject("extract") ?: JSONObject()
        JSONObject().apply {
          extract.keys().forEach { key ->
            val spec = extract.optJSONObject(key) ?: JSONObject()
            val elements = document.select(spec.optString("query").ifBlank { "*" })
            val attr = spec.optString("attr").ifBlank { "textContent" }
            put(key, if (spec.optBoolean("first")) elements.firstOrNull()?.let { attributeOf(it, attr) } ?: JSONObject.NULL else JSONArray(elements.map { attributeOf(it, attr) }))
          }
        }.toString()
      }
      "crypto_decrypt_aes" -> JSONObject.quote(decryptAes(args))
      "crypto_pbkdf2" -> JSONObject.quote(pbkdf2(args))
      // SkyStream answers with a placeholder; there is no solver on either side.
      "solve_captcha" -> null
      else -> throw IllegalArgumentException("Unsupported host call: $channel")
    }
  }

  // ── HTTP ─────────────────────────────────────────────────────────────────────────────────────

  /**
   * One request, in the host's shape. A transport failure is a response with status 0, never a
   * rejection: plugins fire analytics beacons before scraping, and on a device with DNS filtering
   * those fail — throwing would take the whole provider down over a tracker.
   */
  private suspend fun httpRequest(args: JSONObject, http: OkHttpClient, finished: CompletableDeferred<String>): JSONObject {
    val url = args.optString("url").trim()
    val method = args.optString("method").ifBlank { "GET" }.uppercase()
    fun failure(message: String) = JSONObject()
      .put("code", 0).put("statusCode", 0).put("status", 0)
      .put("body", "").put("headers", JSONObject()).put("finalUrl", url).put("error", message)
    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return failure("Only HTTP(S) is allowed: $url")
    // Once the plugin has answered, anything still in flight is abandoned rather than awaited.
    if (finished.isCompleted) return failure("cancelled")

    val headers = linkedMapOf<String, String>()
    args.optJSONObject("headers")?.let { source ->
      source.keys().forEach { key ->
        // A script that passes its whole options object as headers ({headers: {...}, ...}) must not
        // turn that nested object into a header; the host drops non-string values the same way.
        when (val value = source.opt(key)) {
          is String, is Number, is Boolean -> if (key.isNotBlank()) headers[key] = value.toString()
        }
      }
    }
    // OkHttp decompresses transparently only when it negotiated the encoding itself.
    headers.keys.filter { it.equals("Accept-Encoding", true) }.forEach(headers::remove)
    if (headers.keys.none { it.equals("User-Agent", true) }) headers["User-Agent"] = BROWSER_USER_AGENT

    val requestBody = if (method == "GET" || method == "HEAD") null else requestBody(args.opt("body"), headers)
    val request = runCatching {
      Request.Builder().url(url).apply {
        headers.forEach { (key, value) -> runCatching { header(key, value) } }
        method(method, requestBody ?: if (method == "POST" || method == "PUT" || method == "PATCH") ByteArray(0).toRequestBody(null) else null)
      }.build()
    }.getOrElse { return failure(it.message.orEmpty()) }

    return try {
      val call = http.newCall(request)
      val response = coroutineScope {
        val pending = async { call.await() }
        val abandon = async { finished.await(); call.cancel() }
        try { pending.await() } finally { abandon.cancel() }
      }
      response.use {
        val responseHeaders = JSONObject()
        it.headers.names().forEach { name ->
          val key = name.lowercase()
          if (key == "set-cookie") responseHeaders.put(key, JSONArray(it.headers(name)))
          else responseHeaders.put(key, it.headers(name).joinToString(","))
        }
        val body = it.body.source().let { source ->
          source.request(MAX_BODY_BYTES)
          val bytes = source.buffer.let { buffer -> buffer.readByteArray(minOf(buffer.size, MAX_BODY_BYTES)) }
          String(bytes, it.body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
        }
        JSONObject()
          .put("code", it.code).put("statusCode", it.code).put("status", it.code)
          .put("body", body).put("headers", responseHeaders).put("finalUrl", it.request.url.toString())
      }
    } catch (failure: IOException) {
      failure(failure.message.orEmpty())
    }
  }

  /**
   * The body the host would send. A string goes as it is; an object is form-encoded unless the
   * request says JSON — dio's behaviour with the form content type the host defaults POSTs to.
   */
  private fun requestBody(body: Any?, headers: MutableMap<String, String>): okhttp3.RequestBody {
    val typeKey = headers.keys.firstOrNull { it.equals("Content-Type", true) }
    val contentType = typeKey?.let(headers::get)
    return when (body) {
      null, JSONObject.NULL -> ByteArray(0).toRequestBody(contentType?.toMediaTypeOrNull())
      is JSONObject -> if (contentType?.contains("json", true) == true) {
        body.toString().toRequestBody(contentType.toMediaTypeOrNull())
      } else {
        typeKey?.let(headers::remove)
        FormBody.Builder().apply { body.keys().forEach { key -> add(key, body.opt(key)?.toString().orEmpty()) } }.build()
      }
      is JSONArray -> body.toString().toRequestBody((contentType ?: "application/json").toMediaTypeOrNull())
      else -> {
        if (typeKey == null) headers["Content-Type"] = "application/x-www-form-urlencoded"
        body.toString().toRequestBody((contentType ?: "application/x-www-form-urlencoded").toMediaTypeOrNull())
      }
    }
  }

  private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
      override fun onResponse(call: Call, response: Response) { continuation.resume(response) { _ -> response.close() } }
      override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
    })
    continuation.invokeOnCancellation { runCatching { cancel() } }
  }

  // ── DOM ──────────────────────────────────────────────────────────────────────────────────────

  /**
   * Parsed documents and the elements handed out of them, by id, for one invocation.
   *
   * The host's `package:html` keeps markup as written; jsoup pretty-prints by default, which would
   * reflow the `innerHTML` scripts run their regexes over — so pretty printing is off.
   */
  private class DomRegistry {
    private val nodes = HashMap<String, Element>()
    private var counter = 0

    fun parse(html: String): String {
      if (nodes.size > MAX_DOM_NODES) nodes.clear()
      val id = "doc_${counter++}"
      nodes[id] = parseDocument(html)
      return id
    }

    fun node(id: String): Element? = nodes[id]

    fun dispose(id: String) { nodes.remove(id) }

    fun serialize(element: Element): JSONObject {
      if (nodes.size > MAX_DOM_NODES) nodes.entries.removeIf { !it.key.startsWith("doc_") }
      val id = "node_${counter++}"
      nodes[id] = element
      return JSONObject()
        .put("nodeId", id)
        .put("tagName", element.tagName())
        .put("attributes", JSONObject().apply { element.attributes().forEach { put(it.key, it.value) } })
        .put("textContent", element.wholeText())
        .put("innerHTML", element.html())
        .put("outerHTML", element.outerHtml())
    }
  }

  private fun parseDocument(html: String): Document =
    Jsoup.parse(html).apply { outputSettings().prettyPrint(false) }

  private fun attributeOf(element: Element, attr: String): String? = when (attr) {
    "textContent", "text" -> element.wholeText()
    "innerHTML" -> element.html()
    "outerHTML" -> element.outerHtml()
    "tagName" -> element.tagName()
    "className" -> element.className()
    else -> if (element.hasAttr(attr)) element.attr(attr) else null
  }

  // ── Crypto and friends ───────────────────────────────────────────────────────────────────────

  private fun decodeBase64(value: String): ByteArray {
    var clean = value.replace(Regex("\\s+"), "").replace('-', '+').replace('_', '/')
    while (clean.length % 4 != 0) clean += "="
    return Base64.getDecoder().decode(clean)
  }

  private fun digestHex(algorithm: String, input: String): String =
    MessageDigest.getInstance(algorithm).digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

  /** Base64 key, IV and ciphertext; CBC with PKCS#7 padding unless the call asks for GCM. */
  private fun decryptAes(args: JSONObject): String {
    val key = decodeBase64(args.optString("key"))
    val iv = decodeBase64(args.optString("iv"))
    val data = decodeBase64(args.optString("data"))
    val cipher = if (args.optString("mode").equals("gcm", true)) {
      Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }
    } else {
      Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
    }
    return String(cipher.doFinal(data), Charsets.UTF_8)
  }

  private fun pbkdf2(args: JSONObject): String {
    val spec = PBEKeySpec(
      args.optString("password").toCharArray(),
      decodeBase64(args.optString("salt")),
      args.optInt("iterations", 10_000),
      args.optInt("keyLength", 32) * 8,
    )
    val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    return Base64.getEncoder().encodeToString(derived)
  }

  /** `a.b[0].c` and `list[*].field` over parsed JSON, as the host's `nativeJsonExtract` reads them. */
  internal fun extractJsonPath(root: Any?, path: String): Any? {
    var current: Any? = root
    val parts = path.split('.')
    for ((index, part) in parts.withIndex()) {
      if (current == null || current == JSONObject.NULL) return null
      if (part.endsWith("[*]")) {
        val key = part.removeSuffix("[*]")
        if (key.isNotEmpty()) current = (current as? JSONObject)?.opt(key)
        val list = current as? JSONArray ?: return null
        val rest = parts.drop(index + 1).joinToString(".")
        if (rest.isEmpty()) return list
        return JSONArray((0 until list.length()).map { extractJsonPath(list.opt(it), rest) ?: JSONObject.NULL })
      }
      val indexed = Regex("""^(.+)\[(\d+)]$""").find(part)
      current = if (indexed != null) {
        ((current as? JSONObject)?.opt(indexed.groupValues[1]) as? JSONArray)?.opt(indexed.groupValues[2].toInt())
      } else {
        (current as? JSONObject)?.opt(part)
      }
    }
    return current.takeIf { it != JSONObject.NULL }
  }

  // ── JavaScript ───────────────────────────────────────────────────────────────────────────────

  /**
   * Calls the entry point and reports exactly once. A `{success, data}` reply is unwrapped the
   * way SkyStream unwraps it — any object reply without `success: true` is a failure — and
   * anything else (a list, a string) is the value itself.
   */
  private val INVOKE_DRIVER = """
    (function(){
      var name = __SKY_FUNCTION__;
      var args = JSON.parse(__SKY_ARGS__);
      var done = false;
      function send(o){
        if (done) return; done = true;
        var text;
        try { text = JSON.stringify(o); }
        catch (e) { text = JSON.stringify({ok:false, code:'BAD_REPLY', message:'Could not read the plugin reply: '+String(e&&e.message||e)}); }
        __sky_reply(text);
      }
      function fail(e){ send({ok:false, code:'JS_ERROR', message:String(e&&(e.message||e.stack)||e)}); }
      function answer(r){
        if (r === undefined || r === null) return send({ok:true, value:null});
        if (typeof r === 'object' && !Array.isArray(r)) {
          if (r.success === true) return send({ok:true, value: r.data === undefined ? null : r.data});
          return send({ok:false, code:String(r.errorCode||'UNKNOWN_ERROR'), message:String(r.message||r.error||'An unexpected plugin error occurred')});
        }
        send({ok:true, value:r});
      }
      try {
        var fn = globalThis[name];
        if (typeof fn !== 'function') throw new Error('Function ' + name + ' not found');
        args.push(answer);
        var r = fn.apply(null, args);
        if (r && typeof r.then === 'function') r.then(answer, fail);
        else if (r !== undefined) answer(r);
      } catch (e) { fail(e); }
    })();void 0;
  """.trimIndent()

  /**
   * The globals a SkyStream plugin expects of its host, over the two Kotlin bridges above.
   * See the class comment for why each one behaves the way it does.
   */
  private val HOST_SHIM = """
    (function(){
    var G = globalThis;
    G.window = G; G.self = G; G.global = G;
    function show(v){ if (typeof v === 'string') return v; try { return JSON.stringify(v); } catch (e) { return String(v); } }
    function logger(level){ return function(){ __sky_log(level + ': ' + [].slice.call(arguments).map(show).join(' ')); }; }
    G.console = { log: logger('log'), info: logger('info'), debug: logger('debug'), warn: logger('warn'), error: logger('error') };
    G.log = function(m){ G.console.log(m); };

    function decode(s){ if (s === null || s === undefined) return null; try { return JSON.parse(s); } catch (e) { return s; } }
    G.sendMessage = function(channel, payload){
      var text = typeof payload === 'string' ? payload : (payload === undefined || payload === null ? '' : JSON.stringify(payload));
      return decode(__sky_send(String(channel), text));
    };
    G._dartAsyncCall = function(channel, params){
      return __sky_async(String(channel), JSON.stringify(params || {})).then(decode);
    };

    function request(method, url, headers, body){
      if (method === 'POST' && headers && typeof headers === 'object' && !body && (headers.body || headers.headers)) {
        body = headers.body; headers = headers.headers;
      }
      return G._dartAsyncCall('http_request', { method: method, url: String(url), headers: headers || {}, body: body === undefined ? null : body });
    }
    G.http_get = function(url, headers, cb){
      return request('GET', url, headers, null).then(function(r){ if (typeof cb === 'function') cb(r); return r; });
    };
    G.http_post = function(url, headers, body, cb){
      return request('POST', url, headers, body).then(function(r){ if (typeof cb === 'function') cb(r); return r; });
    };
    G.http_parallel = function(requests){ return G._dartAsyncCall('http_parallel', { requests: requests || [] }); };
    G._fetch = function(url){ return G.http_get(url, {}); };
    G.getAndUnpack = function(js){ return G.sendMessage('js_unpack', String(js)); };
    G.parse_html = function(html, selector, attr){ return G._dartAsyncCall('parse_html', { html: html, selector: selector, attr: attr }); };
    G.solveCaptcha = function(siteKey, url){ return G._dartAsyncCall('solve_captcha', { siteKey: siteKey, url: url || '' }); };

    G.getPreference = function(key){ return G.sendMessage('get_preference', JSON.stringify({ key: String(key) })); };
    G.setPreference = function(key, value){
      G.sendMessage('set_preference', JSON.stringify({ key: String(key), value: value === undefined || value === null ? null : String(value) }));
    };

    var timers = {}, timerSeq = 0;
    G.setTimeout = function(fn, ms){
      var extra = [].slice.call(arguments, 2), id = 't' + (++timerSeq);
      timers[id] = true;
      __sky_sleep(Number(ms) || 0).then(function(){
        if (!timers[id]) return; delete timers[id];
        try { if (typeof fn === 'function') fn.apply(null, extra); } catch (e) { G.console.error('Timeout error: ' + e); }
      });
      return id;
    };
    G.clearTimeout = function(id){ if (id) delete timers[id]; };
    G.setInterval = function(fn, ms){
      var id = 'i' + (++timerSeq);
      timers[id] = true;
      (function tick(){
        __sky_sleep(Math.max(Number(ms) || 0, 10)).then(function(){
          if (!timers[id] || __sky_finished()) { delete timers[id]; return; }
          try { fn(); } catch (e) { G.console.error('Interval error: ' + e); }
          tick();
        });
      })();
      return id;
    };
    G.clearInterval = G.clearTimeout;

    function assign(target, source){
      if (source && typeof source === 'object') for (var k in source) if (Object.prototype.hasOwnProperty.call(source, k)) target[k] = source[k];
      return target;
    }
    class Actor { constructor(p){ assign(this, p); } }
    class Trailer { constructor(p){ assign(this, p); } }
    class NextAiring { constructor(p){ assign(this, p); } }
    class MultimediaItem {
      // SkyStream defaults type to 'movie'. Left unset here so an item that never said what it is
      // reaches the search walk untyped, where it can match a film or a series.
      constructor(p){ assign(this, { status: 'ongoing', playbackPolicy: 'none', isAdult: false, streams: [], syncData: {} }); assign(this, p); }
    }
    class Episode {
      constructor(p){ assign(this, { season: 0, episode: 0, dubStatus: 'none', playbackPolicy: 'none', streams: [] }); assign(this, p); }
    }
    class StreamResult {
      constructor(p){
        p = p || {};
        assign(this, p);
        this.url = p.url; this.source = p.source || 'Auto'; this.headers = p.headers;
        this.subtitles = p.subtitles; this.drmKid = p.drmKid; this.drmKey = p.drmKey; this.licenseUrl = p.licenseUrl;
      }
    }
    G.Actor = Actor; G.Trailer = Trailer; G.NextAiring = NextAiring;
    G.MultimediaItem = MultimediaItem; G.Episode = Episode; G.StreamResult = StreamResult;
    G.CloudStream = { getLanguage: function(){ return 'en'; }, getRegion: function(){ return 'US'; } };

    if (typeof G.crypto === 'undefined') G.crypto = {};
    G.crypto.decryptAES = function(data, key, iv, options){
      return G._dartAsyncCall('crypto_decrypt_aes', { data: data, key: key, iv: iv, mode: (options && options.mode) || 'cbc' });
    };
    G.crypto.pbkdf2 = function(password, salt, iterations, keyLength){
      return G._dartAsyncCall('crypto_pbkdf2', { password: password, salt: salt, iterations: iterations || 10000, keyLength: keyLength || 32 });
    };

    class JSNode {
      constructor(nodeId, data){
        this.nodeId = nodeId; this.data = data || {};
        this.textContent = this.data.textContent || '';
        this.innerHTML = this.data.innerHTML || '';
        this.outerHTML = this.data.outerHTML || '';
        this.tagName = this.data.tagName || '';
      }
      get className(){ return this.getAttribute('class') || ''; }
      get innerText(){ return this.textContent; }
      getAttribute(name){ var a = this.data.attributes; return a && Object.prototype.hasOwnProperty.call(a, name) ? a[name] : null; }
      hasAttribute(name){ return this.getAttribute(name) !== null; }
      querySelector(query){
        var r = G.sendMessage('dom_query', JSON.stringify({ nodeId: this.nodeId, query: query, multi: false }));
        return r ? new JSNode(r.nodeId, r) : null;
      }
      querySelectorAll(query){
        var r = G.sendMessage('dom_query', JSON.stringify({ nodeId: this.nodeId, query: query, multi: true }));
        return (r || []).map(function(d){ return new JSNode(d.nodeId, d); });
      }
    }
    class JSDocument extends JSNode {
      constructor(id){ super(id, { nodeId: id }); }
      get body(){ return this.querySelector('body'); }
      get documentElement(){ return this.querySelector('html'); }
      get title(){ var t = this.querySelector('title'); return t ? t.textContent : ''; }
    }
    G.JSDOM = class JSDOM {
      constructor(html){
        var id = G.sendMessage('dom_parse', JSON.stringify({ html: String(html === undefined || html === null ? '' : html) }));
        this.window = { document: new JSDocument(id) };
        this._initPromise = Promise.resolve(this);
      }
      waitForInit(){ return this._initPromise; }
    };
    G.parseHtml = function(html){ return Promise.resolve(new G.JSDOM(html).window.document); };

    G.nativeDomBatch = function(nodeId, queries){ return G.sendMessage('dom_query_batch', JSON.stringify({ nodeId: nodeId, queries: queries })) || []; };
    G.nativeExtract = function(html, extraction){ return G._dartAsyncCall('dom_parse_and_extract', { html: html, extract: extraction }); };
    G.nativeRegex = function(text, pattern, group, caseSensitive){
      return G.sendMessage('regex_match_all', JSON.stringify({ text: text, pattern: pattern, group: group || 0, caseSensitive: caseSensitive !== false })) || [];
    };
    G.nativeJsonExtract = function(json, paths){ return G.sendMessage('json_extract', JSON.stringify({ json: json, paths: paths })) || {}; };
    G.nativeMd5 = function(input){ return G.sendMessage('crypto_md5', String(input)) || ''; };
    G.nativeSha256 = function(input){ return G.sendMessage('crypto_sha256', String(input)) || ''; };

    var B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    if (typeof G.btoa !== 'function') G.btoa = function(input){
      var s = String(input), out = '', i = 0;
      while (i < s.length) {
        var a = s.charCodeAt(i++), b = s.charCodeAt(i++), c = s.charCodeAt(i++);
        if (a > 255 || b > 255 || c > 255) throw new Error("'btoa' failed: not a binary string");
        var n = (a << 16) | ((b || 0) << 8) | (c || 0);
        out += B64.charAt(n >> 18 & 63) + B64.charAt(n >> 12 & 63) +
          (isNaN(b) ? '=' : B64.charAt(n >> 6 & 63)) + (isNaN(c) ? '=' : B64.charAt(n & 63));
      }
      return out;
    };
    if (typeof G.atob !== 'function') G.atob = function(input){
      var s = String(input).replace(/[\s=]+/g, '').replace(/-/g, '+').replace(/_/g, '/'), out = '', bits = 0, value = 0;
      for (var i = 0; i < s.length; i++) {
        var idx = B64.indexOf(s.charAt(i));
        if (idx < 0) continue;
        value = (value << 6) | idx; bits += 6;
        if (bits >= 8) { bits -= 8; out += String.fromCharCode((value >> bits) & 255); }
      }
      return out;
    };

    // QuickJS ships no URL/URLSearchParams, and scrapers reach for them constantly to join a
    // relative href onto a base or pull a query value out. This covers parsing, relative resolution
    // and the query accessors; it is not the full WHATWG algorithm.
    if (typeof G.URLSearchParams !== 'function') {
      var SP = function(init){
        this._p = [];
        if (typeof init === 'string') {
          init.replace(/^\?/, '').split('&').forEach(function(kv){
            if (!kv) return;
            var i = kv.indexOf('=');
            var k = i < 0 ? kv : kv.slice(0, i), v = i < 0 ? '' : kv.slice(i + 1);
            try { this._p.push([decodeURIComponent(k.replace(/\+/g, ' ')), decodeURIComponent(v.replace(/\+/g, ' '))]); }
            catch (e) { this._p.push([k, v]); }
          }, this);
        } else if (init && typeof init === 'object') {
          for (var k in init) if (Object.prototype.hasOwnProperty.call(init, k)) this._p.push([k, String(init[k])]);
        }
      };
      SP.prototype.get = function(k){ for (var i = 0; i < this._p.length; i++) if (this._p[i][0] === k) return this._p[i][1]; return null; };
      SP.prototype.getAll = function(k){ return this._p.filter(function(p){ return p[0] === k; }).map(function(p){ return p[1]; }); };
      SP.prototype.has = function(k){ return this.get(k) !== null; };
      SP.prototype.append = function(k, v){ this._p.push([k, String(v)]); };
      SP.prototype.delete = function(k){ this._p = this._p.filter(function(p){ return p[0] !== k; }); };
      SP.prototype.set = function(k, v){ this.delete(k); this.append(k, v); };
      SP.prototype.forEach = function(f, t){ var s = this; this._p.slice().forEach(function(p){ f.call(t, p[1], p[0], s); }); };
      SP.prototype.entries = function(){ return this._p.map(function(p){ return [p[0], p[1]]; })[Symbol.iterator](); };
      SP.prototype[Symbol.iterator] = SP.prototype.entries;
      SP.prototype.toString = function(){ return this._p.map(function(p){ return encodeURIComponent(p[0]) + '=' + encodeURIComponent(p[1]); }).join('&'); };
      G.URLSearchParams = SP;
    }
    if (typeof G.URL !== 'function') {
      var resolve = function(input, base){
        input = String(input === undefined || input === null ? '' : input);
        if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(input)) return input;
        if (base === undefined || base === null) throw new TypeError('Invalid URL: ' + input);
        var b = String(base);
        var m = b.match(/^([a-zA-Z][a-zA-Z0-9+.-]*:)\/\/([^\/?#]*)([^?#]*)/);
        if (!m) throw new TypeError('Invalid base URL: ' + base);
        var origin = m[1] + '//' + m[2];
        if (input.indexOf('//') === 0) return m[1] + input;
        if (input.charAt(0) === '/') return origin + input;
        if (input.charAt(0) === '?') return origin + (m[3] || '/') + input;
        if (input.charAt(0) === '#') return b.split('#')[0] + input;
        return origin + (m[3] || '/').replace(/[^\/]*$/, '') + input;
      };
      var U = function(input, base){
        var href = resolve(input, base);
        var m = href.match(/^([a-zA-Z][a-zA-Z0-9+.-]*:)\/\/([^\/?#]*)([^?#]*)(\?[^#]*)?(#.*)?$/);
        if (!m) throw new TypeError('Invalid URL: ' + input);
        this.protocol = m[1]; this.host = m[2]; this.pathname = m[3] || '/';
        this.search = m[4] || ''; this.hash = m[5] || ''; this.href = href;
        var hostPort = m[2].split('@').pop(), colon = hostPort.lastIndexOf(':');
        this.hostname = colon > 0 ? hostPort.slice(0, colon) : hostPort;
        this.port = colon > 0 ? hostPort.slice(colon + 1) : '';
        this.origin = this.protocol + '//' + this.host;
        this.searchParams = new G.URLSearchParams(this.search);
      };
      U.prototype.toString = function(){ return this.href; };
      U.prototype.toJSON = function(){ return this.href; };
      G.URL = U;
    }
    })();void 0;
  """.trimIndent()
}
