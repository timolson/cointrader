cointrader
==========

Coin Trader is a Java-based backend for trading cryptocurrencies, released under the Apache License.  It brings together:
* [XChange](https://github.com/timmolter/XChange) for market data, order execution, and account information
* [Esper](http://esper.codehaus.org/) for querying events
* [JPA](http://www.oracle.com/technetwork/java/javaee/tech/persistence-jsp-140049.html) / [Hibernate](http://hibernate.org/) for persistence
* [JCommander](http://jcommander.org) for command-line operation

Coin Trader adds:
* schema and persistence, see below (package schema)
* csv output (not flexible yet, package module.savetickscsv)
* ad-hoc ascii table reports (command `report-jpa` and `class AdHocJpaReportCommand`)
* module loader to connect data emitters, persisters, replayers, signals, strategies, etc, to Esper (package module)
* command pattern making new utilities easy to implement (package bin)

Coin Trader's future:
* order execution, basic routing
* flexible data output
* basic signals
* accounting and reconciliation
* backtesting
* remoting: strategies may use their own jvm or exe

To implement signals and strategies, you connect Esper event queries to Java code like this:
```
@When( "select avg(priceAsDouble) from Trade.win:time(30 sec)" )
void checkMovingAverage( double avg )
{
  if( avg > trigger )
    esper.publish( new MySignal(5.31) );
}

@When( "select * from MySignal where mySignalValue > 5.0" )
void enterTrade( MySignal s )
{
  placeMarketOrder( Listings.BTC_USD, 1.0 );
}
```
Then, when any `Trade` market data arrives, your `checkAverage()` method is invoked, which publishes your signal, which triggers the `enterTrade()` method.  Esper provides a rich and sophisticated language for querying the events published by Coin Trader.


## Presentation
Tim is presenting an introduction to Coin Trader at the San Francisco Bitcoin Devs meetup on June 23rd, 2014 at 20/Mission.  See http://www.meetup.com/SF-Bitcoin-Devs for more info.

## Cryptocoin Partners
Coin Trader was originally intended to be a proprietary project of Cryptocoin Partners, but it's better for everybody if we share an open-source foundation.  The package names have been moved to the .org TLD but retain the original cryptocoinpartners package name, since cointrader.org is owned by someone else.

# Setup
1. install Java
2. install Maven
3. install MySql
 1. create a database
  1. ```mysql -u root -e `create database trader;` ```
4. `git clone https://github.com/timolson/cointrader.git`
5. `cd cointrader`
6. copy the `trader-default.properties `to `trader.properties`
 2. if you have a database password or use a different user than `root`, edit the `trader.properties` file
7. Build with maven (the default goal is `package`):
 3. `mvn`
8. Initialize the database with:
 4. `java -jar code/target/trader-0.2-SNAPSHOT-jar-with-dependencies.jar reset-database`
9. Run the system with:
 5. `java -jar code/target/trader-0.2-SNAPSHOT-jar-with-dependencies.jar <command>`
 6. for example, to run the data collector, invoke
  2. `java -jar code/target/trader-0.2-SNAPSHOT-jar-with-dependencies.jar save-data`
10. If you get errors about "sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target", it is because BTC-e uses an expired SSL cert.  To fix this problem, follow the instructions in `cointrader/src/main/config/install-cert.txt`  See also the [XChange project's SSL Cert documentation](https://github.com/timmolter/XChange/wiki/Installing-SSL-Certificates-into-TrustStore)

## Basic Commands
For the below, `trader XXX` means `java -jar code/target/trader-0.2-SNAPSHOT-jar-with-dependencies.jar XXX`
* Usage Help
 * `trader help`
* Drop and Rebuild Database
 * `trader reset-database`
* Collect Data
 * `trader save-data`
* Report Data Count
 * `trader report-data`
* Generate CSV file of 1-minute ticks
 * `trader dump-ticks <filename>`
* Ad-Hoc JPA Queries
 * `trader report-jpa 'select t from Trade t'`

# Schema
_new diagram coming soon_

## Account
An `Account` differs from a `Fund` in a couple ways: `Account`s do not have an `Owner`, and they are reconciled 1-for-1 against external records (account data gathered from XChange). `Account`s generally relate to external holdings, but there may be `Account`s attached to `Markets.SELF`, meaning the account is internal to this organizition.

## Bar
The common OHLC or open/high/low/close for a standard duration of time like one minute.  These can be generated from `Trade`s or `Tick`s and are not collected from data providers.

## Book
All the `Bid`s and `Ask`s for a `MarketListing` at a given point in time.  `Book`s are one of the two main types of `MarketData` we collect from the `Market`s, the other being `Trade`s.

## Broker
Subtype of `Market`

## BrokerAccount
A `BrokerAccount` represents an external deposit account with an entity who also serves as a `Market`

## Currency
This class is used instead of `java.util.Currency` because the builtin `java.util.Currency` class cannot handle non-ISO currency codes like "DOGE" and "42".  We also track whether a `Currency` is fiat or crypto, and provide accounting basis for the `Currency` (see `DiscreteAmount`.)

## DiscreteAmount
This class is used to represent all prices and volumes.  It acts like an integer counter, except the base step-size is not necessarily 1 (whole numbers).  A `DiscreteAmount` has both a `long count` and a `double basis`.  The `basis` is the "pip size" or what the minimum increment is, and the `count` is the number of integer multiples of the value, so that the final value of the `DiscreteAmount` is `count*basis`.  The minimum increment is `(count+1)*basis`.  This sophistication is required to handle things like trading Swiss Francs, which are rounded to the nearest nickel (0.05).  To represent CHF 0.20 as a `DiscreteAmount`, we use `basis=0.05` and `count=4`, meaning we have four nickels or 0.20.  This approach is also used for trading volumes, so that we can understand the minimum trade amounts.  `MarketListing`s record both a `priceBasis` and a `volumeBasis` which indicate the step sizes for trading a particular `Listing` on that `Market`.
Operations on `DiscreteAmount`s may have remainders or rounding errors, which are optionally passed to a delegate `RemainderHandler`, which may apply the fractional amount to another account, ignore the remainder, etc. See Superman 2.

## EntityBase
This is the base class for anything which can be persisted.  `getId()` gives a `UUID`, which is stored in the db as a `BINARY(16)`.

## Event
A subtype of `EntityBase`, any subclass of `Event` may be published to Esper.

## Exchange
Exchange does not exist! See `Market`.  This is a deliberate terminology choice: none of the existing trading services are actually exchanges; they are all broker-dealers with deposit accounts.  The term `Market` is intended to encompass both broker-dealers and (hopefully in the future) exchanges (if any true exchanges do emerge)

## Fund
`Fund`s may have many `Owner`s who each have a `Stake` in the `Fund`.  Every `Strategy` has a matching `Fund`, and `Owner`s may transfer `Position`s from their own `Fund` into a `Strategy`s `Fund`, thereby gaining a `Stake` in the `Strategy`'s `Fund` and participating in the `Strategy`

## FundManager
Every `Fund` has a `FundManager` who dictates the trading of the `Fund`'s `Position`s.  `Owner`s are the `FundManager`s of their deposit `Fund`, and every `Strategy` is a `FundManager` for the `Stategy`'s `Fund` whose positions it trades.

## Fungible
A `Fungible` is anything that can be replaced by another similar item of the same type.  Fungibles include `Currency`, Stocks, Bonds, Options, etc.

## Listing
A `Listing` has a symbol but is not related to a `Market`.  Generally, it represents a tradeable security like `BTC.USD` when there is no need to differentiate between the same security on different `Market`s.  Usually, you want to use `MarketListing` instead of just a `Listing`, unless you are creating an order which wants to trade a `Listing` without regard to the `Account` or `Market` where the trading occurs.
Every `Listing` has a `baseFungible` and a `quoteFungible`.  The `baseFungible` is what you are buying/selling and the `quoteFungible` is used for payment.  For currency pairs, these are both currencies: The `Listing` for `BTC.USD` has a `baseFungible` of `Currencies.BTC` and a `quoteFungible` of `Currencies.USD`.  A `Listing` for a Japan-based stock would have the `baseFungible` be the stock like `Stocks.SONY` (stocks are not implemented) and the `quoteFungible` would be `Currencies.JPY`

## Market
Any broker/dealer or exchange.  A place which trades `Listing`s of `Fungible` pairs, also called `MarketListing`s

## MarketData
`MarketData` is the parent class of `Trade`, `Book`, `Tick`, and `Bar`, and it represents any information which is joined to a `MarketListing`  In the future, for example, we could support news feeds by subclassing `MarketData`.  See `RemoteEvent` for notes on event timings.

## MarketListing
A `MarketListing` represents a `Listing` (BTC.USD) on a specific `Market` (BITSTAMP), and this is the primary class for tradeable securities.  Note that using `MarketListing` instead of just a `Listing` allows us to differentiate between prices for the same security on different `Market`s, facilitating arbitrage.

## Order
A request from the trader system to buy or sell a `Listing` or `MarketListing`.  `Order`s are business objects which change state, and therefore they are not `Event`s which must be immutable.

## Owner
A simple way to identify the holders of internal funds.  `Owner`s are members or clients of this organization.  Each `Owner` has a deposit `Fund`, and the `Owner` may transfer positions from their deposit `Fund` to other `Funds`, receiving in exchange a `Stake`s in other `Fund`.  This is how `Owner`s may participate in various `Fund`s managed by `Strategy`s

## Position
A Position is a `DiscreteAmount` of a `Fungible`.  All `Positions` have both an `Account` and a `Fund`.  The total of all `Position`s in an `Account` should match the external entity's records, while the internal ownership of the `Positions` is tracked through the `Fund` via `Stake`s and `Owner`s.

## RemoteEvent
Many `Event`s, like `MarketData`s, happen remotely.  `RemoteEvent` allows us to record the time we received an event separately from the time the event happened.  The standard `Event.getTime()` field returns the time the event originally occured, and additionally, `RemoteEvent.getTimeReceived()` records the first instant we heard about the event in the Coin Trader system.  This will help us to understand transmission and processing delays between the markets and our trading clients.

## Stake
`Stake`s record ownership in a `Fund` by an `Owner`

## Strategy
Represents an approach to trading.  Every `Strategy` has a corresponding `Fund` which holds the `Position`s the `Strategy` is allowed to trade.

## Tick
`Tick` reports instantaneous snapshots of the last trade price, current spread, and total volume during the `Tick`'s time window.  It is not a single `Trade` but a window in time when one or more `Trade`s may happen.  `Tick`s may be generated from a stream of `Trade`s and `Book`s, and `Tick`s are not collected from data providers.  To generate `Tick`s from `Trade` and `Book` data, attach the `tickwindow` module to your `Esper`.

## Trade
This is the most useful kind of `MarketData` to generate.  It describes one transaction: the time, market listing, price, and volume.

# Esper
Esper is a Complex Event Processing system which allows you to write SQL-like statements that can select time series.  For example:
`select avg(priceAsDouble) from Trade.win:time(30 sec)`
gives the average price over all `Trade`s occuring within the last 30 seconds.

[Esper Tutorial](http://esper.codehaus.org/tutorials/tutorial/tutorial.html) 

[Esper Reference](http://esper.codehaus.org/esper-4.11.0/doc/reference/en-US/html/index.html)

[EPL Language Introduction](http://esper.codehaus.org/esper-4.11.0/doc/reference/en-US/html/epl_clauses.html#epl-intro)

The Trader relies heavily on Esper as the hub of the architecture.  The 'org.cryptocoinpartners.service.Esper` class manages Esper Engine configuration and supports module loading.

WARNING: any object which has been published to Esper MUST NOT BE CHANGED after being published.  All events are "in the past" and should not be touched after creation. 

# Modules
Modules are attached to Esper to perform any task which needs to publish or listen for events.  The `xchangedata` module, for example, initializes the XChange framework and begins collection of all available data.  The `savedata` module detects all `MarketData` events published to the `Esper` and persists them using `PersistUtil`.  The `save-data` command works like this:
```
Esper esper = new Esper();
esper.loadModule("xchangedata");
esper.loadModule("savedata");
```
Modules are java packages which contain Java code, EPL (Esper) files, and configuration files, described below.

## Configuration
Any file named `config.properties` will be loaded from the directory `src/main/java/com/cryptocoinpartners/module/`*myModuleName* using [Apache Commons Configuration](http://commons.apache.org/proper/commons-configuration/).  It is then combined with any configuration from command-line, system properties, plus custom config from the module loader.  The combined `Configuration` object is then passed to any Java `ModuleListener` subclasses found in the module package (see [Java])

## Java
Any subclasses of `ModuleListenerBase` in the package `org.cryptocoinpartners.module.myModuleName` will have a singleton instantiated using the default constructor().  Then the `init(Esper e, Configuration c)` method will be called, passing in the `Esper` the `ModuleListener` is attached to plus the combined configuration as described in [Configuration].  After the `init` method is called, any method which uses the `@When` annotation will be triggered for every `Event` row which triggers that `@When` clause, like this:
```
public class MyListener extends ModuleListener {
  public void initModule(Esper esper, Configuration config) {
    super.initModule(esper, config); // important!  This sets the protected vars `esper` and `config`
  }

  @When("select * from Trade")
  public void handleNewTrade(Trade t) {
    MyEvent signal = computeSignal(t);
    esper.publish(signal);
  }

  @When("select * from MyEvent")
  public void handleCustomSignal(MyEvent signal) { … }
}
```

## Esper
Any files named `*.epl` in the module directory will be loaded into the module’s Esper instance as EPL language files.  If an EPL file has the same base filename as a Java subclass of `ModuleListener`, then any EPL statements which carry the `@IntoMethod` annotation will be bound to the `ModuleListener`’s singleton method by the same name.  For example:
```
@IntoMethod("setAveragePrice")
select avg(priceAsDouble), count(*) from Tick
```
Will invoke this method on the Java `ModuleListener` of the same name:
```
public void setAveragePrice(double price, int count);
```

# Main

## Command Line Parsing
[JCommander](http://jcommander.org/) is a command-line parser which makes it easy to attach command-line parameters to Java fields by using annotations.  The `Main` class automatically discovers any subclasses of `org.ccp.Command`, then instantiates them with the default constructor(), then registers them with JCommander.  After JCommander has parsed the command-line, `Main` then invokes the run() method of the chosen `Command`.

## Create a New Command
* Subclass `org.ccp.CommandBase` (or implement `org.ccp.Command`)
* Specify the command name by putting this JCommander annotation above the class: `@Parameters(commandNames="save-data”)`
* Use the singular @Parameter tag on any fields in your subclass to capture command-line info (see [JCommander](http://jcommander.org/) docs)
* Implement the run() method

# Other Libs

## Configuration
We use [Apache Commons Configuration](http://commons.apache.org/proper/commons-configuration/) to load system properties, `trader.properties`, and module’s `config.properties` files.

## Logging
We log using the slf4j api like this:
```
Logger log = LoggerFactory.getLogger(MyClass.class);
log.debug("it works");
```
The underlying log implementation is logback, and the config file is at `src/main/resources/logback.xml`

### Log Levels
* `trace`: spammy debug
* `debug`: regular debug
* `info`: for notable infrequent events like connected to DB or data source
* `warn`: problems which can be recovered from.  notify human administrator
* `error`: problems which have no recovery.  notify human administrator immediately

# Dev Credits
* Tim Olson, lead
* Mike Olson
* Philip Chen
* Tsung-Yao Hsu
* @yzernik

## Thanks
* Tom Johnson
* @timmolter
* YOU!
