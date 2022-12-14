package gateio

import com.fasterxml.jackson.annotation.JsonFormat
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
//        println(jsonString)
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
            val signatureString = String.format("channel=%s&event=%s&time=%d", channel, event.text, time)
            Auth(
                "api_key",
                authData.apiKey,
                GateApiV4Auth.sign(it.apiSecret, signatureString)
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
        val timeMs: Long?,
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
        val createTimeMs: String,
        val side: GateIoSpotClient.GateIoApiServiceSpot.OpenOrders.Order.Side,
        val amount: BigDecimal,
        val role: Role,
        val price: BigDecimal,
        val fee: BigDecimal,
        @JsonProperty("point_fee")
        val pointFee: BigDecimal,
        @JsonProperty("gt_fee")
        val gtFee: BigDecimal,
        @JsonProperty("fee_currency")
        val feeCurrency: String,
        val text: String
    ) {
        enum class Role {
            @JsonProperty("maker")
            MAKER,

            @JsonProperty("taker")
            TAKER
        }

        @JsonDeserialize(using = TradeEventListDeserializer::class)
        data class List(val trades: kotlin.collections.List<UserTrade>) : WebSocketEventSealed()

        class TradeEventListDeserializer : JsonDeserializer<List>() {

            private val typeReference = object : TypeReference<kotlin.collections.List<UserTrade>>() {}

            override fun deserialize(jp: JsonParser, ctx: DeserializationContext): List {
                val node = jp.codec.readTree<JsonNode>(jp)
                val json = node.toString()
                val list = JsonToObject.convert(json, typeReference)
                return List(list)
            }

        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Order(
        val id: Long,
        val user: Long,
        val text: String,
        @JsonProperty("create_time")
        val createTime: Long,
        @JsonProperty("create_time_ms")
        val createTimeMs: String,
        @JsonProperty("update_time")
        val updateTime: Long,
        @JsonProperty("update_time_ms")
        val updateTimeMs: Long,
        val event: Event,
        @JsonProperty("currency_pair")
        val currencyPair: String,
        val type: GateIoSpotClient.GateIoApiServiceSpot.OpenOrders.Order.Type,
        val account: GateIoSpotClient.GateIoApiServiceSpot.OpenOrders.Order.Account,
        val side: GateIoSpotClient.GateIoApiServiceSpot.OpenOrders.Order.Side,
        val amount: BigDecimal,
        val price: BigDecimal,
        @JsonProperty("time_in_force")
        val timeInForce: GateIoSpotClient.GateIoApiServiceSpot.OpenOrders.Order.TimeInForce,
        val left: BigDecimal,
        @JsonProperty("filled_total")
        val filledTotal: BigDecimal,
        @JsonProperty("avg_deal_price")
        val avgDealPrice: BigDecimal?,
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
        val rebatedFeeCurrency: String,
        @JsonProperty("auto_borrow")
        val autoBorrow: Boolean,
        @JsonProperty("auto_repay")
        val autoRepay: Boolean,
    ) {

        enum class Event {
            @JsonProperty("put")
            PUT,

            @JsonProperty("update")
            UPDATE,

            @JsonProperty("finish")
            FINISH
        }

        @JsonDeserialize(using = OrdersEventListDeserializer::class)
        data class List(val orders: kotlin.collections.List<Order>) : WebSocketEventSealed()

        class OrdersEventListDeserializer : JsonDeserializer<List>() {

            private val typeReference = object : TypeReference<kotlin.collections.List<Order>>() {}

            override fun deserialize(jp: JsonParser, ctx: DeserializationContext): List {
                val node = jp.codec.readTree<JsonNode>(jp)
                val json = node.toString()
                val list = JsonToObject.convert(json, typeReference)
                return List(list)
            }

        }
    }

    data class CrossBalance(
        val timestamp: Long,
        @JsonProperty("timestamp_ms")
        val timestampMs: Long,
        val user: Long,
        val currency: String,
        val change: BigDecimal,
        val total: BigDecimal,
        val available: BigDecimal
    ) {
        @JsonDeserialize(using = CrossBalancesEventListDeserializer::class)
        data class List(val crossBalances: kotlin.collections.List<CrossBalance>) : WebSocketEventSealed()

        class CrossBalancesEventListDeserializer : JsonDeserializer<List>() {
            private val typeReference = object : TypeReference<kotlin.collections.List<CrossBalance>>() {}
            override fun deserialize(jp: JsonParser, ctx: DeserializationContext): List {
                val node = jp.codec.readTree<JsonNode>(jp)
                val json = node.toString()
                val list = JsonToObject.convert(json, typeReference)
                return List(list)
            }
        }
    }

    data class CrossLoan(
        val timestamp: Long,
        val user: Long,
        val currency: String,
        val change: BigDecimal,
        val total: BigDecimal,
        val available: BigDecimal,
        val borrowed: BigDecimal,
        val interest: BigDecimal
    ) : WebSocketEventSealed()


    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ChangedOrderBookLevels(
        @JsonProperty("t")
        val updateTime: Long,
        @JsonProperty("s")
        val symbol: String,
        @JsonProperty("U")
        val firstUpdateId: Long,
        @JsonProperty("u")
        val lastUpdateId: Long,
        @JsonProperty("b")
        val bids: List<PriceLevel>,
        @JsonProperty("a")
        val asks: List<PriceLevel>,
    ) : WebSocketEventSealed() {
        @JsonFormat(shape = JsonFormat.Shape.ARRAY)
        data class PriceLevel(val price: BigDecimal, val amount: BigDecimal)
    }

    data class SpotBalance(
        val timestamp: Long,
        @JsonProperty("timestamp_ms")
        val timestampMs: Long,
        val user: Long,
        val currency: String,
        val change: BigDecimal,
        val total: BigDecimal,
        val available: BigDecimal,
        val freeze: BigDecimal,
        @JsonProperty("freeze_change")
        val freezeChange: BigDecimal,
        @JsonProperty("change_type")
        val changeType: String,
    ) {
        @JsonDeserialize(using = SpotBalancesEventListDeserializer::class)
        data class List(val spotBalances: kotlin.collections.List<SpotBalance>) : WebSocketEventSealed()

        class SpotBalancesEventListDeserializer : JsonDeserializer<List>() {
            private val typeReference = object : TypeReference<kotlin.collections.List<SpotBalance>>() {}
            override fun deserialize(jp: JsonParser, ctx: DeserializationContext): List {
                val node = jp.codec.readTree<JsonNode>(jp)
                val json = node.toString()
                val list = JsonToObject.convert(json, typeReference)
                return List(list)
            }
        }
    }
}

class ServerEventDeserializer : JsonDeserializer<WebSocketEvent<*>>() {

    private val subscribeEvent = object : TypeReference<WebSocketEventSealed.SubscribeEvent>() {}
    private val ordersEvent = object : TypeReference<WebSocketEventSealed.Order.List>() {}
    private val crossBalancesEvent = object : TypeReference<WebSocketEventSealed.CrossBalance.List>() {}
    private val spotBalancesEvent = object : TypeReference<WebSocketEventSealed.SpotBalance.List>() {}
    private val tradeEvent = object : TypeReference<WebSocketEventSealed.UserTrade.List>() {}

    override fun deserialize(jp: JsonParser, ctx: DeserializationContext): WebSocketEvent<*> {
        val node = jp.codec.readTree<JsonNode>(jp)
        val result = node["result"].toString()
        val channel = node["channel"].textValue()
        val event = when (val event = node["event"].textValue()) {
            "subscribe" -> JsonToObject.convert(result, subscribeEvent)
            "unsubscribe" -> JsonToObject.convert(result, subscribeEvent)
            "update" -> when (channel) {
                "spot.tickers" -> JsonToObject.convert(result, WebSocketEventSealed.Ticker::class.java)
                "spot.usertrades" -> JsonToObject.convert(result, tradeEvent)
                "spot.orders" -> JsonToObject.convert(result, ordersEvent)
                "spot.cross_balances" -> JsonToObject.convert(result, crossBalancesEvent)
                "spot.balances" -> JsonToObject.convert(result, spotBalancesEvent)
                "spot.cross_loan" -> JsonToObject.convert(result, WebSocketEventSealed.CrossLoan::class.java)
                "spot.order_book_update" -> JsonToObject.convert(
                    result,
                    WebSocketEventSealed.ChangedOrderBookLevels::class.java
                )

                else -> {
                    error("Channel \"$channel\" not found")
                }
            }

            else -> error("Event \"$event\" not found")
        }
        val res = WebSocketEventSealed.ServerEvent(
            time = node["time"].asLong(),
            timeMs = node["time_ms"]?.asLong(),
            id = node["id"]?.asLong(),
            channel = node["channel"].toString(),
            event = node["result"].toString(),
            error = null,
            result = event
        )
        return WebSocketEvent(res)
    }
}

object JsonToObject {
    private var mapper = ObjectMapper().registerKotlinModule()
    fun <T> convert(json: String?, clazz: Class<T>): T = mapper.readValue(json, clazz)
    fun <T> convert(json: String?, clazz: TypeReference<T>): T = mapper.readValue(json, clazz)
}

class GateIoApiWebSocketListener<T>(private val callback: GateIoWebSocketClient.WebSocketCallback<T>) :
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