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
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class PluginRepo(val url: String, val name: String, val version: String, val description: String?, val enabled: Boolean = true)
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
  function __sdWordArray(words,sigBytes){
    var value={words:(words||[]).slice(),sigBytes:sigBytes===undefined?(words||[]).length*4:Number(sigBytes)};
    value.bytes=function(){return __sdWordsToBytes(value.words,value.sigBytes)};
    value.concat=function(other){var joined=value.bytes().concat(other&&other.bytes?other.bytes():[]);value.words=__sdBytesToWords(joined);value.sigBytes=joined.length;return value};
    value.toString=function(encoder){if(encoder===__sdCrypto.enc.Utf8)return __sd_utf8_decode(JSON.stringify(value.bytes()));return ''};
    return value;
  }
  var __sdCrypto={
    enc:{Utf8:{parse:function(v){var bytes=JSON.parse(__sd_utf8_encode(String(v)));return __sdWordArray(__sdBytesToWords(bytes),bytes.length)}},Base64:{parse:function(v){var bytes=__sdB64Bytes(v);return __sdWordArray(__sdBytesToWords(bytes),bytes.length)} }},
    lib:{WordArray:{create:function(words,sigBytes){return __sdWordArray(words,sigBytes)}}},mode:{CBC:'CBC'},pad:{Pkcs7:'Pkcs7'}
  };
  function __sdDecrypt(kind,cipher,key,options){var data=typeof cipher==='string'?__sdB64Bytes(cipher):(cipher&&cipher.ciphertext&&cipher.ciphertext.bytes?cipher.ciphertext.bytes():[]);var keyBytes=key&&key.bytes?key.bytes():[];var iv=options&&options.iv&&options.iv.bytes?options.iv.bytes():[];var plain=__sd_crypto_decrypt(kind,JSON.stringify(data),JSON.stringify(keyBytes),JSON.stringify(iv));return {toString:function(){return plain||''}}}
  __sdCrypto.AES={decrypt:function(cipher,key,options){return __sdDecrypt('AES',cipher,key,options)}};
  __sdCrypto.TripleDES={decrypt:function(cipher,key,options){return __sdDecrypt('DESede',cipher,key,options)}};
""".trimIndent()

class StreamDekPluginManager(context: Context) {
  private val prefs = context.getSharedPreferences("streamdek_plugins", Context.MODE_PRIVATE)
  private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
  // Keyed by "${provider.id}:${provider.code.hashCode()}" so a provider refresh that changes
  // its scraper source naturally invalidates the cached bytecode instead of reusing stale code.
  private val providerBytecodeCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
  private var storageKey = "state"
  @Volatile var state = load(storageKey); private set
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
      repos = state.repos.map { if (it.url == url) loaded.first.copy(enabled = existingRepo?.enabled ?: true) else it },
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

  suspend fun streams(id: String, type: String, season: Int?, episode: Int?, onProviderResults: suspend (List<AddonStream>) -> Unit = {}): List<AddonStream> {
    if (!state.enabled) return emptyList()
    val normalized = normalizeType(type)
    val enabledRepos = state.repos.filter { it.enabled }.mapTo(mutableSetOf()) { it.url }
    val providers = state.providers.filter { it.enabled && it.repoUrl in enabledRepos && it.types.any { candidate -> normalizeType(candidate) == normalized } }
    Log.i("StreamDekPlugin", "Loading streams type=" + normalized + " id=" + id + " providers=" + providers.size)
    return supervisorScope {
      val providerGate = Semaphore(5)
      providers.map { provider ->
        async(Dispatchers.Default) {
          providerGate.withPermit {
            val streams = runCatching { runStreams(provider, id, normalized, season, episode) }
              .onFailure { Log.e("StreamDekPlugin", "Provider failed: " + provider.name, it) }
              .onSuccess { Log.i("StreamDekPlugin", "Provider " + provider.name + " returned " + it.size + " streams") }
              .getOrDefault(emptyList())
            if (streams.isNotEmpty()) onProviderResults(streams)
            streams
          }
        }
      }.awaitAll().flatten()
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

  suspend fun settingsSchema(providerId: String): Result<List<PluginSettingField>> = withContext(Dispatchers.IO) { runCatching {
    val provider = state.providers.firstOrNull { it.id == providerId }
      ?: throw IllegalArgumentException("Plugin source not found.")
    require(provider.hasSettings) { "This source does not advertise extra settings." }
    val array = JSONArray(executeProvider(provider, null, null, null, null, settingsOnly = true, timeoutMs = 15_000L))
    buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val type = item.optString("type").lowercase()
        val options = item.optJSONArray("options")
        add(PluginSettingField(
          type = type,
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
  } }

  fun providerSettings(providerId: String): Map<String, Any> {
    val root = runCatching { JSONObject(prefs.getString(settingsStorageKey(providerId), "{}") ?: "{}") }.getOrDefault(JSONObject())
    return buildMap { root.keys().forEach { key -> root.opt(key)?.takeUnless { it == JSONObject.NULL }?.let { put(key, it) } } }
  }

  fun saveProviderSettings(providerId: String, values: Map<String, Any>) {
    val root = JSONObject()
    values.forEach { (key, value) -> root.put(key, value) }
    prefs.edit().putString(settingsStorageKey(providerId), root.toString()).apply()
  }

  private fun settingsStorageKey(providerId: String) = "settings:$storageKey:$providerId"

  private suspend fun runStreams(provider: PluginProvider, id: String, type: String, season: Int?, episode: Int?, timeoutMs: Long = 25_000L): List<AddonStream> =
    parse(executeProvider(provider, id, type, season, episode, settingsOnly = false, timeoutMs = timeoutMs), provider)

  private suspend fun executeProvider(provider: PluginProvider, id: String?, type: String?, season: Int?, episode: Int?, settingsOnly: Boolean, timeoutMs: Long): String = withTimeout(timeoutMs) {
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
        registerDomNode(Jsoup.parse(args.getOrNull(0)?.toString().orEmpty()))
      }
      function("__sd_dom_select") { args: Array<Any?> ->
        val root = args.getOrNull(0)?.toString()?.toIntOrNull()?.let(domNodes::get)
        val selector = args.getOrNull(1)?.toString().orEmpty()
        val ids = if (root == null || selector.isBlank()) emptyList() else runCatching { root.select(selector).map(::registerDomNode) }.getOrDefault(emptyList())
        JSONArray(ids).toString()
      }
      function("__sd_dom_matches") { args: Array<Any?> ->
        val node = args.getOrNull(0)?.toString()?.toIntOrNull()?.let(domNodes::get)
        val selector = args.getOrNull(1)?.toString().orEmpty()
        node != null && selector.isNotBlank() && runCatching { node.`is`(selector) }.getOrDefault(false)
      }
      function("__sd_dom_attr") { args: Array<Any?> ->
        val node = args.getOrNull(0)?.toString()?.toIntOrNull()?.let(domNodes::get)
        node?.attr(args.getOrNull(1)?.toString().orEmpty()).orEmpty()
      }
      function("__sd_dom_text") { args: Array<Any?> ->
        args.getOrNull(0)?.toString()?.toIntOrNull()?.let(domNodes::get)?.text().orEmpty()
      }
      function("__sd_dom_html") { args: Array<Any?> ->
        args.getOrNull(0)?.toString()?.toIntOrNull()?.let(domNodes::get)?.html().orEmpty()
      }
      function("__sd_dom_children") { args: Array<Any?> ->
        val node = args.getOrNull(0)?.toString()?.toIntOrNull()?.let(domNodes::get)
        JSONArray(node?.children()?.map(::registerDomNode).orEmpty()).toString()
      }
      function("__sd_dom_parent") { args: Array<Any?> ->
        args.getOrNull(0)?.toString()?.toIntOrNull()?.let(domNodes::get)?.parent()?.let(::registerDomNode) ?: 0
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
      function("__sd_crypto_decrypt") { args: Array<Any?> ->
        runCatching {
          fun bytes(raw: Any?): ByteArray {
            val source = JSONArray(raw?.toString().orEmpty())
            return ByteArray(source.length()) { index -> source.optInt(index).toByte() }
          }
          val algorithm = args.getOrNull(0)?.toString().orEmpty()
          val encrypted = bytes(args.getOrNull(1))
          val key = bytes(args.getOrNull(2))
          val iv = bytes(args.getOrNull(3))
          val transformation = if (algorithm == "DESede") "DESede/CBC/PKCS5Padding" else "AES/CBC/PKCS5Padding"
          val cipher = Cipher.getInstance(transformation)
          cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, algorithm), IvParameterSpec(iv))
          String(cipher.doFinal(encrypted), Charsets.UTF_8)
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
        require(url.startsWith("http://") || url.startsWith("https://")) { "Only HTTP(S) is allowed." }
        val request = Request.Builder().url(url)
        headerJson.keys().forEach { key -> headerJson.optString(key).takeIf { it.isNotBlank() }?.let { request.header(key, it) } }
        if (!headerJson.keys().asSequence().any { it.equals("User-Agent", true) }) request.header("User-Agent", "StreamDek/1.0")
        val requestBody = if (method == "GET" || method == "HEAD") null else body.toRequestBody(headerJson.optString("Content-Type").toMediaTypeOrNull())
        runBlocking(Dispatchers.IO) {
          http.newCall(request.method(method, requestBody).build()).execute().use {
            val responseHeaders = JSONObject()
            it.headers.names().forEach { name -> responseHeaders.put(name.lowercase(), it.header(name).orEmpty()) }
            JSONObject().put("ok", it.isSuccessful).put("status", it.code).put("url", it.request.url.toString()).put("headers", responseHeaders).put("body", it.body?.string().orEmpty()).toString()
          }
        }
      }
      val providerSource = CHEERIO_COMPAT_SHIM + "\n" + "globalThis.window=globalThis;globalThis.self=globalThis;globalThis.global=globalThis;globalThis.console={log:function(){},error:function(){__sd_log([].slice.call(arguments).map(String).join(' '))}};globalThis.setTimeout=function(fn){fn();return 0};globalThis.clearTimeout=function(){};globalThis.Buffer={from:function(v,e){e=String(e||'utf8').toLowerCase();var b=e==='base64'?__sdB64Bytes(v):e==='binary'||e==='latin1'?String(v||'').split('').map(function(c){return c.charCodeAt(0)&255}):JSON.parse(__sd_utf8_encode(String(v||'')));return {__bytes:b,toString:function(enc){enc=String(enc||'utf8').toLowerCase();if(enc==='base64')return __sdB64Encode(b);if(enc==='hex')return b.map(function(n){return ('0'+n.toString(16)).slice(-2)}).join('');return __sd_utf8_decode(JSON.stringify(b))}}}};" +
        "var __sd_types=new Proxy({isArrayBuffer:function(v){return v instanceof ArrayBuffer},isTypedArray:function(v){return ArrayBuffer.isView(v)}},{get:function(t,k){return t[k]||function(){return false}}});" +
        "function __sd_emitter(){this._events={}};__sd_emitter.prototype.on=function(n,f){(this._events[n]||(this._events[n]=[])).push(f);return this};__sd_emitter.prototype.once=function(n,f){var s=this;function w(){s.removeListener(n,w);return f.apply(s,arguments)}return this.on(n,w)};__sd_emitter.prototype.emit=function(n){var a=[].slice.call(arguments,1);(this._events[n]||[]).slice().forEach(function(f){f.apply(null,a)});return true};__sd_emitter.prototype.removeListener=function(n,f){this._events[n]=(this._events[n]||[]).filter(function(x){return x!==f});return this};" +
        "function require(n){if(n==='cheerio-without-node-native'||n==='cheerio')return __sd_cheerio;if(n==='crypto-js')return __sdCrypto;if(n==='axios')return __sdAxios;if(n==='util'||n==='util/types')return n==='util/types'?__sd_types:{types:__sd_types,inherits:function(c,p){c.prototype=Object.create(p.prototype);c.prototype.constructor=c},promisify:function(f){return function(){var a=[].slice.call(arguments);return new Promise(function(ok,no){a.push(function(e,v){e?no(e):ok(v)});f.apply(null,a)})}},inspect:function(v){try{return JSON.stringify(v)}catch(e){return String(v)}}};if(n==='events')return {EventEmitter:__sd_emitter};if(n==='querystring')return {escape:encodeURIComponent,unescape:decodeURIComponent,stringify:function(o){return Object.keys(o||{}).map(function(k){return encodeURIComponent(k)+'='+encodeURIComponent(o[k])}).join('&')}};if(n==='url')return {URL:globalThis.URL,URLSearchParams:globalThis.URLSearchParams};throw new Error('Module not available in sandbox: '+n)};" +
        "globalThis.fetch=async function(u,o){o=o||{};var r=JSON.parse(__sd_fetch(String(u),String(o.method||\"GET\"),JSON.stringify(o.headers||{}),String(o.body||\"\")));return {ok:r.ok,status:r.status,url:r.url,headers:{get:function(n){return r.headers[String(n).toLowerCase()]||null}},text:function(){return Promise.resolve(r.body)},json:function(){return Promise.resolve(JSON.parse(r.body))}}};" +
        "async function __sdAxios(o){if(typeof o==='string')o={url:o};o=o||{};var u=String(o.url||'');if(o.params){var q=Object.keys(o.params).map(function(k){return encodeURIComponent(k)+'='+encodeURIComponent(o.params[k])}).join('&');if(q)u+=(u.indexOf('?')>=0?'&':'?')+q}var body=o.data;if(body&&typeof body!=='string')body=JSON.stringify(body);var r=await fetch(u,{method:String(o.method||'GET').toUpperCase(),headers:o.headers||{},body:body});var t=await r.text();var data;try{data=JSON.parse(t)}catch(e){data=t}var response={data:data,status:r.status,statusText:'',headers:r.headers,config:o,request:null};if(!r.ok){var error=new Error('Request failed with status code '+r.status);error.response=response;throw error}return response};__sdAxios.get=function(u,o){return __sdAxios(Object.assign({},o||{},{url:u,method:'GET'}))};__sdAxios.post=function(u,d,o){return __sdAxios(Object.assign({},o||{},{url:u,data:d,method:'POST'}))};__sdAxios.request=__sdAxios;__sdAxios.create=function(defaults){var client=function(o){return __sdAxios(Object.assign({},defaults||{},o||{}))};client.get=__sdAxios.get;client.post=__sdAxios.post;client.request=client;return client};__sdAxios.default=__sdAxios;" +        "var module={exports:{}};var exports=module.exports;(function(){" + normalizePluginJavaScript(provider.code) + "})();"
      // Compiling this ~5KB+ shim/boilerplate/provider-source blob to bytecode is the
      // expensive part of each streams() call; cache it per provider so repeat requests
      // (e.g. re-opening a detail page) only re-run the cheap per-call bytecode below.
      val providerBytecode = providerBytecodeCache.getOrPut("${provider.id}:${provider.code.hashCode()}") {
        compile(providerSource, "provider.js", false)
      }
      evaluate<Any?>(providerBytecode)
      val settingsJson = JSONObject(providerSettings(provider.id)).toString()
      evaluate<Any?>("globalThis.SCRAPER_SETTINGS=$settingsJson;globalThis.global.SCRAPER_SETTINGS=globalThis.SCRAPER_SETTINGS;")
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
        add(AddonStream("plugin:" + provider.id, provider.name, item.optString("name").ifBlank { provider.name }, item.optString("title").ifBlank { provider.name }, item.optString("description").ifBlank { null }, url, hash, item.optInt("fileIdx").takeIf { item.has("fileIdx") }, item.optString("filename").ifBlank { null }, item.optString("quality").ifBlank { null }, item.optString("size").ifBlank { null }, emptyList(), requestHeaders = buildMap {
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
    val entries = manifest.optJSONArray("scrapers") ?: throw IllegalArgumentException("No providers in repository.")
    val providers = mutableListOf<PluginProvider>()
    val skipped = mutableListOf<String>()
    for (index in 0 until entries.length()) {
      val item = entries.optJSONObject(index) ?: continue
      val key = item.optString("id")
      val file = item.optString("filename")
      if (key.isBlank() || file.isBlank()) continue
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
      providers += PluginProvider(providerId, url, item.optString("name").ifBlank { key }, buildList {
        if (types != null) for (typeIndex in 0 until types.length()) types.optString(typeIndex).takeIf { it.isNotBlank() }?.let(::add)
        if (isEmpty()) addAll(listOf("movie", "tv"))
      }, item.optBoolean("enabled", true) && (previous[providerId]?.enabled ?: true), code, item.optBoolean("hasSettings", false))
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
    prefs.edit().putString(storageKey, serialize(state)).apply()
  }

  fun restoreCloudState(raw: String) {
    val cachedProviders = state.providers.associateBy { "${it.repoUrl}:${it.id}" }
    val cloudState = parse(raw)
    state = cloudState.copy(providers = cloudState.providers.map { provider ->
      if (provider.code.isNotBlank()) provider
      else provider.copy(code = cachedProviders["${provider.repoUrl}:${provider.id}"]?.code.orEmpty())
    })
    prefs.edit().putString(storageKey, serialize(state)).apply()
  }

  private fun serialize(value: PluginState, includeCode: Boolean = true): String {
    val root = JSONObject().put("enabled", value.enabled).put("updatedAt", value.updatedAt)
    root.put("repos", JSONArray().apply { value.repos.forEach { put(JSONObject().put("url", it.url).put("name", it.name).put("version", it.version).put("description", it.description).put("enabled", it.enabled)) } })
    root.put("providers", JSONArray().apply { value.providers.forEach { put(JSONObject().put("id", it.id).put("repo", it.repoUrl).put("name", it.name).put("types", JSONArray(it.types)).put("enabled", it.enabled).put("code", if (includeCode) it.code else "").put("hasSettings", it.hasSettings)) } })
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
    PluginState(root.optBoolean("enabled", true), List(repos.length()) { repos.getJSONObject(it).run { PluginRepo(getString("url"), getString("name"), getString("version"), optString("description").ifBlank { null }, optBoolean("enabled", true)) } }, List(providers.length()) {
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
