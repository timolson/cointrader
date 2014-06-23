### Coin Trader

Coin Trader is a Java-based backend for trading cryptocurrencies, released under the Apache License.  It features:

* Connectivity to many exchanges
* Control console (text)
* Simulated trading
* Schema and persistence, see below (package schema)
* CSV output of market data (not flexible yet, package module.savetickscsv)
* Ad-hoc table reports (command `jpa` and `class AdHocJpaReportCommand`)
* Command pattern making new utilities easy to implement (package command)

Coin Trader's future includes:
* live order execution, basic routing
* flexible data output
* basic indicators
* accounting and reconciliation
* backtesting
* web console

See the [Wiki](https://github.com/timolson/cointrader/wiki/Home) for more information, or jump to [Setup](https://github.com/timolson/cointrader/wiki/).

#### Console Demo

The Coin Trader console gives you a peek into the engine.

```
Coin Trader Console 0.3.0-SNAPSHOT

ct> help
Type "help {command}" for more detailed information.
Available commands:
    • buy
    • csv
    • data
    • exchanges
    • exit
    • help
    • jpa
    • listings
    • markets
    • sell
    • unwatch
    • watch
    • watches

ct> data
+------------------+------------+-----------+
|      Market      | Num Trades | Num Books |
+------------------+------------+-----------+
|     BTCE:BTC.USD |       3975 |      2336 |
| BITFINEX:BTC.USD |       1599 |      2303 |
| BITSTAMP:BTC.USD |        869 |       786 |
+------------------+------------+-----------+

ct> watch btc.usd
now watching BTC.USD

ct> 
book: BITFINEX:BTC.USD    587.4 (0.317861) - 588.82 (-1.54641998)
book: BTCE:BTC.USD    583.92 (0.01499999) - 585.99 (-0.01819054)
book: BITSTAMP:BTC.USD    588.79 (0.99999999) - 590.33 (-0.19199999)
book: BITFINEX:BTC.USD    587.4 (0.317861) - 588.81 (-13.94196839)
book: BTCE:BTC.USD    583.92 (0.01499999) - 585.99 (-0.01819054)
trade: BITSTAMP:BTC.USD    590.33 (0.03489999)
book: BTCE:BTC.USD    583.92 (0.01499999) - 585.99 (-0.01819054)
book: BITFINEX:BTC.USD    587.4 (0.317861) - 588.8 (-31.37994228)
book: BTCE:BTC.USD    583.92 (0.01499999) - 585.99 (-0.01180944)

ct> unwatch btc.usd
no longer watching BTC.USD

ct> buy 10.0 bitfinex:btc.usd
Sending order SpecificOrder{id=d5a2ff79-0eca-445d-a3b3-75d1e80265a7, parentOrder=null, market=BITFINEX:BTC.USD, volumeCount=1000000000}
Order has been placed. SpecificOrder{id=d5a2ff79-0eca-445d-a3b3-75d1e80265a7, parentOrder=null, market=BITFINEX:BTC.USD, volumeCount=1000000000}

ct> 
Filled order d5a2ff79-0eca-445d-a3b3-75d1e80265a7: Fill{order=d5a2ff79-0eca-445d-a3b3-75d1e80265a7, market=BITFINEX:BTC.USD, price=585.57, volume=2.11486695}
Order is partially filled SpecificOrder{id=d5a2ff79-0eca-445d-a3b3-75d1e80265a7, parentOrder=null, market=BITFINEX:BTC.USD, volumeCount=1000000000, averageFillPrice=585.57}
Filled order d5a2ff79-0eca-445d-a3b3-75d1e80265a7: Fill{order=d5a2ff79-0eca-445d-a3b3-75d1e80265a7, market=BITFINEX:BTC.USD, price=585.58, volume=7.88513305}
Order has been completely filled.  SpecificOrder{id=d5a2ff79-0eca-445d-a3b3-75d1e80265a7, parentOrder=null, market=BITFINEX:BTC.USD, volumeCount=1000000000, averageFillPrice=585.57788513305}

ct> exit
```
_Coin Trader does not send live orders to exchanges yet.  The above demo uses simulated order filling for testing._

#### Automated Trading

To implement signals and automated strategies, you connect [Esper](http://esper.codehaus.org/) event queries to Java code like this:

```java
@When( "select avg(priceAsDouble) from Trade.win:time(30 sec)" )
void checkMovingAverage( double avg )
{
  if( avg > trigger )
    esper.publish( new MyIndicator(5.31) );
}

@When( "select * from MyIndicator where myIndicatorValue > 5.0" )
void enterTrade( MyIndicator s )
{
  orders.create( Listings.BTC_USD, 1.0 )
        .withLimit( 650.25 )
        .place();
}
```

See the [Wiki](https://github.com/timolson/cointrader/wiki/Home) for more information, or jump to [Setup](https://github.com/timolson/cointrader/wiki/).
