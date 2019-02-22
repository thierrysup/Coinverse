package app.coinverse

object Enums {

    enum class Timeframe {
        HOUR, DAY, WEEK, MONTH, YEAR
    }

    // Price graph.
    enum class Exchange {
        COINBASE, BINANCE, KRAKEN, GEMINI, EMPTY
    }

    enum class OrderType {
        ASK, BID
    }

    enum class Currency {
        BTC, ETH
    }

    enum class Status {
        SUCCESS, ERROR
    }

    enum class ContentType(val code: Int) {
        ARTICLE(1), YOUTUBE(2), NONE(-1)
    }

    enum class UserActionType {
        START, CONSUME, FINISH, SAVE, DISMISS
    }

    enum class FeedType(val code: Int) {
        MAIN(1), SAVED(2), DISMISSED(3), NONE(-1)
    }

    enum class SignInType(val code: Int) {
        DIALOG(1), FULLSCREEN(2)
    }

    enum class PaymentStatus {
        PAID, FREE
    }

    enum class AccountType {
        READ, ADMIN
    }
}