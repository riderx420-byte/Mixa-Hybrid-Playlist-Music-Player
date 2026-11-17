package com.example.data.extensions

import android.util.Base64
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.example.domain.model.InstalledExtension
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Sandboxed embedded QuickJS runtime for executing Mornye-style .sflx JavaScript providers.
 * Enforces execution timeouts, isolated threads, and manifest host allowlists on every HTTP request.
 */
class QuickJsExtensionRuntime(
    private val okHttpClient: OkHttpClient,
    private val secretsStore: ExtensionSecretsStore
) {
    suspend fun invokeHook(
        extension: InstalledExtension,
        scriptSource: String,
        hookNames: List<String>,
        argsJsonArray: String,
        timeoutMs: Long = 15_000L
    ): String {
        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "sonora-qjs-${extension.id}").apply { isDaemon = true }
        }
        val dispatcher = executor.asCoroutineDispatcher()
        val quickJs = QuickJs.create(dispatcher)
        val asyncResult = CompletableDeferred<String>()
        return try {
            withTimeout(timeoutMs) {
                bindHostApis(quickJs, extension, asyncResult)
                val bootstrapAndCall = buildString {
                    appendLine(JS_RUNTIME_SHIM)
                    appendLine("var module = { exports: {} };")
                    appendLine("var exports = module.exports;")
                    appendLine("var __sonoraRegisteredExtension = null;")
                    appendLine(
                        """
                        function registerExtension(extObj) {
                          __sonoraRegisteredExtension = extObj || {};
                          return __sonoraRegisteredExtension;
                        }
                        function registerProvider(extObj) { return registerExtension(extObj); }
                        function registerPlugin(extObj) { return registerExtension(extObj); }
                        """.trimIndent()
                    )
                    appendLine(scriptSource)
                    appendLine(
                        """
                        function __sonoraCollectScopes() {
                          return [
                            __sonoraRegisteredExtension,
                            module && module.exports ? module.exports : null,
                            module && module.exports && module.exports.default ? module.exports.default : null,
                            exports,
                            globalThis.extension,
                            globalThis.plugin,
                            globalThis.provider,
                            globalThis
                          ].filter(Boolean);
                        }

                        function __sonoraCallTargetHook(candidates, args) {
                          const scopes = __sonoraCollectScopes();
                          for (const name of candidates) {
                            for (const target of scopes) {
                              const fn = target && target[name];
                              if (typeof fn === "function") {
                                return { found: true, value: fn.apply(target, args) };
                              }
                            }
                          }
                          return { found: false, value: null };
                        }

                        function __sonoraFormatError(err) {
                          if (!err) return "Unknown extension error";
                          if (typeof err === "string") return err;
                          if (err.message) return String(err.message);
                          if (err.stack) return String(err.stack);
                          return String(err);
                        }

                        function __sonoraFinalizeValue(val) {
                          if (val && typeof val.then === "function") {
                            val.then(
                              (resolved) => __sonora_resolve(resolved === undefined ? "null" : JSON.stringify(resolved)),
                              (err) => __sonora_reject(__sonoraFormatError(err))
                            );
                            return "__SONORA_ASYNC_PENDING__";
                          }
                          const syncJson = (val === undefined) ? "null" : JSON.stringify(val);
                          __sonora_resolve(syncJson);
                          return syncJson;
                        }

                        function __sonoraDispatch(candidates, rawArgsJson) {
                          try {
                            const args = JSON.parse(rawArgsJson || "[]");
                            const scopes = __sonoraCollectScopes();
                            let initOut = null;
                            for (const target of scopes) {
                              if (target && target !== globalThis && typeof target.initialize === "function") {
                                const config = JSON.parse(__sonora_get_all_settings() || "{}");
                                initOut = target.initialize(config);
                                break;
                              }
                            }
                            if (initOut && typeof initOut.then === "function") {
                              initOut.then(
                                () => {
                                  try {
                                    const res = __sonoraCallTargetHook(candidates, args);
                                    if (!res.found) {
                                      __sonora_resolve("__SONORA_HOOK_MISSING__");
                                      return;
                                    }
                                    const v = res.value;
                                    if (v && typeof v.then === "function") {
                                      v.then(
                                        (resolved) => __sonora_resolve(resolved === undefined ? "null" : JSON.stringify(resolved)),
                                        (err) => __sonora_reject(__sonoraFormatError(err))
                                      );
                                    } else {
                                      __sonora_resolve(v === undefined ? "null" : JSON.stringify(v));
                                    }
                                  } catch (e) {
                                    __sonora_reject(__sonoraFormatError(e));
                                  }
                                },
                                (err) => __sonora_reject(__sonoraFormatError(err))
                              );
                              return "__SONORA_ASYNC_PENDING__";
                            }
                            const res = __sonoraCallTargetHook(candidates, args);
                            if (!res.found) {
                              __sonora_resolve("__SONORA_HOOK_MISSING__");
                              return "__SONORA_HOOK_MISSING__";
                            }
                            return __sonoraFinalizeValue(res.value);
                          } catch (e) {
                            __sonora_reject(__sonoraFormatError(e));
                            return "__SONORA_REJECTED__";
                          }
                        }
                        """.trimIndent()
                    )
                    val hookArrayLiteral = hookNames.joinToString(",") { JSONObject.quote(it) }
                    val quotedArgs = JSONObject.quote(argsJsonArray)
                    appendLine("__sonoraDispatch([$hookArrayLiteral], $quotedArgs);")
                }
                val immediate = quickJs.evaluate<String?>(bootstrapAndCall)
                when (immediate) {
                    "__SONORA_ASYNC_PENDING__", "__SONORA_REJECTED__" -> asyncResult.await()
                    null -> if (asyncResult.isCompleted) asyncResult.await() else "null"
                    else -> immediate
                }
            }
        } finally {
            runCatching { quickJs.close() }
            runCatching { dispatcher.close() }
            runCatching { executor.shutdownNow() }
        }
    }

    private fun bindHostApis(
        quickJs: QuickJs,
        extension: InstalledExtension,
        asyncResult: CompletableDeferred<String>
    ) {
        val allowedHosts = extension.manifest.allowedHosts

        quickJs.define("console") {
            function("log") { /* Silent sandbox console in production */ }
            function("warn") { /* Silent sandbox console in production */ }
            function("error") { /* Silent sandbox console in production */ }
            function("info") { /* Silent sandbox console in production */ }
            function("debug") { /* Silent sandbox console in production */ }
        }

        quickJs.function<String, String>("__sonora_resolve") { payload ->
            asyncResult.complete(payload)
            ""
        }

        quickJs.function<String, String>("__sonora_reject") { errMsg ->
            val ex = if (errMsg.contains("Blocked by extension sandbox")) {
                SecurityException(errMsg)
            } else {
                IllegalStateException(errMsg)
            }
            asyncResult.completeExceptionally(ex)
            ""
        }

        quickJs.function<String, String>("__sonora_get_setting") { key ->
            val defaultVal = extension.manifest.settingsSchema
                .firstOrNull { it.key == key }
                ?.defaultValue
                .orEmpty()
            secretsStore.getSetting(extension.id, key, defaultVal)
        }

        quickJs.function<String>("__sonora_set_setting") { args ->
            val key = args.getOrNull(0)?.toString().orEmpty()
            val value = args.getOrNull(1)?.toString().orEmpty()
            val isSecret = args.getOrNull(2)?.toString() == "true"
            if (key.isNotBlank()) {
                if (value.isEmpty() && args.getOrNull(1) == null) {
                    val schema = extension.manifest.settingsSchema.firstOrNull { it.key == key }
                    secretsStore.setSetting(
                        extension.id,
                        key,
                        "",
                        isSecret || schema?.type?.name == "SECRET"
                    )
                } else {
                    val schema = extension.manifest.settingsSchema.firstOrNull { it.key == key }
                    secretsStore.setSetting(
                        extension.id,
                        key,
                        value,
                        isSecret || schema?.type?.name == "SECRET"
                    )
                }
            }
            ""
        }

        quickJs.function<String>("__sonora_get_all_settings") { _ ->
            val map = linkedMapOf<String, String>()
            extension.manifest.settingsSchema.forEach { field ->
                if (field.defaultValue.isNotBlank()) {
                    map[field.key] = field.defaultValue
                }
            }
            map.putAll(secretsStore.getAllSettings(extension.id))
            JSONObject(map as Map<*, *>).toString()
        }

        quickJs.function<String, String>("__sonora_atob") { b64 ->
            String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        }

        quickJs.function<String, String>("__sonora_btoa") { raw ->
            Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }

        quickJs.function<String, String>("__sonora_md5") { input ->
            val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        }

        quickJs.function<String, String>("__sonora_sha256") { input ->
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        }

        quickJs.function<String>("__sonora_hmac") { args ->
            val algoRaw = args.getOrNull(0)?.toString().orEmpty().uppercase()
            val key = args.getOrNull(1)?.toString().orEmpty()
            val msg = args.getOrNull(2)?.toString().orEmpty()
            val javaAlgo = when {
                algoRaw.contains("512") -> "HmacSHA512"
                algoRaw.contains("1") && !algoRaw.contains("256") -> "HmacSHA1"
                else -> "HmacSHA256"
            }
            val mac = Mac.getInstance(javaAlgo)
            mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), javaAlgo))
            mac.doFinal(msg.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }

        quickJs.function<String, String>("__sonora_url_encode") { s ->
            URLEncoder.encode(s, "UTF-8")
        }

        quickJs.function<String, String>("__sonora_url_decode") { s ->
            runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
        }

        quickJs.function<String, String>("__sonora_sleep") { msStr ->
            val ms = msStr.toLongOrNull()?.coerceIn(0L, 5_000L) ?: 0L
            if (ms > 0L) runCatching { Thread.sleep(ms) }
            ""
        }

        quickJs.function<String, String>("__sonora_http_request") { reqJson ->
            executeSandboxedHttp(reqJson, allowedHosts)
        }
    }

    private fun executeSandboxedHttp(reqJson: String, allowedHosts: Set<String>): String {
        return try {
            val reqObj = JSONObject(reqJson)
            val url = reqObj.optString("url").trim()
            val parsedHost = runCatching { URI(url).host?.lowercase() }.getOrNull()
                ?: return JSONObject()
                    .put("securityError", true)
                    .put("error", "Malformed extension URL: $url")
                    .toString()

            if (allowedHosts.isNotEmpty() && !isHostAllowed(parsedHost, allowedHosts)) {
                return JSONObject()
                    .put("securityError", true)
                    .put("error", "Blocked by extension sandbox: host '$parsedHost' is not declared in manifest allowedHosts")
                    .toString()
            }

            val method = reqObj.optString("method", "GET").ifBlank { "GET" }.uppercase()
            val bodyStr = if (reqObj.has("body") && !reqObj.isNull("body")) {
                reqObj.opt("body")?.toString()
            } else {
                null
            }
            val headersObj = reqObj.optJSONObject("headers")
            var contentType = "application/json; charset=utf-8"
            var hasUserAgent = false

            val builder = Request.Builder().url(url)
            headersObj?.keys()?.forEach { key ->
                val v = headersObj.optString(key)
                if (key.equals("Content-Type", ignoreCase = true) && v.isNotBlank()) {
                    contentType = v
                }
                if (key.equals("User-Agent", ignoreCase = true) && v.isNotBlank()) {
                    hasUserAgent = true
                }
                if (v.isNotBlank()) {
                    builder.header(key, v)
                }
            }
            if (!hasUserAgent) {
                builder.header("User-Agent", DEFAULT_USER_AGENT)
            }

            val requestBody = when (method) {
                "POST", "PUT", "PATCH" -> (bodyStr ?: "").toRequestBody(contentType.toMediaTypeOrNull())
                "DELETE" -> bodyStr?.toRequestBody(contentType.toMediaTypeOrNull())
                else -> null
            }
            builder.method(method, requestBody)

            val responseJson = JSONObject()
            okHttpClient.newCall(builder.build()).execute().use { resp ->
                val text = runCatching { resp.body?.string().orEmpty() }.getOrElse {
                    throw IOException("Failed reading response from $parsedHost: ${it.message}")
                }
                responseJson.put("status", resp.code)
                responseJson.put("ok", resp.isSuccessful)
                responseJson.put("body", text)
                val respHeaders = JSONObject()
                resp.headers.names().forEach { name ->
                    respHeaders.put(name.lowercase(), resp.header(name).orEmpty())
                }
                responseJson.put("headers", respHeaders)
            }
            return responseJson.toString()
        } catch (t: Throwable) {
            JSONObject()
                .put("networkError", true)
                .put("error", t.message ?: t.javaClass.simpleName)
                .toString()
        }
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        fun isHostAllowed(host: String, allowedHosts: Set<String>): Boolean {
            val normalizedHost = host.lowercase().trim()
            return allowedHosts.any { allowedRaw ->
                val clean = allowedRaw.lowercase().trim().removePrefix("*.")
                normalizedHost == clean || normalizedHost.endsWith(".$clean")
            }
        }

        private val JS_RUNTIME_SHIM = """
            function atob(str) { return __sonora_atob(String(str)); }
            function btoa(str) { return __sonora_btoa(String(str)); }
            const log = {
              info: (...a) => console.log(...a),
              warn: (...a) => console.warn(...a),
              error: (...a) => console.error(...a),
              debug: (...a) => console.debug(...a)
            };
            const logger = log;

            const sonora = {
              getSetting: (k) => __sonora_get_setting(String(k)),
              setSetting: (k, v) => __sonora_set_setting(String(k), v == null ? "" : String(v), "false"),
              md5: (s) => __sonora_md5(String(s)),
              sha256: (s) => __sonora_sha256(String(s)),
              hmacSha256: (k, m) => __sonora_hmac("sha256", String(k), String(m))
            };

            const utils = {
              md5: (s) => __sonora_md5(String(s)),
              sha256: (s) => __sonora_sha256(String(s)),
              hmac: (algo, k, m) => __sonora_hmac(String(algo), String(k), String(m)),
              hmacSha256: (k, m) => __sonora_hmac("sha256", String(k), String(m)),
              hmacSHA256: (k, m) => __sonora_hmac("sha256", String(k), String(m)),
              hmacSha1: (k, m) => __sonora_hmac("sha1", String(k), String(m)),
              base64Encode: (s) => __sonora_btoa(String(s)),
              encodeBase64: (s) => __sonora_btoa(String(s)),
              base64Decode: (s) => __sonora_atob(String(s)),
              decodeBase64: (s) => __sonora_atob(String(s)),
              btoa: (s) => __sonora_btoa(String(s)),
              atob: (s) => __sonora_atob(String(s)),
              urlEncode: (s) => __sonora_url_encode(String(s)),
              urlDecode: (s) => __sonora_url_decode(String(s)),
              parseJSON: (s) => JSON.parse(String(s)),
              stringifyJSON: (o) => JSON.stringify(o),
              sleep: (ms) => __sonora_sleep(String(ms || 0)),
              randomUserAgent: () => "$DEFAULT_USER_AGENT"
            };

            const settings = {
              get: (k, defVal) => {
                const v = __sonora_get_setting(String(k));
                return (v === "" && defVal !== undefined) ? defVal : v;
              },
              set: (k, v) => __sonora_set_setting(String(k), v == null ? "" : String(v), "false"),
              getAll: () => JSON.parse(__sonora_get_all_settings() || "{}")
            };
            const config = settings;

            const storage = {
              get: (k, defVal) => {
                const v = __sonora_get_setting(String(k));
                if (v === "") return defVal !== undefined ? defVal : null;
                return v;
              },
              set: (k, v) => __sonora_set_setting(String(k), v == null ? "" : (typeof v === "string" ? v : JSON.stringify(v)), "false"),
              remove: (k) => __sonora_set_setting(String(k), "", "false"),
              delete: (k) => __sonora_set_setting(String(k), "", "false"),
              has: (k) => __sonora_get_setting(String(k)) !== ""
            };

            const credentials = {
              get: (k, defVal) => {
                const v = __sonora_get_setting(String(k));
                if (v === "") return defVal !== undefined ? defVal : null;
                return v;
              },
              set: (k, v) => __sonora_set_setting(String(k), v == null ? "" : String(v), "true"),
              remove: (k) => __sonora_set_setting(String(k), "", "true"),
              delete: (k) => __sonora_set_setting(String(k), "", "true"),
              has: (k) => __sonora_get_setting(String(k)) !== ""
            };

            const file = {
              exists: () => false,
              read: () => "",
              write: () => false,
              remove: () => false
            };

            function __sonoraNormalizeHeaders(headersOrOpts) {
              if (!headersOrOpts || typeof headersOrOpts !== "object") return {};
              if (headersOrOpts.headers && typeof headersOrOpts.headers === "object") {
                return headersOrOpts.headers;
              }
              return headersOrOpts;
            }

            const http = {
              request: (urlOrOpts, maybeOpts) => {
                let reqObj;
                if (typeof urlOrOpts === "string") {
                  const o = maybeOpts || {};
                  reqObj = {
                    url: urlOrOpts,
                    method: (o.method || "GET").toUpperCase(),
                    headers: o.headers || {},
                    body: o.body != null ? (typeof o.body === "string" ? o.body : JSON.stringify(o.body)) : null
                  };
                } else {
                  const o = urlOrOpts || {};
                  reqObj = {
                    url: String(o.url || ""),
                    method: (o.method || "GET").toUpperCase(),
                    headers: o.headers || {},
                    body: o.body != null ? (typeof o.body === "string" ? o.body : JSON.stringify(o.body)) : null
                  };
                }
                const res = JSON.parse(__sonora_http_request(JSON.stringify(reqObj)));
                if (res && (res.securityError || res.networkError || (res.error && res.status == null))) {
                  throw new Error(String(res.error));
                }
                const bodyText = res.body || "";
                return {
                  status: res.status,
                  statusCode: res.status,
                  code: res.status,
                  ok: Boolean(res.ok),
                  headers: res.headers || {},
                  body: bodyText,
                  data: bodyText,
                  text: async () => bodyText,
                  json: async () => JSON.parse(bodyText || "{}")
                };
              },
              get: (url, headersOrOpts) => http.request(url, {
                method: "GET",
                headers: __sonoraNormalizeHeaders(headersOrOpts)
              }),
              post: (url, body, headersOrOpts) => http.request(url, {
                method: "POST",
                body: body,
                headers: __sonoraNormalizeHeaders(headersOrOpts)
              }),
              put: (url, body, headersOrOpts) => http.request(url, {
                method: "PUT",
                body: body,
                headers: __sonoraNormalizeHeaders(headersOrOpts)
              }),
              patch: (url, body, headersOrOpts) => http.request(url, {
                method: "PATCH",
                body: body,
                headers: __sonoraNormalizeHeaders(headersOrOpts)
              }),
              delete: (url, headersOrOpts) => http.request(url, {
                method: "DELETE",
                headers: __sonoraNormalizeHeaders(headersOrOpts)
              })
            };

            async function fetch(url, opts) {
              const o = opts || {};
              return http.request(String(url), {
                method: (o.method || "GET").toUpperCase(),
                headers: o.headers || {},
                body: o.body != null ? (typeof o.body === "string" ? o.body : JSON.stringify(o.body)) : null
              });
            }

            if (typeof URLSearchParams === "undefined") {
              globalThis.URLSearchParams = function(init) {
                this._pairs = [];
                if (typeof init === "string") {
                  const s = init.startsWith("?") ? init.slice(1) : init;
                  if (s) {
                    s.split("&").forEach((part) => {
                      const idx = part.indexOf("=");
                      const k = idx >= 0 ? __sonora_url_decode(part.slice(0, idx)) : __sonora_url_decode(part);
                      const v = idx >= 0 ? __sonora_url_decode(part.slice(idx + 1)) : "";
                      this._pairs.push([k, v]);
                    });
                  }
                } else if (init && typeof init === "object") {
                  Object.keys(init).forEach((k) => {
                    if (init[k] !== undefined && init[k] !== null) {
                      this._pairs.push([String(k), String(init[k])]);
                    }
                  });
                }
              };
              URLSearchParams.prototype.append = function(k, v) { this._pairs.push([String(k), String(v)]); };
              URLSearchParams.prototype.set = function(k, v) {
                const key = String(k);
                this._pairs = this._pairs.filter((p) => p[0] !== key);
                this._pairs.push([key, String(v)]);
              };
              URLSearchParams.prototype.get = function(k) {
                const hit = this._pairs.find((p) => p[0] === String(k));
                return hit ? hit[1] : null;
              };
              URLSearchParams.prototype.has = function(k) { return this._pairs.some((p) => p[0] === String(k)); };
              URLSearchParams.prototype.toString = function() {
                return this._pairs.map((p) => __sonora_url_encode(p[0]) + "=" + __sonora_url_encode(p[1])).join("&");
              };
            }

            if (typeof TextEncoder === "undefined") {
              globalThis.TextEncoder = function() {};
              TextEncoder.prototype.encode = function(str) {
                const utf8 = unescape(encodeURIComponent(String(str)));
                const arr = new Uint8Array(utf8.length);
                for (let i = 0; i < utf8.length; i++) arr[i] = utf8.charCodeAt(i);
                return arr;
              };
            }
            if (typeof TextDecoder === "undefined") {
              globalThis.TextDecoder = function() {};
              TextDecoder.prototype.decode = function(buf) {
                const arr = buf instanceof Uint8Array ? buf : new Uint8Array(buf || []);
                let out = "";
                for (let i = 0; i < arr.length; i++) out += String.fromCharCode(arr[i]);
                try { return decodeURIComponent(escape(out)); } catch (_) { return out; }
              };
            }
            if (typeof setTimeout === "undefined") {
              globalThis.setTimeout = function(fn, ms) {
                try { if (typeof fn === "function") fn(); } catch (_) {}
                return 1;
              };
              globalThis.clearTimeout = function() {};
            }
        """.trimIndent()
    }
}
