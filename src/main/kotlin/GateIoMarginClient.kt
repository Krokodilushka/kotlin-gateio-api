import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import java.math.BigDecimal

class GateIoMarginClient(
    apiKey: String?,
    secret: String?,
    baseUrl: String
) {

    private val service =
        ServiceGenerator.createService(KucoinApiServiceMargin::class.java, apiKey, secret, baseUrl)

    fun accounts() = ServiceGenerator.executeSync(service.accounts())

    fun repayments(currency: String, amount: BigDecimal) = ServiceGenerator.executeSync(
        service.repayments(
            mapOf(
                "currency" to currency,
                "amount" to amount.stripTrailingZeros().toPlainString()
            )
        )
    )

    fun currencyPairs() = ServiceGenerator.executeSync(service.currencyPairs())

    fun currencies() = ServiceGenerator.executeSync(service.currencies())

    interface KucoinApiServiceMargin {

        @Headers(HEADER_AUTH_RETROFIT_HEADER)
        @GET("api/v4/margin/cross/accounts")
        fun accounts(): Call<Accounts>

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Accounts(
            @JsonProperty("user_id")
            val userId: Int,
            val locked: Boolean,
            val balances: Map<String, Asset>,
            val total: BigDecimal,
            val borrowed: BigDecimal,
            val interest: BigDecimal,
            val risk: BigDecimal,
            @JsonProperty("total_initial_margin")
            val totalInitialMargin: BigDecimal,
            @JsonProperty("total_margin_balance")
            val totalMarginBalance: BigDecimal,
            @JsonProperty("total_maintenance_margin")
            val totalMaintenanceMargin: BigDecimal,
            @JsonProperty("total_initial_margin_rate")
            val totalInitialMarginRate: BigDecimal,
            @JsonProperty("total_maintenance_margin_rate")
            val totalMaintenanceMarginRate: BigDecimal,
            @JsonProperty("total_available_margin")
            val totalAvailableMargin: BigDecimal
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Asset(
                val available: BigDecimal,
                val freeze: BigDecimal,
                val borrowed: BigDecimal,
                val interest: BigDecimal
            )
        }

        @Headers(HEADER_AUTH_RETROFIT_HEADER)
        @POST("api/v4/margin/cross/repayments")
        fun repayments(@Body text: Map<String, String?>): Call<List<Repayments>>

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Repayments(
            val id: BigDecimal,
            @JsonProperty("create_time")
            val createTime: BigDecimal,
            @JsonProperty("update_time")
            val updateTime: BigDecimal,
            val currency: String,
            val amount: BigDecimal,
            val text: String,
            val status: Int,
            val repaid: BigDecimal,
            @JsonProperty("repaid_interest")
            val repaidInterest: BigDecimal,
            @JsonProperty("unpaid_interest")
            val unpaidInterest: BigDecimal,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = false)
            enum class Status {
                @JsonProperty("1")
                FAILED_TO_BORROW,

                @JsonProperty("2")
                BORROWED_BUT_NOT_REPAID,

                @JsonProperty("3")
                REPAYMENT_COMPLETE
            }
        }

        @GET("api/v4/margin/currency_pairs")
        fun currencyPairs(): Call<List<CurrencyPair>>

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class CurrencyPair(
            val id: String,
            val base: String,
            val quote: String,
            val leverage: Int,
            @JsonProperty("min_base_amount")
            val minBaseAmount: BigDecimal?,
            @JsonProperty("min_quote_amount")
            val minQuoteAmount: BigDecimal?,
            @JsonProperty("max_quote_amount")
            val maxQuoteAmount: BigDecimal?,
            val status: Int,
        )

        @Headers(HEADER_AUTH_RETROFIT_HEADER)
        @GET("api/v4/margin/cross/currencies")
        fun currencies(): Call<List<Currency>>

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Currency(
            val name: String,
            val rate: BigDecimal,
            val prec: BigDecimal,
            val discount: BigDecimal,
            @JsonProperty("min_borrow_amount")
            val minBorrowAmount: BigDecimal,
            @JsonProperty("user_max_borrow_amount")
            val userMaxBorrowAmount: BigDecimal,
            @JsonProperty("total_max_borrow_amount")
            val totalMaxBorrowAmount: BigDecimal,
            val price: BigDecimal,
            val status: Int
        )
    }
}