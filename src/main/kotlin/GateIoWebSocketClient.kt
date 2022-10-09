import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import okhttp3.*
import java.math.BigDecimal

class GateIoWebSocketClient(
    private val host: String,
    private val client: OkHttpClient,
    private val listener: WebSocketListener
) {

    private var webSocket: WebSocket? = null
    private val objectMapper = ObjectMapper().registerKotlinModule()

    fun connect() {
        val request = okhttp3.Request.Builder().url("$host/ws/v4/").build()
        webSocket = client.newWebSocket(request, listener)
    }

    fun close() {
        webSocket?.let {
            val code = 1000
            listener.onClosing(it, code, "Closed by user")
            it.close(code, null)
            listener.onClosed(it, code, "Closed by user")
        }
    }

    fun send(message: Request) {
        val jsonString = objectMapper.writeValueAsString(message)
        webSocket?.send(jsonString)
    }

    interface WebSocketCallback<T> where T : WebSocketEventSealed {
        fun onEvent(eventWrapper: WebSocketEvent<T>)
        fun onFailure(cause: Throwable)
        fun onClosing(code: Int, reason: String) {}
    }

    data class Request(
        @JsonProperty("time")
        val time: Long,
        @JsonProperty("id")
        val id: Long? = null,
        @JsonProperty("channel")
        val channel: String,
        @JsonIgnore
        val authData: AuthData? = null,
        @JsonProperty("event")
        val event: Method,
        @JsonProperty("payload")
        val payload: List<String>? = null,
    ) {

        @JsonProperty("auth")
        val auth: Auth? = authData?.let {
            Auth(
                "api_key",
                authData.apiKey,
                GateApiV4Auth.sign(it.apiSecret, "channel=$channel&event=${event.text}&time=$time")
            )
        }

        data class Auth(
            @JsonProperty("method") val method: String,
            @JsonProperty("KEY") val key: String,
            @JsonProperty("SIGN") val sign: String,
        )

        data class AuthData(
            val apiKey: String,
            val apiSecret: String,
        )

        enum class Method(val text: String) {
            @JsonProperty("subscribe")
            SUBSCRIBE("subscribe"),

            @JsonProperty("unsubscribe")
            UNSUBSCRIBE("unsubscribe")
        }
    }
}

data class WebSocketEvent<T>(val serverEvent: WebSocketEventSealed.ServerEvent<T>) where T : WebSocketEventSealed

sealed class WebSocketEventSealed {

    @JsonDeserialize(using = ServerEventDeserializer::class)
    @JsonIgnoreProperties(ignoreUnknown = false)
    data class ServerEvent<T : WebSocketEventSealed>(
        val time: Long,
        val id: Long?,
        val channel: String,
        val event: String,
        val error: Error?,
        val result: T?,
    ) {
        data class Error(
            @JsonProperty("code")
            val code: Code,
            @JsonProperty("message")
            val message: String,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = false)
            enum class Code {
                @JsonProperty("1")
                INVALID_REQUEST_BODY_FORMAT,

                @JsonProperty("2")
                INVALID_ARGUMENT_PROVIDED,

                @JsonProperty("3")
                SERVER_SIDE_ERROR_HAPPENED
            }
        }
    }

    data class SubscribeEvent(
        @JsonProperty("status")
        val status: String
    ) : WebSocketEventSealed()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Ticker(
        @JsonProperty("currency_pair")
        val currencyPair: String,
        val last: BigDecimal,
        @JsonProperty("lowest_ask")
        val lowestAsk: BigDecimal? = null,
        @JsonProperty("highest_bid")
        val highestBid: BigDecimal? = null,
        @JsonProperty("change_percentage")
        val changePercentage: BigDecimal,
        @JsonProperty("base_volume")
        val baseVolume: BigDecimal,
        @JsonProperty("quote_volume")
        val quoteVolume: BigDecimal,
        @JsonProperty("high_24h")
        val high24h: BigDecimal,
        @JsonProperty("low_24h")
        val low24h: BigDecimal
    ) : WebSocketEventSealed()

    data class UserTrade(
        val id: Long,
        @JsonProperty("user_id")
        val userId: Long,
        @JsonProperty("order_id")
        val orderId: Long,
        @JsonProperty("currency_pair")
        val currencyPair: String,
        @JsonProperty("create_time")
        val createTime: Long,
        @JsonProperty("create_time_ms")
        val createTimeMs: Long,
        val side: GateIoSpotClient.KucoinApiServiceSpot.OpenOrders.Order.Side,
        val amount: BigDecimal,
        val role: String,
        val price: BigDecimal,
        val fee: BigDecimal,
        @JsonProperty("point_fee")
        val pointFee: BigDecimal,
        @JsonProperty("gt_fee")
        val gtFee: BigDecimal,
        val text: String
    ) : WebSocketEventSealed() {
        enum class Role {
            @JsonProperty("maker")
            MAKER,

            @JsonProperty("taker")
            TAKER
        }
    }

    data class Order(
        val id: Long,
        val user: Long,
        val text: String,
        @JsonProperty("create_time")
        val createTime: Long,
        @JsonProperty("create_time_ms")
        val createTimeMs: Long,
        @JsonProperty("update_time")
        val updateTime: Long,
        @JsonProperty("update_time_ms")
        val updateTimeMs: Long,
        val event: String,
        @JsonProperty("currency_pair")
        val currencyPair: String,
        val type: GateIoSpotClient.KucoinApiServiceSpot.OpenOrders.Order.Type,
        val account: GateIoSpotClient.KucoinApiServiceSpot.OpenOrders.Order.Account,
        val side: GateIoSpotClient.KucoinApiServiceSpot.OpenOrders.Order.Side,
        val amount: BigDecimal,
        val price: BigDecimal,
        @JsonProperty("time_in_force")
        val timeInForce: GateIoSpotClient.KucoinApiServiceSpot.OpenOrders.Order.TimeInForce,
        val left: BigDecimal,
        @JsonProperty("filled_total")
        val filledTotal: BigDecimal,
        val fee: BigDecimal,
        @JsonProperty("fee_currency")
        val feeCurrency: String,
        @JsonProperty("point_fee")
        val pointFee: BigDecimal,
        @JsonProperty("gt_fee")
        val gtFee: BigDecimal,
        @JsonProperty("gt_discount")
        val gtDiscount: Boolean,
        @JsonProperty("rebated_fee")
        val rebatedFee: BigDecimal,
        @JsonProperty("rebated_fee_currency")
        val rebatedFeeCurrency: BigDecimal
    ) : WebSocketEventSealed()

}

class ServerEventDeserializer : JsonDeserializer<WebSocketEvent<*>>() {

    private val subscribeEvent =
        object : TypeReference<WebSocketEventSealed.SubscribeEvent>() {}

    override fun deserialize(
        jp: JsonParser,
        ctx: DeserializationContext
    ): WebSocketEvent<*> {
        val jsonToObject = JsonToObject()
        val node = jp.codec.readTree<JsonNode>(jp)
        val result = node["result"].toString()
        val channel = node["channel"].textValue()
        val event = when (val event = node["event"].textValue()) {
            "subscribe" -> jsonToObject.convert(result, subscribeEvent)
            "unsubscribe" -> jsonToObject.convert(result, subscribeEvent)
            "update" -> when (channel) {
                "spot.tickers" -> jsonToObject.convert(result, WebSocketEventSealed.Ticker::class.java)
                "spot.usertrades" -> jsonToObject.convert(result, WebSocketEventSealed.UserTrade::class.java)
                "spot.orders" -> jsonToObject.convert(result, WebSocketEventSealed.Order::class.java)
                else -> {
                    error("Channel \"$channel\" not found")
                }
            }

            else -> error("Event \"$event\" not found")
        }
        val res = WebSocketEventSealed.ServerEvent(
            time = node["time"].asLong(),
            id = node["id"]?.asLong(),
            channel = node["channel"].toString(),
            event = node["result"].toString(),
            error = null,
            result = event
        )
        return WebSocketEvent(res)
    }

    class JsonToObject {
        private var mapper = ObjectMapper().registerKotlinModule()
        fun <T> convert(json: String?, clazz: Class<T>): T = mapper.readValue(json, clazz)
        fun <T> convert(json: String?, clazz: TypeReference<T>): T = mapper.readValue(json, clazz)
    }
}

class BinanceApiWebSocketListener<T>(private val callback: GateIoWebSocketClient.WebSocketCallback<T>) :
    WebSocketListener() where T : WebSocketEventSealed {

    private val mapper = ObjectMapper().registerKotlinModule()
    private val messageWrapperReader = mapper.readerFor(WebSocketEventSealed.ServerEvent::class.java)
    private var isClosed = false

    override fun onMessage(webSocket: WebSocket, text: String) {
        val msg = messageWrapperReader.readValue<WebSocketEvent<T>>(text)
        callback.onEvent(msg)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        isClosed = true
        callback.onClosing(code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (!isClosed) {
            callback.onFailure(t)
        }
    }

}