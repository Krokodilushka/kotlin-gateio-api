package gateio

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*
import java.math.BigDecimal

class GateIoSpotClient(
    apiKey: String?,
    secret: String?,
    baseUrl: String
) {

    private val service =
        ServiceGenerator.createService(GateIoApiServiceSpot::class.java, apiKey, secret, baseUrl)

    fun currencyPairs() = ServiceGenerator.executeSync(service.currencyPairs())

    fun openOrders(
        page: Int? = null,
        limit: Int? = null,
        account: GateIoApiServiceSpot.OpenOrders.Order.Account? = null
    ) = ServiceGenerator.executeSync(service.openOrders(page, limit, account?.text))

    fun createOrder(
        text: String? = null,
        currencyPair: String,
        type: GateIoApiServiceSpot.OpenOrders.Order.Type? = null,
        account: GateIoApiServiceSpot.OpenOrders.Order.Account? = null,
        side: GateIoApiServiceSpot.OpenOrders.Order.Side,
        amount: BigDecimal,
        price: BigDecimal,
        timeInForce: GateIoApiServiceSpot.OpenOrders.Order.TimeInForce? = null,
        iceberg: BigDecimal? = null,
        autoBorrow: Boolean? = null,
        autoRepay: Boolean? = null,
    ): Response<GateIoApiServiceSpot.OpenOrders.Order> {
        text?.also {
            if (!it.startsWith("t-")) {
                error("Text must starts with 't-'")
            }
//            val bytesCount = it.toByteArray().count()
//            if (bytesCount > 28) {
//                error("Text bytes must be <=28. Current: $bytesCount")
//            }
            val pattern = "[A-Za-z0-9_\\-.]+".toRegex()
            if (!it.matches(pattern)) {
                error("Text must be ${pattern.pattern}")
            }
        }
        if (null !== autoBorrow && null !== autoRepay && autoBorrow == autoRepay) {
            error("Both autoBorrow and autoRepay cannot be true simultaneously")
        }
        return ServiceGenerator.executeSync(
            service.createOrder(
                mapOf(
                    "text" to text,
                    "currency_pair" to currencyPair,
                    "type" to type?.text,
                    "account" to account?.text,
                    "side" to side.text,
                    "amount" to amount.stripTrailingZeros().toPlainString(),
                    "price" to price.stripTrailingZeros().toPlainString(),
                    "time_in_force" to timeInForce?.text,
                    "iceberg" to iceberg?.stripTrailingZeros()?.toPlainString(),
                    "auto_borrow" to autoBorrow?.toString(),
                    "auto_repay" to autoRepay?.toString()
                )
            )
        )
    }

    fun candlesticks(
        currencyPair: String,
        limit: Int? = null,
        from: Long? = null,
        to: Long? = null,
        interval: GateIoApiServiceSpot.Candletick.CandletickInterval? = null
    ) = ServiceGenerator.executeSync(service.candlesticks(currencyPair, limit, from, to, interval?.text))

    fun tickers() = ServiceGenerator.executeSync(service.tickers())

    fun myTrades(
        currencyPair: String,
        limit: Int? = null,
        page: Int? = null,
        orderId: Long? = null,
        account: GateIoApiServiceSpot.Trade.Account? = null,
        from: String? = null,
        to: String? = null,
    ) = ServiceGenerator.executeSync(service.myTrades(currencyPair, limit, page, orderId, account?.text, from, to))

    fun cancelOrder(
        orderId: String,
        currencyPair: String,
        account: GateIoApiServiceSpot.OpenOrders.Order.Account? = null
    ) =
        ServiceGenerator.executeSync(service.cancelOrder(orderId, currencyPair, account?.text))

    fun orderBook(
        currencyPair: String,
        interval: String? = null,
        limit: Int? = null,
        withId: Boolean? = null,
    ) =
        ServiceGenerator.executeSync(service.orderBook(currencyPair, interval, limit, withId))

    interface GateIoApiServiceSpot {
        @GET("api/v4/spot/currency_pairs")
        fun currencyPairs(): Call<List<CurrencyPair>>

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class CurrencyPair(
            val id: String,
            val base: String,
            val quote: String,
            val fee: BigDecimal,
            @JsonProperty("min_base_amount")
            val minBaseAmount: BigDecimal?,
            @JsonProperty("min_quote_amount")
            val minQuoteAmount: BigDecimal?,
            @JsonProperty("amount_precision")
            val amountPrecision: Int,
            val precision: Int,
            @JsonProperty("trade_status")
            val tradeStatus: TradeStatus,
            @JsonProperty("sell_start")
            val sellStart: Long,
            @JsonProperty("buy_start")
            val buyStart: Long
        ) {
            enum class TradeStatus {
                @JsonProperty("untradable")
                UNTRADABLE,

                @JsonProperty("buyable")
                BUYABLE,

                @JsonProperty("sellable")
                SELLABLE,

                @JsonProperty("tradable")
                TRADABLE,
            }
        }

        @Headers(HEADER_AUTH_RETROFIT_HEADER)
        @GET("api/v4/spot/open_orders")
        fun openOrders(
            @Query("page") page: Int?,
            @Query("limit") limit: Int?,
            @Query("account") account: String?
        ): Call<List<OpenOrders>>

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class OpenOrders(
            @JsonProperty("currency_pair")
            val currencyPair: String,
            val total: Int,
            val orders: List<Order>
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Order(
                val id: String,
                val text: String,
                @JsonProperty("create_time")
                val createTime: Long,
                @JsonProperty("update_time")
                val updateTime: Long,
                @JsonProperty("currency_pair")
                val currencyPair: String,
                val status: Status,
                val type: Type,
                val account: Account,
                val side: Side,
                val amount: BigDecimal,
                val price: BigDecimal,
                @JsonProperty("time_in_force")
                val timeInForce: TimeInForce,
                val left: BigDecimal,
                @JsonProperty("filled_total")
                val filledTotal: BigDecimal,
                val fee: BigDecimal,
                @JsonProperty("fee_currency")
                val feeCurrency: String,
                @JsonProperty("point_fee")
                val pointFee: Int,
                @JsonProperty("gt_fee")
                val gtFee: BigDecimal,
                @JsonProperty("gt_discount")
                val gtDiscount: Boolean,
                @JsonProperty("rebated_fee")
                val rebatedFee: BigDecimal,
                @JsonProperty("rebated_fee_currency")
                val rebatedFeeCurrency: String
            ) {
                @JsonIgnoreProperties(ignoreUnknown = false)
                enum class Status {
                    @JsonProperty("open")
                    OPEN,

                    @JsonProperty("closed")
                    CLOSED,

                    @JsonProperty("cancelled")
                    CANCELLED
                }

                @JsonIgnoreProperties(ignoreUnknown = false)
                enum class Type(val text: String) {
                    @JsonProperty("limit")
                    LIMIT("limit")
                }

                @JsonIgnoreProperties(ignoreUnknown = false)
                enum class Account(val text: String) {
                    @JsonProperty("spot")
                    SPOT("spot"),

                    @JsonProperty("margin")
                    MARGIN("margin"),

                    @JsonProperty("cross_margin")
                    CROSS_MARGIN("cross_margin")
                }

                @JsonIgnoreProperties(ignoreUnknown = false)
                enum class Side(val text: String) {
                    @JsonProperty("buy")
                    BUY("buy"),

                    @JsonProperty("sell")
                    SELL("sell")
                }

                @JsonIgnoreProperties(ignoreUnknown = false)
                enum class TimeInForce(val text: String) {
                    @JsonProperty("gtc")
                    GTC("gtc"),

                    @JsonProperty("ioc")
                    IOC("ioc"),

                    @JsonProperty("poc")
                    POC("poc"),

                    @JsonProperty("fok")
                    FOK("fok")
                }
            }
        }

        @Headers(HEADER_AUTH_RETROFIT_HEADER, "Content-Type: application/json")
        @POST("api/v4/spot/orders")
        fun createOrder(
            @Body text: Map<String, String?>,
        ): Call<OpenOrders.Order>

        @GET("api/v4/spot/candlesticks")
        fun candlesticks(
            @Query("currency_pair") currencyPair: String,
            @Query("limit") limit: Int?,
            @Query("from") from: Long?,
            @Query("to") to: Long?,
            @Query("interval") interval: String?,
        ): Call<List<Candletick>>

        @JsonFormat(shape = JsonFormat.Shape.ARRAY)
        @JsonPropertyOrder
        @JsonIgnoreProperties(ignoreUnknown = false)
        data class Candletick(
            val timestamp: Long,
            val quoteVolume: BigDecimal,
            val close: BigDecimal,
            val high: BigDecimal,
            val low: BigDecimal,
            val open: BigDecimal,
            val baseVolume: BigDecimal
        ) {
            enum class CandletickInterval(val text: String) {
                S10("10s"),
                M1("1m"),
                M5("5m"),
                M15("15m"),
                M30("30m"),
                H1("1h"),
                H4("4h"),
                H8("8h"),
                D1("1d"),
                D7("7d"),
                D30("30d"),
            }
        }

        @GET("api/v4/spot/tickers")
        fun tickers(): Call<List<Ticker>>

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
            @JsonProperty("change_utc0")
            val changeUtc0: BigDecimal,
            @JsonProperty("change_utc8")
            val changeUtc8: BigDecimal,
            @JsonProperty("base_volume")
            val baseVolume: BigDecimal,
            @JsonProperty("quote_volume")
            val quoteVolume: BigDecimal,
            @JsonProperty("high_24h")
            val high24h: BigDecimal,
            @JsonProperty("low_24h")
            val low24h: BigDecimal,
            @JsonProperty("etf_net_value")
            val etfNetValue: BigDecimal? = null,
            @JsonProperty("etf_pre_net_value")
            val etfPreNetValue: BigDecimal? = null,
            @JsonProperty("etf_pre_timestamp")
            val etfPreTimestamp: Long? = null,
            @JsonProperty("etf_leverage")
            val etfLeverage: BigDecimal? = null
        )

        @Headers(HEADER_AUTH_RETROFIT_HEADER, "Content-Type: application/json")
        @GET("api/v4/spot/my_trades")
        fun myTrades(
            @Query("currency_pair") currencyPair: String,
            @Query("limit") limit: Int?,
            @Query("page") page: Int?,
            @Query("order_id") orderId: Long?,
            @Query("account") account: String?,
            @Query("from") from: String?,
            @Query("to") to: String?,
        ): Call<List<Trade>>

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Trade(
            val id: Long,
            @JsonProperty("create_time")
            val createTime: Long,
            @JsonProperty("create_time_ms")
            val createTimeMs: String,
            @JsonProperty("order_id")
            val orderId: Long,
            val side: OpenOrders.Order.Side,
            val role: WebSocketEventSealed.UserTrade.Role,
            val amount: BigDecimal,
            val price: BigDecimal,
            val fee: BigDecimal,
            @JsonProperty("fee_currency")
            val feeCurrency: String,
            @JsonProperty("point_fee")
            val pointFee: Int,
            @JsonProperty("gt_fee")
            val gtFee: Int
        ) {
            enum class Account(val text: String) {
                CROSS_MARGIN("cross_margin"),
                SPOT("spot")
            }
        }

        @Headers(HEADER_AUTH_RETROFIT_HEADER, "Content-Type: application/json")
        @DELETE("api/v4/spot/orders/{order_id}")
        fun cancelOrder(
            @Path("order_id") orderId: String,
            @Query("currency_pair") currencyPair: String,
            @Query("account") account: String?,
        ): Call<OpenOrders.Order>

        @Headers(HEADER_AUTH_RETROFIT_HEADER, "Content-Type: application/json")
        @GET("api/v4/spot/order_book")
        fun orderBook(
            @Query("currency_pair") currencyPair: String,
            @Query("interval") interval: String?,
            @Query("limit") limit: Int?,
            @Query("with_id") withId: Boolean?,
        ): Call<OrderBook>

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class OrderBook(
            val id: Long,
            val current: Long,
            val update: Long,
            val asks: List<PriceLevel>,
            val bids: List<PriceLevel>,
        ) {
            @JsonFormat(shape = JsonFormat.Shape.ARRAY)
            data class PriceLevel(val price: BigDecimal, val amount: BigDecimal)
        }
    }

}