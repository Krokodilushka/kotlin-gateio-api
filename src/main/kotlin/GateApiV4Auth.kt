import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.IOException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val HEADER_AUTH_KEY = "NEED_AUTH"
const val HEADER_AUTH_VALUE = "true"
const val HEADER_AUTH_RETROFIT_HEADER = "$HEADER_AUTH_KEY: $HEADER_AUTH_VALUE"

class GateApiV4Auth(private val apiKey: String, private val apiSecret: String) : Interceptor {

    private fun bodyToString(body: RequestBody?): String {
        return if (body == null) {
            ""
        } else try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        }
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (HEADER_AUTH_VALUE != request.header(HEADER_AUTH_KEY)) {
            return chain.proceed(request)
        }
        val ts = (System.currentTimeMillis() / 1000).toString()
        val bodyString = bodyToString(request.body())
        val queryString = if (request.url().query() == null) "" else request.url().query()!!
        val signatureString = String.format(
            "%s\n%s\n%s\n%s\n%s",
            request.method(),
            request.url().encodedPath(),
            queryString,
            DigestUtils.sha512Hex(bodyString),
            ts
        )
        return try {
            val hmacSha512 = Mac.getInstance("HmacSHA512")
            val spec = SecretKeySpec(apiSecret.toByteArray(), "HmacSHA512")
            hmacSha512.init(spec)
            val signature = Hex.encodeHexString(hmacSha512.doFinal(signatureString.toByteArray()))
            val newRequest = request.newBuilder()
                .removeHeader(HEADER_AUTH_KEY)
                .addHeader("KEY", apiKey)
                .addHeader("SIGN", signature)
                .addHeader("Timestamp", ts)
                .build()
            chain.proceed(newRequest)
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
            chain.proceed(request)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            chain.proceed(request)
        }
    }

}