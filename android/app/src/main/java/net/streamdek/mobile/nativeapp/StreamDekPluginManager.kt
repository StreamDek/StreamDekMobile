package net.streamdek.mobile.nativeapp

import android.content.Context
import android.util.Log
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * How many plugin providers may be scraping at once.
 *
 * Kept at the previous value so the move onto the IO pool could be measured on its own; the
 * fan-out width is tuned separately against a device, not guessed at.
 */
private const val MAX_CONCURRENT_PLUGIN_PROVIDERS = 5

data class PluginRepo(val url: String, val name: String, val version: String, val description: String?, val enabled: Boolean = true, val favourite: Boolean = false)
data class PluginProvider(
  val id: String,
  val repoUrl: String,
  val name: String,
  val types: List<String>,
  val enabled: Boolean,
  val code: String,
  val hasSettings: Boolean = false,
)
data class PluginState(val enabled: Boolean = true, val repos: List<PluginRepo> = emptyList(), val providers: List<PluginProvider> = emptyList(), val updatedAt: Long = 0L)
data class PluginTestMedia(val label: String, val id: String, val type: String, val season: Int? = null, val episode: Int? = null)

data class PluginSettingOption(val label: String, val value: String)
data class PluginSettingField(
  val type: String,
  val key: String? = null,
  val label: String,
  val description: String? = null,
  val placeholder: String? = null,
  val defaultValue: Any? = null,
  val isPassword: Boolean = false,
  val options: List<PluginSettingOption> = emptyList(),
)
/** Reads a settings schema, whether it came from onSettings() or from a collection manifest. */
internal fun parsePluginSettingFields(array: JSONArray): List<PluginSettingField> = buildList {
  for (index in 0 until array.length()) {
    val item = array.optJSONObject(index) ?: continue
    val options = item.optJSONArray("options")
    add(PluginSettingField(
      // Anything unrecognised is treated as a text field rather than skipped. A manifest that says
      // "string" or "password" instead of "text" would otherwise declare a field that renders as
      // nothing at all, which looks identical to a source with no settings at all.
      type = item.optString("type").lowercase().ifBlank { "text" },
      key = item.optString("key").ifBlank { null },
      label = item.optString("label").ifBlank { item.optString("key") },
      description = item.optString("description").ifBlank { null },
      placeholder = item.optString("placeholder").ifBlank { null },
      defaultValue = item.opt("defaultValue")?.takeUnless { it == JSONObject.NULL },
      isPassword = item.optBoolean("isPassword", false),
      options = buildList {
        if (options != null) for (optionIndex in 0 until options.length()) {
          val option = options.optJSONObject(optionIndex) ?: continue
          add(PluginSettingOption(option.optString("label"), option.optString("value")))
        }
      },
    ))
  }
}

internal fun humanReadablePluginError(error: Throwable): String {
  if (error is TimeoutCancellationException) return "This source took too long to respond. It may be offline or blocked on this network."
  val raw = error.message.orEmpty().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
  return when {
    raw.contains("HTTP 404", true) || raw.contains("Request failed: 404", true) ->
      "A file referenced by this plugin collection could not be found (HTTP 404)."
    raw.contains("SyntaxError", true) || raw.contains("Unexpected identifier", true) ->
      "This source uses JavaScript syntax that could not be loaded. Refresh the collection and try again."
    raw.contains("regexp pattern", true) ->
      "This source uses a matching pattern that this version of StreamDek cannot run. Try refreshing the plugin collection."
    raw.contains("Module not available", true) ->
      "This source needs a JavaScript module that StreamDek does not support yet."
    raw.contains("does not export", true) ->
      "This source is missing the function StreamDek needs to run it."
    raw.isBlank() || raw.startsWith("at ") -> "The plugin could not complete this request."
    else -> raw.replace(Regex("\\s+at\\s+.*$"), "").take(240)
  }
}

internal fun normalizePluginRepositoryUrl(raw: String): String {
  var value = raw.trim()
  if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) value = "https://$value"
  val uri = URI(value)
  require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "Invalid repository URL." }
  return value
}

internal fun pluginRepositoryUrlCandidates(url: String): List<String> {
  val normalized = normalizePluginRepositoryUrl(url)
  val uri = URI(normalized)
  val lastSegment = uri.path.orEmpty().substringAfterLast('/')
  val looksDirectoryLike = uri.path.orEmpty().endsWith('/') || !lastSegment.contains('.')
  if (!looksDirectoryLike) return listOf(normalized)
  val fallbackPath = uri.path.orEmpty().trimEnd('/') + "/manifest.json"
  val fallback = URI(uri.scheme, uri.userInfo, uri.host, uri.port, fallbackPath, uri.query, uri.fragment).toString()
  return listOf(normalized, fallback).distinct()
}

internal fun resolvePluginProviderUrl(repositoryUrl: String, filename: String): String {
  val trimmed = filename.trim()
  require(trimmed.isNotBlank()) { "Provider filename is missing." }
  if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) return trimmed
  val manifest = URI(repositoryUrl)
  val resolved = manifest.resolve(trimmed.trimStart('/'))
  if (resolved.query != null || manifest.query == null) return resolved.toString()
  return URI(resolved.scheme, resolved.userInfo, resolved.host, resolved.port, resolved.path, manifest.query, resolved.fragment).toString()
}

/**
 * Whether a source exports `onSettings`, whatever its manifest claims.
 *
 * `hasSettings` is advisory and collections forget it. A source that needs an API token or a
 * cookie but is listed without the flag leaves nowhere to type one in, and the only symptom is a
 * source that returns no streams -- so trust the code over the listing.
 */
internal fun pluginDeclaresSettings(code: String): Boolean =
  Regex("""(?:\bfunction\s+onSettings\b)|(?:\bonSettings\s*[:=])""").containsMatchIn(code)

internal fun normalizePluginJavaScript(source: String): String {
  var normalized = source
  normalized = normalized.replace(
    Regex("(?m)^\\s*import\\s+([A-Za-z_$][\\w$]*)\\s+from\\s+(['\"][^'\"]+['\"])\\s*;?\\s*$"),
    "const $1 = require($2);",
  )
  normalized = normalized.replace(
    Regex("(?m)^\\s*import\\s+\\*\\s+as\\s+([A-Za-z_$][\\w$]*)\\s+from\\s+(['\"][^'\"]+['\"])\\s*;?\\s*$"),
    "const $1 = require($2);",
  )
  normalized = normalized.replace(
    Regex("(?m)^\\s*import\\s+\\{([^}]+)\\}\\s+from\\s+(['\"][^'\"]+['\"])\\s*;?\\s*$"),
  ) { match ->
    val bindings = match.groupValues[1].split(',').joinToString(",") { binding ->
      val parts = binding.trim().split(Regex("\\s+as\\s+"), limit = 2)
      if (parts.size == 2) "${parts[0]}: ${parts[1]}" else parts[0]
    }
    "const {$bindings} = require(${match.groupValues[2]});"
  }
  normalized = normalized.replace(Regex("(?m)^\\s*export\\s+\\{([^}]+)\\}\\s*;?\\s*$")) { match ->
    val bindings = match.groupValues[1].split(',').joinToString(",") { binding ->
      val parts = binding.trim().split(Regex("\\s+as\\s+"), limit = 2)
      if (parts.size == 2) "${parts[1]}: ${parts[0]}" else parts[0]
    }
    "module.exports = {$bindings};"
  }
  normalized = normalized.replace(Regex("(?m)^\\s*export\\s+default\\s+"), "module.exports.default = ")
  return normalized
}
private val CHEERIO_COMPAT_SHIM = """
  function __sdIds(raw){try{return JSON.parse(raw||'[]')}catch(e){return []}}
  function __sdToken(id){return {__sd_node:Number(id)}}
  function __sdWrap(ids){
    ids=(ids||[]).map(Number).filter(function(id){return id>0});
    var api={__sd_ids:ids};
    Object.defineProperty(api,'length',{get:function(){return ids.length}});
    api.get=function(index){if(index===undefined)return ids.map(__sdToken);var i=Number(index);if(i<0)i=ids.length+i;return i>=0&&i<ids.length?__sdToken(ids[i]):undefined};
    api.toArray=function(){return api.get()};
    api.first=function(){return __sdWrap(ids.length?[ids[0]]:[])};
    api.last=function(){return __sdWrap(ids.length?[ids[ids.length-1]]:[])};
    api.eq=function(index){var item=api.get(index);return __sdWrap(item?[item.__sd_node]:[])};
    api.find=function(selector){var out=[];ids.forEach(function(id){out=out.concat(__sdIds(__sd_dom_select(id,String(selector))))});return __sdWrap(out.filter(function(id,index,list){return list.indexOf(id)===index}))};
    api.filter=function(test){if(typeof test==='function')return __sdWrap(ids.filter(function(id,index){return !!test(index,__sdToken(id))}));return __sdWrap(ids.filter(function(id){return !!__sd_dom_matches(id,String(test))}))};
    api.each=function(callback){ids.forEach(function(id,index){callback(index,__sdToken(id))});return api};
    api.map=function(callback){var values=[];ids.forEach(function(id,index){var value=callback(index,__sdToken(id));if(value!==undefined&&value!==null)values.push(value)});return {get:function(){return values},toArray:function(){return values}}};
    api.attr=function(name){return ids.length?__sd_dom_attr(ids[0],String(name)):undefined};
    api.text=function(){return ids.map(function(id){return __sd_dom_text(id)}).join('')};
    api.html=function(){return ids.length?__sd_dom_html(ids[0]):null};
    api.children=function(selector){var out=[];ids.forEach(function(id){out=out.concat(__sdIds(__sd_dom_children(id)))});var result=__sdWrap(out);return selector?result.filter(selector):result};
    api.parent=function(){var out=[];ids.forEach(function(id){var parent=Number(__sd_dom_parent(id));if(parent>0&&out.indexOf(parent)<0)out.push(parent)});return __sdWrap(out)};
    return api;
  }
  function __sdCheerioLoad(html){
    var root=Number(__sd_dom_load(String(html||'')));
    function query(selector,context){
      if(selector&&selector.__sd_node)return __sdWrap([selector.__sd_node]);
      if(selector&&selector.__sd_ids)return selector;
      var contexts=context?(context.__sd_ids||[context.__sd_node||Number(context)]):[root];
      var out=[];contexts.forEach(function(id){out=out.concat(__sdIds(__sd_dom_select(id,String(selector||'*'))))});
      return __sdWrap(out.filter(function(id,index,list){return id>0&&list.indexOf(id)===index}));
    }
    query.root=function(){return __sdWrap([root])};
    return query;
  }
  var __sd_cheerio={load:__sdCheerioLoad};
  function __sdB64Encode(bytes){var chars='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';var out='';for(var i=0;i<bytes.length;i+=3){var a=bytes[i]&255,b=i+1<bytes.length?bytes[i+1]&255:0,c=i+2<bytes.length?bytes[i+2]&255:0;var n=(a<<16)|(b<<8)|c;out+=chars[(n>>18)&63]+chars[(n>>12)&63]+(i+1<bytes.length?chars[(n>>6)&63]:'=')+(i+2<bytes.length?chars[n&63]:'=')}return out}
  function __sdB64Bytes(value){
    var chars='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';var clean=String(value||'').replace(/[^A-Za-z0-9+/]/g,'');var out=[];var buffer=0,bits=0;
    for(var i=0;i<clean.length;i++){var n=chars.indexOf(clean.charAt(i));if(n<0)continue;buffer=(buffer<<6)|n;bits+=6;if(bits>=8){bits-=8;out.push((buffer>>bits)&255)}}return out;
  }
  function __sdBytesToWords(bytes){var words=[];for(var i=0;i<bytes.length;i++)words[i>>>2]=(words[i>>>2]||0)|(bytes[i]<<(24-(i%4)*8));return words}
  function __sdWordsToBytes(words,count){var out=[];for(var i=0;i<count;i++)out.push((words[i>>>2]>>>(24-(i%4)*8))&255);return out}
""".trimIndent()

/**
 * What FebBox and the other hosts these sources scrape expect to see. The old "StreamDek/1.0"
 * was enough for a bot filter to answer with a challenge page instead of JSON, which reaches the
 * provider as an unparseable body and leaves it reporting no streams rather than an error.
 */
private const val PLUGIN_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/** The plugin sandbox: DOM and crypto shims, a browser-ish global scope, and the module loader. */
private val PLUGIN_RUNTIME_SOURCE = CHEERIO_COMPAT_SHIM + "\n" + PLUGIN_POLYFILLS + "\n" + "globalThis.window=globalThis;globalThis.self=globalThis;globalThis.global=globalThis;globalThis.console={log:function(){__sd_log([].slice.call(arguments).map(String).join(' '))},warn:function(){__sd_log([].slice.call(arguments).map(String).join(' '))},info:function(){__sd_log([].slice.call(arguments).map(String).join(' '))},debug:function(){__sd_log([].slice.call(arguments).map(String).join(' '))},error:function(){__sd_log([].slice.call(arguments).map(String).join(' '))}};var __sd_timers={};var __sd_timer_seq=0;globalThis.setTimeout=function(fn){if(typeof fn!=='function')return 0;var id=++__sd_timer_seq;__sd_timers[id]=1;Promise.resolve().then(function(){if(__sd_timers[id]){delete __sd_timers[id];fn()}});return id};globalThis.clearTimeout=function(id){delete __sd_timers[id]};globalThis.setInterval=function(){return 0};globalThis.clearInterval=function(){};globalThis.Buffer={from:function(v,e){e=String(e||'utf8').toLowerCase();var b=e==='base64'?__sdB64Bytes(v):e==='binary'||e==='latin1'?String(v||'').split('').map(function(c){return c.charCodeAt(0)&255}):JSON.parse(__sd_utf8_encode(String(v||'')));return {__bytes:b,toString:function(enc){enc=String(enc||'utf8').toLowerCase();if(enc==='base64')return __sdB64Encode(b);if(enc==='hex')return b.map(function(n){return ('0'+n.toString(16)).slice(-2)}).join('');return __sd_utf8_decode(JSON.stringify(b))}}}};" +
  "var __sd_types=new Proxy({isArrayBuffer:function(v){return v instanceof ArrayBuffer},isTypedArray:function(v){return ArrayBuffer.isView(v)}},{get:function(t,k){return t[k]||function(){return false}}});" +
  "function __sd_emitter(){this._events={}};__sd_emitter.prototype.on=function(n,f){(this._events[n]||(this._events[n]=[])).push(f);return this};__sd_emitter.prototype.once=function(n,f){var s=this;function w(){s.removeListener(n,w);return f.apply(s,arguments)}return this.on(n,w)};__sd_emitter.prototype.emit=function(n){var a=[].slice.call(arguments,1);(this._events[n]||[]).slice().forEach(function(f){f.apply(null,a)});return true};__sd_emitter.prototype.removeListener=function(n,f){this._events[n]=(this._events[n]||[]).filter(function(x){return x!==f});return this};" +
  "function require(n){if(n==='cheerio-without-node-native'||n==='cheerio')return __sd_cheerio;if(n==='crypto-js')return __sdCrypto;if(n==='axios')return __sdAxios;if(n==='util'||n==='util/types')return n==='util/types'?__sd_types:{types:__sd_types,inherits:function(c,p){c.prototype=Object.create(p.prototype);c.prototype.constructor=c},promisify:function(f){return function(){var a=[].slice.call(arguments);return new Promise(function(ok,no){a.push(function(e,v){e?no(e):ok(v)});f.apply(null,a)})}},inspect:function(v){try{return JSON.stringify(v)}catch(e){return String(v)}}};if(n==='events')return {EventEmitter:__sd_emitter};if(n==='querystring')return {escape:encodeURIComponent,unescape:decodeURIComponent,stringify:function(o){return Object.keys(o||{}).map(function(k){return encodeURIComponent(k)+'='+encodeURIComponent(o[k])}).join('&')}};if(n==='url')return {URL:globalThis.URL,URLSearchParams:globalThis.URLSearchParams};throw new Error('Module not available in sandbox: '+n)};" +
  "globalThis.fetch=async function(u,o){o=o||{};var h=o.headers||{};if(h&&typeof h.forEach==='function'){var m={};h.forEach(function(v,k){m[k]=String(v)});h=m}var r=JSON.parse(__sd_fetch(String(u),String(o.method||\"GET\"),JSON.stringify(h),String(o.body||\"\"),o.redirect!=='manual'));return {ok:r.ok,status:r.status,statusText:r.statusText||'',url:r.url,headers:{get:function(n){return r.headers[String(n).toLowerCase()]||null}},text:function(){return Promise.resolve(r.body)},json:function(){try{return Promise.resolve(JSON.parse(r.body))}catch(e){return Promise.resolve(null)}}}};" +
  "async function __sdAxios(o){if(typeof o==='string')o={url:o};o=o||{};var u=String(o.url||'');if(o.params){var q=Object.keys(o.params).map(function(k){return encodeURIComponent(k)+'='+encodeURIComponent(o.params[k])}).join('&');if(q)u+=(u.indexOf('?')>=0?'&':'?')+q}var body=o.data;if(body&&typeof body!=='string')body=JSON.stringify(body);var r=await fetch(u,{method:String(o.method||'GET').toUpperCase(),headers:o.headers||{},body:body});var t=await r.text();var data;try{data=JSON.parse(t)}catch(e){data=t}var response={data:data,status:r.status,statusText:'',headers:r.headers,config:o,request:null};if(!r.ok){var error=new Error('Request failed with status code '+r.status);error.response=response;throw error}return response};__sdAxios.get=function(u,o){return __sdAxios(Object.assign({},o||{},{url:u,method:'GET'}))};__sdAxios.post=function(u,d,o){return __sdAxios(Object.assign({},o||{},{url:u,data:d,method:'POST'}))};__sdAxios.request=__sdAxios;__sdAxios.create=function(defaults){var client=function(o){return __sdAxios(Object.assign({},defaults||{},o||{}))};client.get=__sdAxios.get;client.post=__sdAxios.post;client.request=client;return client};__sdAxios.default=__sdAxios;"

private const val TAG = "StreamDekPlugin"

/**
 * Reads a DOM node handle out of a QuickJS argument.
 *
 * The same JS integer arrives boxed differently depending on how it got there: the handle
 * returned straight out of `__sd_dom_load` came through as a Long, while handles that had been
 * round-tripped through JSON and `Number()` came through as a Double. Parsing with
 * `toString().toIntOrNull()` worked for the first and returned null for the second -- "2.0" is not
 * an Int -- so a root selector matched but every nested `.find()` and `.attr()` off it silently
 * resolved to nothing, and a source that leaned on cheerio just reported no streams.
 */
private fun domNodeHandle(raw: Any?): Int? = when (raw) {
  null -> null
  is Number -> raw.toInt()
  else -> raw.toString().trim().toDoubleOrNull()?.toInt()
}

class StreamDekPluginManager(context: Context) {
  private val prefs = context.getSharedPreferences("streamdek_plugins", Context.MODE_PRIVATE)
  private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
  // Keyed by "${provider.id}:${provider.code.hashCode()}" so a provider refresh that changes
  // its scraper source naturally invalidates the cached bytecode instead of reusing stale code.
  private val providerBytecodeCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
  // The shim itself is identical for every provider, so it compiles once for the whole app.
  @Volatile private var runtimeBytecode: ByteArray? = null
  private var storageKey = "state"
  @Volatile
  var state: PluginState = load(storageKey)
    private set(value) {
      field = value
      stateChanges.value = value
    }

  private val stateChanges = MutableStateFlow(state)

  /**
   * The same state, for anything that has to follow it rather than read it once.
   *
   * A cloud restore replaces this wholesale without going anywhere near the screen that shows it,
   * so a plain read left the plugins page displaying whatever was there when it opened. Distinct
   * from [onStateChanged], which is a single slot already taken by the cloud push.
   */
  val stateChanged: StateFlow<PluginState> get() = stateChanges.asStateFlow()
  @Volatile private var operationNotice: String? = null
  var onStateChanged: ((String) -> Unit)? = null

  fun consumeOperationNotice(): String? = operationNotice.also { operationNotice = null }

  suspend fun add(raw: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
    operationNotice = null
    val url = normalizeUrl(raw)
    require(state.repos.none { it.url.equals(url, true) }) { "Repository already installed." }
    val loaded = fetchRepo(url, emptyMap())
    state = state.copy(repos = state.repos + loaded.first, providers = state.providers + loaded.second)
    save()
  } }

  suspend fun refresh(url: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
    operationNotice = null
    val old = state.providers.filter { it.repoUrl == url }.associateBy { it.id }
    val loaded = fetchRepo(url, old)
    val existingRepo = state.repos.firstOrNull { it.url == url }
    val refreshedProviders = loaded.second.map { provider ->
      if (existingRepo?.enabled == false) provider.copy(enabled = false) else provider
    }
    state = state.copy(
      repos = state.repos.map { if (it.url == url) loaded.first.copy(enabled = existingRepo?.enabled ?: true, favourite = existingRepo?.favourite ?: false) else it },
      providers = state.providers.filterNot { it.repoUrl == url } + refreshedProviders,
    )
    save()
  } }

  fun remove(url: String) { state = state.copy(repos = state.repos.filterNot { it.url == url }, providers = state.providers.filterNot { it.repoUrl == url }); save() }
  fun enable(value: Boolean) { state = state.copy(enabled = value); save() }
  fun enableRepo(url: String, value: Boolean) {
    state = state.copy(
      repos = state.repos.map { if (it.url == url) it.copy(enabled = value) else it },
      providers = state.providers.map { provider ->
        if (!value && provider.repoUrl == url) provider.copy(enabled = false) else provider
      },
    )
    save()
  }
  fun enableProvider(id: String, value: Boolean) { state = state.copy(enabled = state.enabled || value, providers = state.providers.map { if (it.id == id) it.copy(enabled = value) else it }); save() }
  fun toggleRepoFavourite(url: String) {
    state = state.copy(repos = state.repos.map { if (it.url == url) it.copy(favourite = !it.favourite) else it })
    save()
  }

  /**
   * The pool the scrapers run on.
   *
   * Not [Dispatchers.Default], which is what this was. Default is sized for CPU work — roughly one
   * thread per core — so on a phone with four usable cores only four providers could be in flight
   * however high the semaphore was set, and a provider blocked on a socket was occupying a thread
   * meant for computation. A scraper spends nearly all of its time waiting on the network, so the
   * IO pool is the right home for it: parking there is what those threads are for, and the
   * semaphore below then genuinely governs how many run at once.
   */
  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  private val pluginDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_PLUGIN_PROVIDERS)

  suspend fun streams(id: String, type: String, season: Int?, episode: Int?, onProviderResults: suspend (List<AddonStream>) -> Unit = {}): List<AddonStream> {
    if (!state.enabled) return emptyList()
    val normalized = normalizeType(type)
    val enabledRepos = state.repos.filter { it.enabled }.associateBy { it.url }
    val providers = state.providers
      .filter { it.enabled && it.repoUrl in enabledRepos && it.types.any { candidate -> normalizeType(candidate) == normalized } }
      .sortedWith(compareByDescending<PluginProvider> { enabledRepos[it.repoUrl]?.favourite == true }.thenBy { it.name.lowercase() })
    Log.i("StreamDekPlugin", "Loading streams type=" + normalized + " id=" + id + " providers=" + providers.size)
    val perf = Perf.span("pluginStreams", "providers=" + providers.size)
    return supervisorScope {
      val providerGate = Semaphore(MAX_CONCURRENT_PLUGIN_PROVIDERS)
      providers.map { provider ->
        async(pluginDispatcher) {
          providerGate.withPermit {
            val began = android.os.SystemClock.uptimeMillis()
            val streams = runCatching { runStreams(provider, id, normalized, season, episode) }
              .onFailure { Log.e("StreamDekPlugin", "Provider failed: " + provider.name, it) }
              .onSuccess { Log.i("StreamDekPlugin", "Provider " + provider.name + " returned " + it.size + " streams") }
              .getOrDefault(emptyList())
            perf.mark("provider:" + provider.name, "took=" + (android.os.SystemClock.uptimeMillis() - began) + " results=" + streams.size)
            if (streams.isNotEmpty()) onProviderResults(streams)
            streams
          }
        }
      }.awaitAll().flatten().also { perf.end("done", "total=" + it.size) }
    }
  }

  fun testMediaForProvider(providerId: String): PluginTestMedia {
    val provider = state.providers.firstOrNull { it.id == providerId }
      ?: return PluginTestMedia("The Matrix (1999) • TMDB 603", "603", "movie")
    val signature = listOf(provider.id, provider.name, provider.repoUrl).joinToString(" ").lowercase()
    val isAnime = listOf("anime", "aniwatch", "hianime", "gogo", "animesama", "animepahe").any(signature::contains)
    val types = provider.types.map(::normalizeType).toSet()
    return when {
      isAnime && "tv" in types -> PluginTestMedia("Attack on Titan S1 E1 • TMDB 1429", "1429", "tv", 1, 1)
      isAnime && "movie" in types -> PluginTestMedia("Spirited Away (2001) • TMDB 129", "129", "movie")
      "movie" in types -> PluginTestMedia("The Matrix (1999) • TMDB 603", "603", "movie")
      "tv" in types -> PluginTestMedia("Breaking Bad S1 E1 • TMDB 1396", "1396", "tv", 1, 1)
      else -> PluginTestMedia("The Matrix (1999) • TMDB 603", "603", "movie")
    }
  }

  suspend fun testProvider(providerId: String): Result<List<AddonStream>> = withContext(Dispatchers.IO) {
    val providerName = state.providers.firstOrNull { it.id == providerId }?.name ?: providerId
    runCatching {
      val provider = state.providers.firstOrNull { it.id == providerId }
        ?: throw IllegalArgumentException("Plugin source not found.")
      val testMedia = testMediaForProvider(providerId)
      require(provider.types.any { normalizeType(it) == testMedia.type }) { "This source does not support a compatible test title." }
      runStreams(provider, testMedia.id, testMedia.type, testMedia.season, testMedia.episode, timeoutMs = 45_000L).take(5)
    }.onFailure { Log.e("StreamDekPlugin", "Source test failed: $providerName", it) }
  }

  /**
   * The fields a source wants filled in.
   *
   * Three places can answer, in descending order of authority. The source's own onSettings() is
   * asked first -- it is the only answer that is definitely current. A collection whose manifest
   * declares the fields instead is next: several do, including ones whose scraper is minified or
   * generated, and before this a manifest saying hasSettings opened a dialog that could only
   * report that the code exports no onSettings, leaving a source that needs a token with nowhere
   * to type one. Last is whatever was learned previously, which covers a source that is offline.
   *
   * An empty list is a real answer rather than a failure -- see the dialog, which then lets the
   * values be entered by hand.
   */
  suspend fun settingsSchema(providerId: String): Result<List<PluginSettingField>> = withContext(Dispatchers.IO) { runCatching {
    val provider = state.providers.firstOrNull { it.id == providerId }
      ?: throw IllegalArgumentException("Plugin source not found.")
    val fromCode = if (!pluginDeclaresSettings(provider.code)) null else runCatching {
      val raw = executeProvider(provider, null, null, null, null, settingsOnly = true, timeoutMs = 15_000L)
      // Only the device can produce this -- it comes from running the source's own onSettings() in
      // the sandbox -- so it is cached and synced outward for the web portal, which has no engine
      // and would otherwise have no idea what fields a source wants.
      JSONArray(raw).also { prefs.edit().putString(settingsSchemaKey(providerId), raw).apply() }
    }.getOrNull()
    parsePluginSettingFields(fromCode ?: settingsSchemaJson(providerId) ?: JSONArray())
  } }

  fun providerSettings(providerId: String): Map<String, Any> {
    val root = runCatching { JSONObject(prefs.getString(settingsStorageKey(providerId), "{}") ?: "{}") }.getOrDefault(JSONObject())
    return buildMap { root.keys().forEach { key -> root.opt(key)?.takeUnless { it == JSONObject.NULL }?.let { put(key, it) } } }
  }

  fun saveProviderSettings(providerId: String, values: Map<String, Any>) {
    val root = JSONObject()
    values.forEach { (key, value) -> root.put(key, value) }
    prefs.edit().putString(settingsStorageKey(providerId), root.toString()).apply()
    // save() stamps updatedAt and fires onStateChanged, which is what pushes the document to the
    // profile. Without it a token entered here stayed on this device and every other one kept
    // reporting no streams.
    save()
  }

  private fun settingsStorageKey(providerId: String) = "settings:$storageKey:$providerId"

  private fun settingsSchemaKey(providerId: String) = "schema:$storageKey:$providerId"

  private fun settingsSchemaJson(providerId: String): JSONArray? =
    prefs.getString(settingsSchemaKey(providerId), null)?.let { raw -> runCatching { JSONArray(raw) }.getOrNull() }

  /**
   * Copies settings carried in a synced document into local storage.
   *
   * Blank values are skipped rather than written: a portal or an older build that has never seen a
   * source sends it back with no settings at all, and taking that literally would wipe a working
   * token off this device on the next sign-in.
   */
  private fun applyCloudSettings(raw: String) {
    val providers = runCatching { JSONObject(raw).optJSONArray("providers") }.getOrNull() ?: return
    val editor = prefs.edit()
    for (index in 0 until providers.length()) {
      val item = providers.optJSONObject(index) ?: continue
      val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
      val values = item.optJSONObject("settings") ?: continue
      if (values.length() > 0) editor.putString(settingsStorageKey(id), values.toString())
    }
    editor.apply()
  }

  private suspend fun runStreams(provider: PluginProvider, id: String, type: String, season: Int?, episode: Int?, timeoutMs: Long = 60_000L): List<AddonStream> =
    parse(executeProvider(provider, id, type, season, episode, settingsOnly = false, timeoutMs = timeoutMs), provider)

  /** Serialises the write-back in [providerCode]. See the comment there. */
  private val codeRecoveryLock = Any()

  /** Provider id to scraper URL for one collection, read once per process. */
  private val manifestSourceUrls = mutableMapOf<String, Map<String, String>>()

  private fun providerSourceUrls(repoUrl: String): Map<String, String> = synchronized(manifestSourceUrls) {
    manifestSourceUrls.getOrPut(repoUrl) {
      val entries = JSONObject(text(repoUrl)).optJSONArray("scrapers") ?: JSONArray()
      buildMap {
        for (index in 0 until entries.length()) {
          val item = entries.optJSONObject(index) ?: continue
          val key = item.optString("id")
          val file = item.optString("filename")
          if (key.isNotBlank() && file.isNotBlank()) {
            put(repoUrl.lowercase() + ":" + key, resolvePluginProviderUrl(repoUrl, file))
          }
        }
      }
    }
  }

  /**
   * The scraper's own code, downloading it if this device does not have it.
   *
   * A stored collection can be holding sources it has no code for, and the whole of what that
   * looked like from the outside was one source that had stopped working. The account document
   * carries every provider without its code -- 35 scrapers per collection is not something to push
   * through a profile blob -- so a device restoring it refills each one from its local cache and
   * re-downloads the rest. Any of those downloads can fail, the blank entry is saved either way,
   * and nothing retried it: the source stayed listed, stayed switched on, and ran an empty file.
   * An empty file exports nothing, so it failed as "this source is missing the function StreamDek
   * needs" -- which reads as the plugin author's mistake and is not one.
   *
   * Fetched here instead, at the moment it is needed, and written back so it is paid for once.
   * This is what the television has always done, which is why the same collection answers there.
   */
  private suspend fun providerCode(provider: PluginProvider): String {
    provider.code.takeIf { it.isNotBlank() }?.let { return it }
    val sourceUrl = withContext(Dispatchers.IO) { runCatching { providerSourceUrls(provider.repoUrl)[provider.id] } }
      .getOrNull()
      ?: throw IllegalStateException("StreamDek does not have this source's code, and its collection could not be reached to download it. Refresh the collection and try again.")
    val code = withContext(Dispatchers.IO) { text(sourceUrl) }
    require(code.isNotBlank()) { "This source's collection served an empty file for it." }
    Log.i(TAG, "Recovered missing code for " + provider.name + " (" + code.length + " chars)")
    // Written back so the next call is instant, and so a source that has been silently empty since
    // its last cloud restore stops being empty rather than being re-downloaded on every press.
    //
    // Locked because a search fans out to five providers at once and each is a read-modify-write
    // of the same state: unsynchronised, four of five recoveries would be written and then
    // overwritten by a copy taken before them, and those sources would download again next time.
    synchronized(codeRecoveryLock) {
      state = state.copy(providers = state.providers.map { if (it.id == provider.id) it.copy(code = code) else it })
      save()
    }
    return code
  }

  private suspend fun executeProvider(provider: PluginProvider, id: String?, type: String?, season: Int?, episode: Int?, settingsOnly: Boolean, timeoutMs: Long): String = withTimeout(timeoutMs) {
    val code = providerCode(provider)
    val deferred = CompletableDeferred<String>()
    val domNodes = mutableMapOf<Int, Element>()
    var nextDomNodeId = 1
    fun registerDomNode(element: Element): Int {
      domNodes.entries.firstOrNull { it.value === element }?.let { return it.key }
      val id = nextDomNodeId++
      domNodes[id] = element
      return id
    }
    val raw = quickJs(Dispatchers.Default) {
      function("__sd_log") { args: Array<Any?> ->
        Log.i("StreamDekPlugin", "[${provider.name}] " + args.getOrNull(0)?.toString().orEmpty())
        null
      }
      function("__sd_dom_load") { args: Array<Any?> ->
        val html = args.getOrNull(0)?.toString().orEmpty()
        registerDomNode(Jsoup.parse(html)).also {
          Log.d(TAG, "[" + provider.name + "] dom.load " + html.length + " chars -> node " + it)
        }
      }
      function("__sd_dom_select") { args: Array<Any?> ->
        val root = domNodeHandle(args.getOrNull(0))?.let(domNodes::get)
        val selector = args.getOrNull(1)?.toString().orEmpty()
        val ids = if (root == null || selector.isBlank()) emptyList() else runCatching { root.select(selector).map(::registerDomNode) }.getOrDefault(emptyList())
        Log.d(TAG, "[" + provider.name + "] dom.select " + selector + " -> " + ids.size)
        JSONArray(ids).toString()
      }
      function("__sd_dom_matches") { args: Array<Any?> ->
        val node = domNodeHandle(args.getOrNull(0))?.let(domNodes::get)
        val selector = args.getOrNull(1)?.toString().orEmpty()
        node != null && selector.isNotBlank() && runCatching { node.`is`(selector) }.getOrDefault(false)
      }
      function("__sd_dom_attr") { args: Array<Any?> ->
        val node = domNodeHandle(args.getOrNull(0))?.let(domNodes::get)
        node?.attr(args.getOrNull(1)?.toString().orEmpty()).orEmpty()
      }
      function("__sd_dom_text") { args: Array<Any?> ->
        domNodeHandle(args.getOrNull(0))?.let(domNodes::get)?.text().orEmpty()
      }
      function("__sd_dom_html") { args: Array<Any?> ->
        domNodeHandle(args.getOrNull(0))?.let(domNodes::get)?.html().orEmpty()
      }
      function("__sd_dom_children") { args: Array<Any?> ->
        val node = domNodeHandle(args.getOrNull(0))?.let(domNodes::get)
        JSONArray(node?.children()?.map(::registerDomNode).orEmpty()).toString()
      }
      function("__sd_dom_parent") { args: Array<Any?> ->
        domNodeHandle(args.getOrNull(0))?.let(domNodes::get)?.parent()?.let(::registerDomNode) ?: 0
      }
      function("__sd_utf8_encode") { args: Array<Any?> ->
        JSONArray(args.getOrNull(0)?.toString().orEmpty().toByteArray(Charsets.UTF_8).map { it.toInt() and 0xff }).toString()
      }
      function("__sd_utf8_decode") { args: Array<Any?> ->
        runCatching {
          val source = JSONArray(args.getOrNull(0)?.toString().orEmpty())
          String(ByteArray(source.length()) { index -> source.optInt(index).toByte() }, Charsets.UTF_8)
        }.getOrDefault("")
      }
      function("__capture_result") { args: Array<Any?> ->
        deferred.complete(args.getOrNull(0)?.toString() ?: "[]")
        null
      }
      function("__capture_error") { args: Array<Any?> ->
        deferred.completeExceptionally(IllegalStateException(args.getOrNull(0)?.toString() ?: "Plugin execution failed"))
        null
      }
      function("__sd_fetch") { args: Array<Any?> ->
        val url = args.getOrNull(0)?.toString().orEmpty()
        val method = args.getOrNull(1)?.toString()?.uppercase() ?: "GET"
        val headerJson = runCatching { JSONObject(args.getOrNull(2)?.toString() ?: "{}") }.getOrDefault(JSONObject())
        val body = args.getOrNull(3)?.toString().orEmpty()
        val followRedirects = args.getOrNull(4) as? Boolean ?: true
        require(url.startsWith("http://") || url.startsWith("https://")) { "Only HTTP(S) is allowed." }
        val request = Request.Builder().url(url)
        // Scrapers copy browser header dumps wholesale, Accept-Encoding included. Setting it by
        // hand switches OkHttp out of transparent gzip, so the body arrives still compressed and
        // every JSON.parse in the provider fails on binary. Drop it and let OkHttp negotiate.
        headerJson.keys().asSequence().filterNot { it.equals("Accept-Encoding", true) }.toList()
          .forEach { key -> headerJson.optString(key).takeIf { it.isNotBlank() }?.let { request.header(key, it) } }
        if (!headerJson.keys().asSequence().any { it.equals("User-Agent", true) }) request.header("User-Agent", PLUGIN_USER_AGENT)
        val requestBody = if (method == "GET" || method == "HEAD") null else body.toRequestBody(headerJson.optString("Content-Type").toMediaTypeOrNull())
        // `redirect: "manual"` is how a source reads the Location of a 302 rather than following
        // it, which is the usual way these hosts hand back a signed download URL.
        val client = if (followRedirects) http else http.newBuilder().followRedirects(false).followSslRedirects(false).build()
        runBlocking(Dispatchers.IO) {
          client.newCall(request.method(method, requestBody).build()).execute().use {
            Log.d(TAG, "[" + provider.name + "] " + method + " " + url.substringBefore("?").take(120) +
              " -> " + it.code + " " + (it.body?.contentLength() ?: -1L) + "b" +
              (if (headerJson.keys().asSequence().any { name -> name.equals("Cookie", true) }) " +cookie" else ""))
            val responseHeaders = JSONObject()
            // names() is a unique set and header(name) answers with the last value only, so a
            // response carrying several Set-Cookie lines arrived as one. A provider that
            // authenticates by cookie reads those, and losing all but the last shows up as
            // "no streams" with nothing to explain it.
            it.headers.names().forEach { name ->
              val values = it.headers.values(name)
              responseHeaders.put(name.lowercase(), if (values.size > 1) values.joinToString(", ") else values.firstOrNull().orEmpty())
            }
            JSONObject().put("ok", it.isSuccessful).put("status", it.code).put("statusText", it.message).put("url", it.request.url.toString()).put("headers", responseHeaders).put("body", it.body?.string().orEmpty()).toString()
          }
        }
      }
      installPluginCryptoBridge()
      // Compiling the shim to bytecode is the expensive part of each streams() call, and it is the
      // same blob every time, so it is compiled once per process rather than once per provider.
      evaluate<Any?>(runtimeBytecode ?: compile(PLUGIN_RUNTIME_SOURCE, "runtime.js", false).also { runtimeBytecode = it })
      // Settings go in before the provider body runs. A source that reads SCRAPER_SETTINGS at module
      // scope rather than inside getStreams -- a FebBox token, say -- saw undefined and returned nothing.
      val settingsJson = JSONObject(providerSettings(provider.id)).toString()
      evaluate<Any?>("globalThis.SCRAPER_SETTINGS=$settingsJson;globalThis.global.SCRAPER_SETTINGS=globalThis.SCRAPER_SETTINGS;")
      // Cached separately, keyed by content, so a refreshed provider recompiles and the shim does not.
      val providerBytecode = providerBytecodeCache.getOrPut("${provider.id}:${code.hashCode()}") {
        compile("var module={exports:{}};var exports=module.exports;(function(){" + normalizePluginJavaScript(code) + "})();", "provider.js", false)
      }
      evaluate<Any?>(providerBytecode)
      val invocation = if (settingsOnly) {
        "var f=module.exports.onSettings||globalThis.onSettings;if(typeof f!=='function')throw new Error('Plugin does not export onSettings');var r=await f();"
      } else {
        "var f=module.exports.getStreams||globalThis.getStreams;if(typeof f!=='function')throw new Error('Plugin does not export getStreams');var r=await f(" +
          JSONObject.quote(id) + "," + JSONObject.quote(type) + "," + (season?.toString() ?: "undefined") + "," + (episode?.toString() ?: "undefined") + ");"
      }
      val call = "(async function(){${invocation}__capture_result(JSON.stringify(r||[]));})().catch(function(e){__sd_log(String(e&&e.stack||e));__capture_error(String(e&&e.message||e));})"
      evaluate<Any?>(call)
      deferred.await()
    }
    raw
  }

  private fun parse(raw: String, provider: PluginProvider): List<AddonStream> {
    val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    return buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val url = when (val value = item.opt("url")) {
          is JSONObject -> value.optString("url").ifBlank { null }
          else -> value?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        } ?: item.optString("externalUrl").ifBlank { null }
        val hash = item.optString("infoHash").ifBlank { null }
        if (url == null && hash == null) continue
        val headers = item.optJSONObject("headers")
        // The source's own name deliberately no longer stands in for a missing name or title
        // here. It is already shown as the result's attribution, and copying it into the headline
        // made every result from such a source read as that source's name repeated, with the
        // actual release, quality and size pushed into a smaller line underneath or lost. Left
        // null, the list falls back to the title being watched -- see streamDisplayName.
        add(AddonStream("plugin:" + provider.id, provider.name, item.optString("name").ifBlank { null }, item.optString("title").ifBlank { null }, item.optString("description").ifBlank { null }, url, hash, item.optInt("fileIdx").takeIf { item.has("fileIdx") }, item.optString("filename").ifBlank { null }, item.optString("quality").ifBlank { null }, item.optString("size").ifBlank { null }, emptyList(), requestHeaders = buildMap {
          headers?.keys()?.forEach { key -> headers.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) } }
        }))
      }
    }
  }

  private fun fetchRepo(url: String, previous: Map<String, PluginProvider>): Pair<PluginRepo, List<PluginProvider>> {
    val manifest = JSONObject(text(url))
    val name = manifest.optString("name").trim()
    val version = manifest.optString("version").trim()
    require(name.isNotEmpty() && version.isNotEmpty()) { "Invalid repository manifest." }
    // Refused outright rather than installed-and-filtered: a collection whose whole purpose is
    // pornography has nothing left once its sources are removed, and saying so is clearer than
    // adding something that then returns nothing.
    require(!AdultContentFilter.isBlocked(name, manifest.optString("description"))) {
      "This collection is an adult source and cannot be added."
    }
    val entries = manifest.optJSONArray("scrapers") ?: throw IllegalArgumentException("No providers in repository.")
    val providers = mutableListOf<PluginProvider>()
    val skipped = mutableListOf<String>()
    for (index in 0 until entries.length()) {
      val item = entries.optJSONObject(index) ?: continue
      val key = item.optString("id")
      val file = item.optString("filename")
      if (key.isBlank() || file.isBlank()) continue
      // Individual adult sources inside an otherwise ordinary collection are skipped, so the
      // rest of it still works rather than the whole thing being unusable.
      if (AdultContentFilter.isBlocked(item.optString("name"), item.optString("description"), key)) continue
      val source = resolvePluginProviderUrl(url, file)
      val providerId = url.lowercase() + ":" + key
      val types = item.optJSONArray("supportedTypes")
      val code = try {
        text(source)
      } catch (error: Throwable) {
        val providerName = item.optString("name").ifBlank { key }
        Log.w("StreamDekPlugin", "Skipping provider $providerName from $url", error)
        skipped += providerName
        continue
      }
      // A collection may describe its source's settings in the manifest rather than in the
      // scraper. Kept in the same slot the sandbox writes to, so everything downstream -- the
      // dialog, the cloud document, the web portal -- reads one schema without caring which end
      // of the collection declared it.
      val declaredSettings = item.optJSONArray("settings") ?: item.optJSONArray("settingsSchema")
      if (declaredSettings != null && declaredSettings.length() > 0) {
        prefs.edit().putString(settingsSchemaKey(providerId), declaredSettings.toString()).apply()
      }
      providers += PluginProvider(providerId, url, item.optString("name").ifBlank { key }, buildList {
        if (types != null) for (typeIndex in 0 until types.length()) types.optString(typeIndex).takeIf { it.isNotBlank() }?.let(::add)
        if (isEmpty()) addAll(listOf("movie", "tv"))
      }, item.optBoolean("enabled", true) && (previous[providerId]?.enabled ?: true), code,
        item.optBoolean("hasSettings", false) || pluginDeclaresSettings(code) || declaredSettings != null,
      )
    }
    require(providers.isNotEmpty()) { "No compatible providers in repository." }
    if (skipped.isNotEmpty()) {
      val names = skipped.take(3).joinToString(", ")
      operationNotice = "Installed ${providers.size} sources. Skipped ${skipped.size} unavailable source${if (skipped.size == 1) "" else "s"}: $names${if (skipped.size > 3) "…" else ""}."
    }
    return PluginRepo(url, name, version, manifest.optString("description").ifBlank { null }) to providers
  }
  private fun fetchRepoWithFallback(url: String, previous: Map<String, PluginProvider>): Pair<PluginRepo, List<PluginProvider>> {
    var lastFailure: Throwable? = null
    for (candidate in pluginRepositoryUrlCandidates(url)) {
      runCatching { fetchRepo(candidate, previous) }
        .onSuccess { return it }
        .onFailure { lastFailure = it }
    }
    throw lastFailure ?: IllegalArgumentException("Invalid repository URL.")
  }

  private fun text(url: String): String = try {
    http.newCall(Request.Builder().url(url).header("User-Agent", "StreamDek/1.0").build()).execute().use {
      require(it.isSuccessful) { "HTTP ${it.code} while loading ${runCatching { URI(url).path.substringAfterLast('/') }.getOrDefault("plugin file")}" }
      it.body?.string() ?: throw IllegalStateException("Empty response.")
    }
  } catch (e: java.io.IOException) {
    val host = runCatching { URI(url).host }.getOrNull().orEmpty()
    if (isLocalNetworkHost(host)) {
      throw IllegalStateException(
        "Could not reach $host from this phone. Localhost/private-network URLs are supported, " +
          "but 127.0.0.1 always means \"this device\" — point it at your computer's LAN IP " +
          "instead (e.g. 192.168.x.x), or run `adb reverse tcp:<port> tcp:<port>` and use " +
          "127.0.0.1:<port> if the phone is connected over USB.",
        e,
      )
    }
    throw e
  }

  private fun normalizeUrl(raw: String): String {
    var value = raw.trim()
    if (!value.startsWith("http")) value = "https://" + value
    val uri = URI(value)
    require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "Invalid repository URL." }
    if (!uri.path.endsWith(".json")) value = value.trimEnd('/') + "/manifest.json"
    return value
  }

  private fun normalizeType(value: String) = if (value.lowercase() in setOf("series", "show")) "tv" else value.lowercase()

  fun snapshotJson(includeCode: Boolean = true): String = serialize(state, includeCode)

  fun snapshotUpdatedAt(raw: String): Long = runCatching { JSONObject(raw).optLong("updatedAt", 0L) }.getOrDefault(0L)

  fun selectProfileStorage(ownerKey: String, cloudJson: String? = null) {
    storageKey = "state:$ownerKey"
    state = if (!cloudJson.isNullOrBlank() && cloudJson != "{}") parse(cloudJson) else load(storageKey)
    // After storageKey, never before -- the settings keys are scoped by it.
    if (!cloudJson.isNullOrBlank() && cloudJson != "{}") applyCloudSettings(cloudJson)
    prefs.edit().putString(storageKey, serialize(state)).apply()
  }

  fun restoreCloudState(raw: String) {
    val cachedProviders = state.providers.associateBy { "${it.repoUrl}:${it.id}" }
    val cloudState = parse(raw)
    state = cloudState.copy(providers = cloudState.providers.map { provider ->
      if (provider.code.isNotBlank()) provider
      else provider.copy(code = cachedProviders["${provider.repoUrl}:${provider.id}"]?.code.orEmpty())
    })
    applyCloudSettings(raw)
    prefs.edit().putString(storageKey, serialize(state)).apply()
  }

  private fun serialize(value: PluginState, includeCode: Boolean = true): String {
    val root = JSONObject().put("enabled", value.enabled).put("updatedAt", value.updatedAt)
    root.put("repos", JSONArray().apply { value.repos.forEach { put(JSONObject().put("url", it.url).put("name", it.name).put("version", it.version).put("description", it.description).put("enabled", it.enabled).put("favourite", it.favourite)) } })
    root.put("providers", JSONArray().apply {
      value.providers.forEach {
        put(
          JSONObject().put("id", it.id).put("repo", it.repoUrl).put("name", it.name).put("types", JSONArray(it.types))
            .put("enabled", it.enabled).put("code", if (includeCode) it.code else "").put("hasSettings", it.hasSettings)
            // The values a source needs to work at all -- a FebBox cookie, an API key. They used to
            // live only in local storage, so a restored profile came back with every source enabled
            // and no token, which reads as a source that simply returns nothing.
            .put("settings", JSONObject(providerSettings(it.id)))
            .put("settingsSchema", settingsSchemaJson(it.id) ?: JSONArray()),
        )
      }
    })
    return root.toString()
  }

  private fun save() {
    state = state.copy(updatedAt = System.currentTimeMillis())
    val raw = serialize(state)
    prefs.edit().putString(storageKey, raw).apply()
    onStateChanged?.invoke(serialize(state, includeCode = false))
  }

  private fun load(key: String): PluginState = parse(prefs.getString(key, "{}") ?: "{}")

  private fun parse(raw: String): PluginState = runCatching {
    val root = JSONObject(raw)
    val repos = root.optJSONArray("repos") ?: JSONArray()
    val providers = root.optJSONArray("providers") ?: JSONArray()
    PluginState(root.optBoolean("enabled", true), List(repos.length()) { repos.getJSONObject(it).run { PluginRepo(getString("url"), getString("name"), getString("version"), optString("description").ifBlank { null }, optBoolean("enabled", true), optBoolean("favourite", false)) } }, List(providers.length()) {
      providers.getJSONObject(it).run {
        val types = optJSONArray("types") ?: JSONArray()
        PluginProvider(getString("id"), getString("repo"), getString("name"), List(types.length()) { typeIndex -> types.getString(typeIndex) }, optBoolean("enabled", true), optString("code"), optBoolean("hasSettings", false))
      }
    }, root.optLong("updatedAt", 0L))
  }.getOrDefault(PluginState())
}



object StreamDekPlugins {
  lateinit var manager: StreamDekPluginManager
    private set
  fun initialize(context: Context) { if (!::manager.isInitialized) manager = StreamDekPluginManager(context.applicationContext) }
}
