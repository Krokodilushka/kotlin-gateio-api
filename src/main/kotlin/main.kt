fun main() {
    val key = "1"
    val secret = "2"
    val url = "https://api.gateio.ws/"
    GateIoSpotClient(key, secret, url).apply {
//    currencyPairs().body()?.forEach {
//        println(it)
//    }
//        myTrades("BTC_USDT", account = GateIoSpotClient.KucoinApiServiceSpot.Trade.Account.CROSS_MARGIN).body()?.also {
//            println(it)
//        }
//        openOrders(account = GateIoSpotClient.KucoinApiServiceSpot.OpenOrders.Order.Account.CROSS_MARGIN).body()
//            ?.also {
//                println(it)
//            }
//    client.createOrder(
//        text = "t-test",
//        currencyPair = "BTC_USDT",
//        type = KucoinSpotClient.KucoinApiServiceSpot.OpenOrders.Order.Type.LIMIT,
//        account = KucoinSpotClient.KucoinApiServiceSpot.OpenOrders.Order.Account.CROSS_MARGIN,
//        side = KucoinSpotClient.KucoinApiServiceSpot.OpenOrders.Order.Side.BUY,
//        amount = BigDecimal("1"),
//        price = BigDecimal("10000"),
//        timeInForce = KucoinSpotClient.KucoinApiServiceSpot.OpenOrders.Order.TimeInForce.FOK,
//        iceberg = null,
//        autoBorrow = null,
//        autoRepay = null,
//    ).body()?.also {
//        println(it)
//    }
//        candlesticks("BTC_USDT", interval = GateIoSpotClient.KucoinApiServiceSpot.CandletickInterval.D1).body()?.also {
//            println(it)
//        }
//        tickers().body()?.forEach {
//            println(it)
//        }
    }

    GateIoMarginClient(key, secret, url).apply {
//        accounts().body()!!.also {
//            println(it)
//            it.balances.forEach {
//                println(it)
//            }
//        }
//        currencyPairs().body()!!.also {
//            it.forEach {
//                println(it)
//            }
//        }
        currencies().body()!!.also {
            it.forEach {
                println(it)
            }
        }
    }

    val listener = GateIoApiWebSocketListener(object : GateIoWebSocketClient.WebSocketCallback<WebSocketEventSealed> {
        override fun onEvent(eventWrapper: WebSocketEvent<WebSocketEventSealed>) {
            eventWrapper.serverEvent.result?.also {
                when (it) {
                    is WebSocketEventSealed.Ticker -> {
//                        println("Ticker: ${eventWrapper.serverEvent}")
                    }

                    is WebSocketEventSealed.SubscribeEvent -> {
                        println("SubscribeEvent: $eventWrapper")
                    }

                    is WebSocketEventSealed.UserTrade.List -> {
                        println("UserTrade: ${eventWrapper.serverEvent}")
                    }

                    is WebSocketEventSealed.Order.List -> {
                        println("Orders:")
                        eventWrapper.serverEvent.result as WebSocketEventSealed.Order.List
                        eventWrapper.serverEvent.result.orders.forEach {
                            println("Order: $it")
                        }
                    }

                    is WebSocketEventSealed.CrossBalance.List -> {
                        println("CrossBalances:")
                        eventWrapper.serverEvent.result as WebSocketEventSealed.CrossBalance.List
                        eventWrapper.serverEvent.result.crossBalances.forEach {
                            println("CrossBalance: $it")
                        }
                    }

                    is WebSocketEventSealed.CrossLoan -> {
                        println("CrossLoan:")
                        eventWrapper.serverEvent.result as WebSocketEventSealed.CrossLoan
                        eventWrapper.serverEvent.result.also {
                            println("CrossLoan: $it")
                        }
                    }

                }
            }
            eventWrapper.serverEvent.error?.also {
                println("Error: $it")
            }
        }

        override fun onFailure(cause: Throwable) {
            println("onFailure: $cause")
            throw cause
        }
    })
    GateIoWebSocketClient("wss://api.gateio.ws", ServiceGenerator.client, listener).apply {
        connect()
        val marginClient = GateIoMarginClient(key, secret, url)
//        val currencyPairs = marginClient.currencyPairs().body()!!.map { it.id }
//        println("currencyPairs: ${currencyPairs.filter { it.contains("BTC") }}")
//        currencyPairs
//            .filter { it=="BTC_USDT" }
//            .take(1)
//            .forEachIndexed { index, s ->
//                val r = GateIoWebSocketClient.Request(
//                    id = index.toLong(),
//                    time = System.currentTimeMillis()/1000,
//                    channel = "spot.tickers",
//                    event = GateIoWebSocketClient.Request.Method.SUBSCRIBE,
//                    payload = listOf(s),
//                )
//                send(r)
//            }
        GateIoWebSocketClient.Request(
            time = System.currentTimeMillis() / 1000,
            channel = "spot.orders",
            event = GateIoWebSocketClient.Request.Method.SUBSCRIBE,
            payload = listOf("BTC_USDT"),
            authData = GateIoWebSocketClient.Request.AuthData(key, secret)
        ).also {
            send(it)
        }
        GateIoWebSocketClient.Request(
            time = System.currentTimeMillis() / 1000,
            channel = "spot.cross_balances",
            event = GateIoWebSocketClient.Request.Method.SUBSCRIBE,
            authData = GateIoWebSocketClient.Request.AuthData(key, secret)
        ).also {
            send(it)
        }
        GateIoWebSocketClient.Request(
            time = System.currentTimeMillis() / 1000,
            channel = "spot.cross_loan",
            event = GateIoWebSocketClient.Request.Method.SUBSCRIBE,
            authData = GateIoWebSocketClient.Request.AuthData(key, secret)
        ).also {
            send(it)
        }
        GateIoWebSocketClient.Request(
            time = System.currentTimeMillis() / 1000,
            channel = "spot.usertrades",
            event = GateIoWebSocketClient.Request.Method.SUBSCRIBE,
            payload = listOf("!all"),
            authData = GateIoWebSocketClient.Request.AuthData(key, secret)
        ).also {
            send(it)
        }
    }
    Thread.sleep(6000000L)
}