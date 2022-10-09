import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

object ServiceGenerator {

    val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()
    private val converterFactory: Converter.Factory =
        JacksonConverterFactory.create(ObjectMapper().registerKotlinModule())
    private val errorBodyConverter = converterFactory.responseBodyConverter(
        GateIoApiException.GateIoApiError::class.java,
        arrayOfNulls(0),
        null
    ) as Converter<ResponseBody, GateIoApiException.GateIoApiError>

    fun <S> createService(serviceClass: Class<S>, apiKey: String?, secret: String?, baseUrl: String): S {
        val retrofitBuilder = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(converterFactory)
//            .addConverterFactory(EnumConverterFactory())
        if (null !== apiKey && null !== secret) {
            val adaptedClient = GateApiV4Auth(apiKey, secret).let {
                client.newBuilder().addInterceptor(it).build()
            }
            retrofitBuilder.client(adaptedClient)
        } else {
            retrofitBuilder.client(client)
        }
        return retrofitBuilder.build().create(serviceClass)
    }

    /**
     * Execute a REST call and block until the response is received.
     */
    fun <T> executeSync(call: Call<T>): Response<T> {
        val response = call.execute()
        if (response.isSuccessful) {
            return response
        } else {
            val apiError = errorBodyConverter.convert(response.errorBody()!!)!!
            throw GateIoApiException(call.request(), response, apiError)
        }
    }

    class GateIoApiException(
        val request: Request,
        val response: Response<*>,
        val apiError: GateIoApiError
    ) : RuntimeException() {

        override val message =
            "Http code: ${response.code()}. Label: \"${apiError.label}\". Message: \"${apiError.message}\". Status: \"${apiError.status}\""

        @JsonIgnoreProperties(ignoreUnknown = false)
        data class GateIoApiError(
            @JsonProperty("label") val label: String,
            @JsonProperty("message") val message: String,
            @JsonProperty("status") val status: Int? = null,
        )
    }

    class EnumConverterFactory : Converter.Factory() {
        override fun stringConverter(
            type: Type,
            annotations: Array<out Annotation>,
            retrofit: Retrofit
        ): Converter<*, String>? {
            if (type is Class<*> && type.isEnum) {
                return Converter<Any?, String> { value -> getSerializedNameValue(value as Enum<*>) }
            }
            return null
        }
    }

    fun <E : Enum<*>> getSerializedNameValue(e: E): String {
        try {
            return e.javaClass.getField(e.name).getAnnotation(JsonProperty::class.java).value
        } catch (exception: NoSuchFieldException) {
            exception.printStackTrace()
        }
        return ""
    }
}