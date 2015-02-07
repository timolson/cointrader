### Coin Trader

[Coin Trader](http://cryptocoinpartners.org) is a Java-based backend for trading cryptocurrencies, released under the Apache License.  It features:

* Connectivity to many exchanges
* Control console (text)
* Basic order routing
* Simulated trading
* Live order execution
* Schema and persistence
* CSV output of market data
* Ad-hoc table reports
* Modular infrastructure
* Backtesting
* Accounting
* Library of quantitative indicators (TaLib)
* Bitcoin derivatives

Coin Trader's future goals include:
* Reconciliation
* Flexible data output
* Web console & graphing

#### Video
[Tim presents Coin Trader](http://youtu.be/JAvcL05nSKw) at the SF Bitcoin Dev meetup.

#### Setup
Coin Trader requires Java JDK 1.7, Maven, and a SQL database (MySQL default).  
[Setup Instructions](https://github.com/timolson/cointrader/wiki/Setup)

#### Support
See the [Wiki](https://github.com/timolson/cointrader/wiki/Home) for more information.  
There's no mailing list, so [open a new issue](https://github.com/timolson/cointrader/issues/new) for anything, help or just discussion.  Tag it with "Question" and I'll follow through with you.

#### Automated Trading

To implement signals and automated strategies, you connect [Esper](http://esper.codehaus.org/tutorials/tutorial/tutorial.html) event queries to Java code like this:

```java
@When( "select avg(priceAsDouble) from Trade.win:time(30 sec)" )
void checkMovingAverage( double avg )
{
  if( avg > trigger )
    context.publish( new MyIndicator(5.31) );
}

@When( "select * from MyIndicator where myIndicatorValue > 5.0" )
void enterTrade( MyIndicator s )
{
  orders.create( Listings.BTC_USD, 1.0 )
        .withLimit( 650.25 )
        .place();
}
```

You then attach this class to a [Context](https://github.com/timolson/cointrader/wiki/The-Context-Class) to receive Esper event rows as method invocations.

#### 24Hr OHLC Bars
cointrader-esper.cfg.xml
```
<plugin-view factory-class="org.cryptocoinpartners.esper.OHLCBarPlugInViewFactory" name="ohlcbar" namespace="custom"/>  
```
epl
* Create an esper named window to add the OHLC bars into
```
create window OHLCShortWindow.win:length(10)
as
	select *
from
	Bar;
```
* Add the  OHLC bars to it
```
insert into OHLCShortWindow
select * 
 from Trade.custom:ohlcbar(timestamp, priceCountAsDouble, market);
```
* Select values from OHLC bar window
```
select * from OHLCShortWindow as ohlc 
```
	
#### TALIB Set Up
cointrader-esper.cfg.xml
```
<plugin-aggregation-function name="talib" function-class="org.cryptocoinpartners.esper.GenericTALibFunction"/>  
```
epl
```
select coalesce(talib("atr", max, min, last, 9),0) as atr from OHLCShortWindow;
```


#### Console Demo

The Coin Trader Console gives you a peek into the engine.

```
$ ./cointrader.sh console

Coin Trader Console 0.3.0-SNAPSHOT

ct> help
Type "help {command}" for more detailed information.
Available commands:
    • attach
    • buy
    • csv
    • currencies
    • data
    • exchanges
    • exit
    • help
    • jpa
    • listings
    • markets
    • sell
    • set
    • unset
    • unwatch
    • watch
    • watches

ct> data summary
+------------------+------------+-----------+
|      Market      | Num Trades | Num Books |
+------------------+------------+-----------+
|     BTCE:BTC.USD |       8149 |      2671 |
| BITFINEX:BTC.USD |       3509 |      2551 |
| BITSTAMP:BTC.USD |       1344 |       901 |
| BITFINEX:DRK.USD |       1000 |        21 |
| BITFINEX:DRK.BTC |       1000 |        21 |
| BITFINEX:LTC.USD |       1000 |        22 |
| BITFINEX:LTC.BTC |       1000 |        22 |
| BTCCHINA:BTC.CNY |        134 |        31 |
| BTCCHINA:LTC.CNY |        110 |        31 |
| BTCCHINA:LTC.BTC |        102 |        31 |
|     BTER:BTC.CNY |         47 |         7 |
|     BTER:LTC.BTC |         38 |        84 |
|    BTER:DOGE.CNY |         26 |         7 |
|     BTER:NXT.CNY |         26 |         6 |
|     BTER:DRK.CNY |         23 |         6 |
|    BTER:DOGE.BTC |         23 |         6 |
|     BTER:LTC.CNY |         21 |         7 |
|     BTER:XCP.CNY |         20 |         6 |
|     BTER:DRK.BTC |         19 |         6 |
|     BTER:XCP.BTC |         18 |        12 |
|     BTER:NXT.USD |          2 |         6 |
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

ct> buy 1 btc.usd
Creating order GeneralOrder{id=a4664af4-a21d-4b77-a999-bc8a78a8d951, parentOrder=null, listing=BTC.USD, volume=1}
Order has been placed. SpecificOrder{id=3ff02408-8269-4bf9-929f-8d2ca060f6fc, parentOrder=a4664af4-a21d-4b77-a999-bc8a78a8d951, market=BITSTAMP:BTC.USD, volumeCount=100000000}

ct>
Filled order 3ff02408-8269-4bf9-929f-8d2ca060f6fc: Fill{order=3ff02408-8269-4bf9-929f-8d2ca060f6fc, market=BITSTAMP:BTC.USD, price=584.99, volume=1}
Order has been completely filled.  SpecificOrder{id=3ff02408-8269-4bf9-929f-8d2ca060f6fc, parentOrder=a4664af4-a21d-4b77-a999-bc8a78a8d951, market=BITSTAMP:BTC.USD, volumeCount=100000000, averageFillPrice=584.99}
Order has been completely filled.  GeneralOrder{id=a4664af4-a21d-4b77-a999-bc8a78a8d951, parentOrder=null, listing=BTC.USD, volume=1}

ct> exit

$
```


See the [Wiki](https://github.com/timolson/cointrader/wiki/Home) for more information, or jump to [Setup](https://github.com/timolson/cointrader/wiki/).
