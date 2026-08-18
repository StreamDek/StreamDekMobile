package net.streamdek.mobile.nativeapp

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Host surface for the QuickJS plugin sandbox, split out of [StreamDekPluginManager] so the
 * scraper-facing JavaScript environment can be read as one thing.
 *
 * These sources are written against a browser/React-Native environment, so anything they reach for
 * that QuickJS does not ship has to be supplied here. The bar is "what a Nuvio-style scraper
 * actually calls": `crypto-js`, `atob`/`btoa`, `TextEncoder`, `URL`, `AbortController`. A missing
 * one is not a nice error — it surfaces as a source that quietly returns no streams, because
 * scrapers wrap their work in `try { … } catch { return [] }`.
 *
 * Binary values cross the bridge as hex strings: QuickJS marshals numbers and strings cleanly but
 * not byte arrays, and hex survives the round trip without the size blow-up of a JSON int array.
 */

/** Real hashing, HMAC, PBKDF2 and block ciphers for the `crypto-js` shim in [PLUGIN_POLYFILLS]. */
internal fun QuickJs.installPluginCryptoBridge() {
  function("__sd_digest_hex") { args: Array<Any?> ->
    runCatching {
      val algorithm = pluginDigestAlgorithm(args.getOrNull(0)?.toString().orEmpty())
      java.security.MessageDigest.getInstance(algorithm).digest(pluginHexToBytes(args.getOrNull(1))).toPluginHex()
    }.getOrDefault("")
  }
  function("__sd_hmac_hex") { args: Array<Any?> ->
    runCatching {
      val algorithm = pluginHmacAlgorithm(args.getOrNull(0)?.toString().orEmpty())
      val key = pluginHexToBytes(args.getOrNull(1))
      Mac.getInstance(algorithm).apply { init(SecretKeySpec(key.ifEmptyByte(), algorithm)) }
        .doFinal(pluginHexToBytes(args.getOrNull(2))).toPluginHex()
    }.getOrDefault("")
  }
  function("__sd_pbkdf2_hex") { args: Array<Any?> ->
    runCatching {
      // Rolled by hand rather than via SecretKeyFactory: PBEKeySpec takes a char[], and crypto-js
      // callers pass raw key bytes that would be mangled by the char conversion.
      val password = pluginHexToBytes(args.getOrNull(0))
      val salt = pluginHexToBytes(args.getOrNull(1))
      val iterations = (args.getOrNull(2) as? Number)?.toInt() ?: 1000
      val keyBits = (args.getOrNull(3) as? Number)?.toInt() ?: 256
      val algorithm = pluginHmacAlgorithm(args.getOrNull(4)?.toString().orEmpty())
      pluginPbkdf2(algorithm, password, salt, iterations, (keyBits + 7) / 8).toPluginHex()
    }.getOrDefault("")
  }
  function("__sd_cipher_hex") { args: Array<Any?> ->
    runCatching {
      val encrypt = args.getOrNull(0) as? Boolean ?: false
      // Already a JCA transformation ("AES/CBC/PKCS5Padding") — the shim maps crypto-js mode and
      // padding objects onto it, so nothing here has to know about CryptoJS.mode.
      val transformation = args.getOrNull(1)?.toString().orEmpty()
      val key = pluginHexToBytes(args.getOrNull(2))
      val iv = pluginHexToBytes(args.getOrNull(3))
      val data = pluginHexToBytes(args.getOrNull(4))
      val cipher = Cipher.getInstance(transformation)
      val spec = SecretKeySpec(key, transformation.substringBefore('/'))
      val mode = if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
      if (iv.isEmpty()) cipher.init(mode, spec) else cipher.init(mode, spec, IvParameterSpec(iv))
      cipher.doFinal(data).toPluginHex()
    }.getOrDefault("")
  }
  function("__sd_random_hex") { args: Array<Any?> ->
    val length = (args.getOrNull(0) as? Number)?.toInt() ?: 0
    ByteArray(length).also { java.security.SecureRandom().nextBytes(it) }.toPluginHex()
  }
}

private fun ByteArray.ifEmptyByte(): ByteArray = if (isEmpty()) ByteArray(1) else this

private fun ByteArray.toPluginHex(): String = buildString(size * 2) {
  this@toPluginHex.forEach { byte -> append("0123456789abcdef"[(byte.toInt() shr 4) and 0xf]).append("0123456789abcdef"[byte.toInt() and 0xf]) }
}

internal fun pluginHexToBytes(raw: Any?): ByteArray {
  val hex = raw?.toString().orEmpty().filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
  val length = hex.length / 2
  return ByteArray(length) { index -> ((Character.digit(hex[index * 2], 16) shl 4) or Character.digit(hex[index * 2 + 1], 16)).toByte() }
}

private fun pluginDigestAlgorithm(name: String): String = when (name.uppercase().replace("-", "")) {
  "MD5" -> "MD5"
  "SHA1" -> "SHA-1"
  "SHA224" -> "SHA-224"
  "SHA384" -> "SHA-384"
  "SHA512" -> "SHA-512"
  else -> "SHA-256"
}

private fun pluginHmacAlgorithm(name: String): String = when (name.uppercase().replace("-", "").removePrefix("HMAC")) {
  "MD5" -> "HmacMD5"
  "SHA1" -> "HmacSHA1"
  "SHA224" -> "HmacSHA224"
  "SHA384" -> "HmacSHA384"
  "SHA512" -> "HmacSHA512"
  else -> "HmacSHA256"
}

private fun pluginPbkdf2(algorithm: String, password: ByteArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
  val mac = Mac.getInstance(algorithm).apply { init(SecretKeySpec(password.ifEmptyByte(), algorithm)) }
  val blockSize = mac.macLength
  val output = ByteArray(keyBytes)
  var written = 0
  var block = 1
  while (written < keyBytes) {
    mac.update(salt)
    mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
    var current = mac.doFinal()
    val accumulated = current.copyOf()
    repeat(iterations - 1) {
      current = mac.doFinal(current)
      for (index in accumulated.indices) accumulated[index] = (accumulated[index].toInt() xor current[index].toInt()).toByte()
    }
    val take = minOf(blockSize, keyBytes - written)
    accumulated.copyInto(output, written, 0, take)
    written += take
    block++
  }
  return output
}

/**
 * The web/Node surface QuickJS itself does not provide, plus a `crypto-js` implementation backed by
 * [installPluginCryptoBridge].
 *
 * Evaluated once per sandbox, ahead of the scraper's own code, and cached as bytecode — it is
 * constant, so it never needs recompiling per provider.
 */
internal val PLUGIN_POLYFILLS = """
  function __sdHex(bytes){var out='';for(var i=0;i<bytes.length;i++)out+=('0'+(bytes[i]&255).toString(16)).slice(-2);return out}
  function __sdUnhex(hex){hex=String(hex||'').replace(/[^0-9a-fA-F]/g,'');var out=[];for(var i=0;i+1<hex.length;i+=2)out.push(parseInt(hex.substr(i,2),16));return out}
  function __sdUtf8Bytes(value){return JSON.parse(__sd_utf8_encode(String(value==null?'':value)))}

  if(typeof globalThis.atob==='undefined')globalThis.atob=function(value){var bytes=__sdB64Bytes(value);var out='';for(var i=0;i<bytes.length;i++)out+=String.fromCharCode(bytes[i]);return out};
  if(typeof globalThis.btoa==='undefined')globalThis.btoa=function(value){value=String(value);var bytes=[];for(var i=0;i<value.length;i++)bytes.push(value.charCodeAt(i)&255);return __sdB64Encode(bytes)};

  if(typeof globalThis.TextEncoder==='undefined'){
    globalThis.TextEncoder=function(){};
    globalThis.TextEncoder.prototype.encode=function(value){return new Uint8Array(__sdUtf8Bytes(value))};
  }
  if(typeof globalThis.TextDecoder==='undefined'){
    globalThis.TextDecoder=function(){};
    globalThis.TextDecoder.prototype.decode=function(value){
      if(value==null)return '';
      var bytes=value.buffer?Array.prototype.slice.call(new Uint8Array(value.buffer,value.byteOffset||0,value.byteLength)):Array.prototype.slice.call(value);
      return __sd_utf8_decode(JSON.stringify(bytes));
    };
  }

  // Parsed in JS rather than through a host call: the shape scrapers use is href/origin/host/
  // pathname/searchParams, and a regex covers that without another bridge round trip.
  if(typeof globalThis.URLSearchParams==='undefined'){
    globalThis.URLSearchParams=function(init){
      this._pairs=[];
      var self=this;
      if(typeof init==='string'){
        String(init).replace(/^\?/,'').split('&').forEach(function(pair){
          if(!pair)return;
          var index=pair.indexOf('=');
          var key=index<0?pair:pair.slice(0,index);
          var value=index<0?'':pair.slice(index+1);
          self._pairs.push([decodeURIComponent(key.replace(/\+/g,' ')),decodeURIComponent(value.replace(/\+/g,' '))]);
        });
      } else if(init&&typeof init==='object'){
        if(Array.isArray(init))init.forEach(function(pair){if(pair&&pair.length>1)self._pairs.push([String(pair[0]),String(pair[1])])});
        else Object.keys(init).forEach(function(key){self._pairs.push([key,String(init[key])])});
      }
    };
    globalThis.URLSearchParams.prototype.append=function(key,value){this._pairs.push([String(key),String(value)])};
    globalThis.URLSearchParams.prototype.set=function(key,value){this.delete(key);this._pairs.push([String(key),String(value)])};
    globalThis.URLSearchParams.prototype.get=function(key){for(var i=0;i<this._pairs.length;i++)if(this._pairs[i][0]===String(key))return this._pairs[i][1];return null};
    globalThis.URLSearchParams.prototype.getAll=function(key){return this._pairs.filter(function(p){return p[0]===String(key)}).map(function(p){return p[1]})};
    globalThis.URLSearchParams.prototype.has=function(key){return this.get(key)!==null};
    globalThis.URLSearchParams.prototype.delete=function(key){this._pairs=this._pairs.filter(function(p){return p[0]!==String(key)})};
    globalThis.URLSearchParams.prototype.keys=function(){return this._pairs.map(function(p){return p[0]})};
    globalThis.URLSearchParams.prototype.values=function(){return this._pairs.map(function(p){return p[1]})};
    globalThis.URLSearchParams.prototype.entries=function(){return this._pairs.map(function(p){return [p[0],p[1]]})};
    globalThis.URLSearchParams.prototype.forEach=function(callback){var self=this;this._pairs.slice().forEach(function(p){callback(p[1],p[0],self)})};
    globalThis.URLSearchParams.prototype.sort=function(){this._pairs.sort(function(a,b){return a[0]<b[0]?-1:a[0]>b[0]?1:0})};
    globalThis.URLSearchParams.prototype.toString=function(){return this._pairs.map(function(p){return encodeURIComponent(p[0])+'='+encodeURIComponent(p[1])}).join('&')};
  }
  if(typeof globalThis.URL==='undefined'){
    globalThis.URL=function(input,base){
      var href=String(input);
      if(base&&!/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(href)){
        var root=String(base.href||base);
        if(href.charAt(0)==='/'){var origin=root.match(/^([a-zA-Z][a-zA-Z0-9+.-]*:\/\/[^\/?#]*)/);href=(origin?origin[1]:'')+href}
        else href=root.split('?')[0].split('#')[0].replace(/\/[^\/]*${'$'}/,'/')+href;
      }
      var parts=href.match(/^([a-zA-Z][a-zA-Z0-9+.-]*:)\/\/([^\/?#]*)([^?#]*)(\?[^#]*)?(#.*)?${'$'}/)||[];
      var authority=parts[2]||'';
      var hostPart=authority.indexOf('@')>=0?authority.slice(authority.indexOf('@')+1):authority;
      this.href=href;
      this.protocol=parts[1]||'';
      this.host=hostPart;
      this.hostname=hostPart.split(':')[0];
      this.port=hostPart.indexOf(':')>=0?hostPart.split(':')[1]:'';
      this.pathname=parts[3]||'/';
      this.search=parts[4]||'';
      this.hash=parts[5]||'';
      this.origin=this.protocol+'//'+this.host;
      this.searchParams=new URLSearchParams(this.search);
    };
    globalThis.URL.prototype.toString=function(){return this.href};
  }

  if(typeof globalThis.AbortSignal==='undefined'){
    globalThis.AbortSignal=function(){this.aborted=false;this.reason=undefined;this._listeners=[]};
    globalThis.AbortSignal.prototype.addEventListener=function(type,listener){if(type==='abort'&&typeof listener==='function')this._listeners.push(listener)};
    globalThis.AbortSignal.prototype.removeEventListener=function(type,listener){this._listeners=this._listeners.filter(function(l){return l!==listener})};
    globalThis.AbortSignal.prototype.dispatchEvent=function(event){this._listeners.slice().forEach(function(l){try{l(event)}catch(e){}});return true};
    globalThis.AbortSignal.prototype.throwIfAborted=function(){if(this.aborted)throw this.reason||new Error('Aborted')};
  }
  if(typeof globalThis.AbortController==='undefined'){
    globalThis.AbortController=function(){this.signal=new AbortSignal()};
    globalThis.AbortController.prototype.abort=function(reason){
      if(this.signal.aborted)return;
      this.signal.aborted=true;this.signal.reason=reason;
      this.signal.dispatchEvent({type:'abort'});
    };
  }

  if(!Array.prototype.flat)Array.prototype.flat=function(depth){depth=depth===undefined?1:Math.floor(depth);return depth<1?Array.prototype.slice.call(this):(function flatten(list,level){return level>0?list.reduce(function(acc,item){return acc.concat(Array.isArray(item)?flatten(item,level-1):item)},[]):list.slice()})(this,depth)};
  if(!Array.prototype.flatMap)Array.prototype.flatMap=function(callback,thisArg){return this.map(callback,thisArg).flat()};
  if(!Object.entries)Object.entries=function(source){return Object.keys(source||{}).map(function(key){return [key,source[key]]})};
  if(!Object.fromEntries)Object.fromEntries=function(entries){var out={};(entries||[]).forEach(function(entry){out[entry[0]]=entry[1]});return out};
  if(!String.prototype.replaceAll)String.prototype.replaceAll=function(search,replace){return search instanceof RegExp?this.replace(search,replace):this.split(search).join(replace)};

  // ── crypto-js ────────────────────────────────────────────────────────────────────────────────
  function __sdWordArray(words,sigBytes){
    var value={words:(words||[]).slice(),sigBytes:sigBytes===undefined?(words||[]).length*4:Number(sigBytes)};
    value.bytes=function(){return __sdWordsToBytes(value.words,value.sigBytes)};
    value.clone=function(){return __sdWordArray(value.words,value.sigBytes)};
    value.concat=function(other){var joined=value.bytes().concat(other&&other.bytes?other.bytes():[]);value.words=__sdBytesToWords(joined);value.sigBytes=joined.length;return value};
    value.toString=function(encoder){return (encoder||__sdCrypto.enc.Hex).stringify(value)};
    return value;
  }
  function __sdFromBytes(bytes){bytes=bytes||[];return __sdWordArray(__sdBytesToWords(bytes),bytes.length)}
  // Accepts anything crypto-js accepts as a message: a WordArray, a plain string, or raw bytes.
  function __sdToBytes(value){
    if(value==null)return [];
    if(typeof value==='string')return __sdUtf8Bytes(value);
    if(typeof value.bytes==='function')return value.bytes();
    if(value.words)return __sdWordsToBytes(value.words,value.sigBytes===undefined?value.words.length*4:value.sigBytes);
    if(typeof value.length==='number')return Array.prototype.slice.call(value);
    return [];
  }

  var __sdCrypto={
    enc:{
      Hex:{stringify:function(wordArray){return __sdHex(__sdToBytes(wordArray))},parse:function(value){return __sdFromBytes(__sdUnhex(value))}},
      Utf8:{stringify:function(wordArray){return __sd_utf8_decode(JSON.stringify(__sdToBytes(wordArray)))},parse:function(value){return __sdFromBytes(__sdUtf8Bytes(value))}},
      Latin1:{
        stringify:function(wordArray){var bytes=__sdToBytes(wordArray),out='';for(var i=0;i<bytes.length;i++)out+=String.fromCharCode(bytes[i]);return out},
        parse:function(value){value=String(value||'');var bytes=[];for(var i=0;i<value.length;i++)bytes.push(value.charCodeAt(i)&255);return __sdFromBytes(bytes)}
      },
      Base64:{stringify:function(wordArray){return __sdB64Encode(__sdToBytes(wordArray))},parse:function(value){return __sdFromBytes(__sdB64Bytes(value))}},
      Base64url:{
        stringify:function(wordArray){return __sdB64Encode(__sdToBytes(wordArray)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+${'$'}/,'')},
        parse:function(value){value=String(value||'').replace(/-/g,'+').replace(/_/g,'/');while(value.length%4)value+='=';return __sdFromBytes(__sdB64Bytes(value))}
      }
    },
    lib:{
      WordArray:{
        create:function(words,sigBytes){
          if(words==null)return __sdWordArray([],0);
          if(typeof words==='string')return __sdCrypto.enc.Utf8.parse(words);
          if(words.words)return __sdWordArray(words.words,sigBytes===undefined?words.sigBytes:sigBytes);
          if(typeof words[0]==='number'&&sigBytes===undefined)return __sdWordArray(words);
          return __sdWordArray(words,sigBytes);
        },
        random:function(count){return __sdFromBytes(__sdUnhex(__sd_random_hex(Number(count)||0)))}
      },
      // Note the hasOwnProperty test: every object inherits a truthy toString, so a `||` fallback
      // here silently leaves Object.prototype's in place and the ciphertext stringifies to
      // "[object Object]" — which then decrypts to nothing at all.
      CipherParams:{create:function(params){
        params=params||{};
        if(!Object.prototype.hasOwnProperty.call(params,'toString'))params.toString=function(formatter){return (formatter||__sdCrypto.format.OpenSSL).stringify(this)};
        return params;
      }}
    },
    mode:{CBC:'CBC',ECB:'ECB',CFB:'CFB',OFB:'OFB',CTR:'CTR'},
    pad:{Pkcs7:'Pkcs7',NoPadding:'NoPadding',ZeroPadding:'NoPadding'},
    algo:{MD5:'MD5',SHA1:'SHA1',SHA256:'SHA256',SHA384:'SHA384',SHA512:'SHA512'}
  };

  __sdCrypto.format={OpenSSL:{
    stringify:function(params){
      var cipher=__sdToBytes(params.ciphertext);
      if(!params.salt)return __sdB64Encode(cipher);
      return __sdB64Encode([83,97,108,116,101,100,95,95].concat(__sdToBytes(params.salt)).concat(cipher));
    },
    parse:function(value){
      var bytes=__sdB64Bytes(value);
      var salted=bytes.length>16&&bytes[0]===83&&bytes[1]===97&&bytes[2]===108&&bytes[3]===116&&bytes[4]===101&&bytes[5]===100&&bytes[6]===95&&bytes[7]===95;
      if(salted)return __sdCrypto.lib.CipherParams.create({salt:__sdFromBytes(bytes.slice(8,16)),ciphertext:__sdFromBytes(bytes.slice(16))});
      return __sdCrypto.lib.CipherParams.create({ciphertext:__sdFromBytes(bytes)});
    }
  }};

  function __sdDigest(name,message){return __sdFromBytes(__sdUnhex(__sd_digest_hex(name,__sdHex(__sdToBytes(message)))))}
  function __sdHmac(name,message,key){return __sdFromBytes(__sdUnhex(__sd_hmac_hex(name,__sdHex(__sdToBytes(key)),__sdHex(__sdToBytes(message)))))}
  ['MD5','SHA1','SHA224','SHA256','SHA384','SHA512'].forEach(function(name){
    __sdCrypto[name]=function(message){return __sdDigest(name,message)};
    __sdCrypto['Hmac'+name]=function(message,key){return __sdHmac(name,message,key)};
  });
  __sdCrypto.PBKDF2=function(password,salt,options){
    options=options||{};
    var bits=(options.keySize||4)*32;
    var hex=__sd_pbkdf2_hex(__sdHex(__sdToBytes(password)),__sdHex(__sdToBytes(salt)),Number(options.iterations||1000),bits,String(options.hasher||'SHA1'));
    return __sdFromBytes(__sdUnhex(hex));
  };

  // OpenSSL's key derivation for passphrase-keyed ciphers — CryptoJS.AES.decrypt(data, 'secret')
  // derives key and IV this way, and scrapers do use the passphrase form.
  function __sdEvpKdf(passwordBytes,saltBytes,keyBytes,ivBytes){
    var derived=[],block=[];
    while(derived.length<keyBytes+ivBytes){
      block=__sdUnhex(__sd_digest_hex('MD5',__sdHex(block.concat(passwordBytes).concat(saltBytes))));
      derived=derived.concat(block);
    }
    return {key:derived.slice(0,keyBytes),iv:derived.slice(keyBytes,keyBytes+ivBytes)};
  }

  function __sdTransformation(algorithm,options){
    var mode=String((options&&options.mode)||'CBC');
    var padding=String((options&&options.padding)||'Pkcs7');
    return algorithm+'/'+mode+'/'+(padding==='NoPadding'?'NoPadding':'PKCS5Padding');
  }

  function __sdCipher(algorithm,keySize,encrypt,input,key,options){
    options=options||{};
    var keyBytes,ivBytes,saltBytes=null;
    var params=(!encrypt&&typeof input==='string')?__sdCrypto.format.OpenSSL.parse(input):null;
    if(typeof key==='string'){
      saltBytes=options.salt?__sdToBytes(options.salt):(params&&params.salt?__sdToBytes(params.salt):__sdUnhex(__sd_random_hex(8)));
      var derived=__sdEvpKdf(__sdUtf8Bytes(key),saltBytes,keySize,16);
      keyBytes=derived.key;ivBytes=options.iv?__sdToBytes(options.iv):derived.iv;
    } else {
      keyBytes=__sdToBytes(key);ivBytes=options.iv?__sdToBytes(options.iv):[];
      saltBytes=null;
    }
    var dataBytes=encrypt?__sdToBytes(input):(params?__sdToBytes(params.ciphertext):(input&&input.ciphertext?__sdToBytes(input.ciphertext):__sdToBytes(input)));
    var outHex=__sd_cipher_hex(!!encrypt,__sdTransformation(algorithm,options),__sdHex(keyBytes),__sdHex(ivBytes),__sdHex(dataBytes));
    if(!encrypt)return __sdFromBytes(__sdUnhex(outHex));
    return __sdCrypto.lib.CipherParams.create({
      ciphertext:__sdFromBytes(__sdUnhex(outHex)),
      key:__sdFromBytes(keyBytes),
      iv:__sdFromBytes(ivBytes),
      salt:saltBytes?__sdFromBytes(saltBytes):undefined
    });
  }

  __sdCrypto.AES={
    encrypt:function(message,key,options){return __sdCipher('AES',32,true,message,key,options)},
    decrypt:function(cipher,key,options){return __sdCipher('AES',32,false,cipher,key,options)}
  };
  __sdCrypto.TripleDES={
    encrypt:function(message,key,options){return __sdCipher('DESede',24,true,message,key,options)},
    decrypt:function(cipher,key,options){return __sdCipher('DESede',24,false,cipher,key,options)}
  };
  __sdCrypto.DES={
    encrypt:function(message,key,options){return __sdCipher('DES',8,true,message,key,options)},
    decrypt:function(cipher,key,options){return __sdCipher('DES',8,false,cipher,key,options)}
  };
  globalThis.CryptoJS=__sdCrypto;
""".trimIndent()
