package app.coinverse.priceGraph.models

import app.coinverse.Enums.Exchange.EMPTY
import java.util.*

data class MaximumPercentPriceDifference(
        val timestamp: Date,
        val pair: String,
        val gdaxExchangeOrderData: ExchangeOrderData,
        val krakenExchangeOrderData: ExchangeOrderData,
        val binanceExchangeOrderData: ExchangeOrderData,
        val kucoinExchangeOrderData: ExchangeOrderData,
        val geminiExchangeOrderData: ExchangeOrderData,
        val percentDifference: PercentDifference) {
    constructor() : this(
            Date(),
            "",
            ExchangeOrderData(EMPTY, 0.0, 0.0, Order(EMPTY, 0.0),
                    Order(EMPTY, 0.0), Order(EMPTY, 0.0), Order(EMPTY, 0.0)),
            ExchangeOrderData(EMPTY, 0.0, 0.0, Order(EMPTY, 0.0),
                    Order(EMPTY, 0.0), Order(EMPTY, 0.0), Order(EMPTY, 0.0)),
            ExchangeOrderData(EMPTY, 0.0, 0.0, Order(EMPTY, 0.0),
                    Order(EMPTY, 0.0), Order(EMPTY, 0.0), Order(EMPTY, 0.0)),
            ExchangeOrderData(EMPTY, 0.0, 0.0, Order(EMPTY, 0.0),
                    Order(EMPTY, 0.0), Order(EMPTY, 0.0), Order(EMPTY, 0.0)),
            ExchangeOrderData(EMPTY, 0.0, 0.0, Order(EMPTY, 0.0),
                    Order(EMPTY, 0.0), Order(EMPTY, 0.0), Order(EMPTY, 0.0)),
            PercentDifference(0.0, 0.0, EMPTY, 0.0, EMPTY)
    )
}