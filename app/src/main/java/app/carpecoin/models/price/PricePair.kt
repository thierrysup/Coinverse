package app.carpecoin.models.price
import app.carpecoin.Enums.Currency

data class PricePair(var BASE_CURRENCY: Currency, var QUOTE_CURRENCY: Currency)