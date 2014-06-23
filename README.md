Coin Trader
===========

Coin Trader is a Java-based backend for trading cryptocurrencies, released under the Apache License.  It brings together:
* [XChange](https://github.com/timmolter/XChange) for market data, order execution, and account information
* [Esper](http://esper.codehaus.org/) for querying events
* [JPA](http://www.oracle.com/technetwork/java/javaee/tech/persistence-jsp-140049.html) / [Hibernate](http://hibernate.org/) for persistence
* [JCommander](http://jcommander.org) and [Antlr](http://antlr.org) for command parsing

Coin Trader adds:
* a control console
* simulated trading
* schema and persistence, see below (package schema)
* csv market data output (not flexible yet, package module.savetickscsv)
* ad-hoc ascii table reports (command `jpa` and `class AdHocJpaReportCommand`)
* module loader to connect data emitters, persisters, replayers, signals, strategies, etc, to Esper (package module)
* command pattern making new utilities easy to implement (package bin)

Coin Trader's future:
* live order execution, basic routing
* flexible data output
* basic signals
* accounting and reconciliation
* a variety of backtesting

To implement signals and strategies, you connect Esper event queries to Java code like this:

```java
@When( "select avg(priceAsDouble) from Trade.win:time(30 sec)" )
void checkMovingAverage( double avg )
{
  if( avg > trigger )
    esper.publish( new MySignal(5.31) );
}

@When( "select * from MySignal where mySignalValue > 5.0" )
void enterTrade( MySignal s )
{
  orders.create( Listings.BTC_USD, 1.0 )
        .withLimit( 650.25 )
        .place();
}
```

Then, when any `Trade` market data arrives, your `checkAverage()` method is invoked, which publishes your signal, which triggers the `enterTrade()` method.  Esper provides a rich and sophisticated language for querying the events published by Coin Trader.


## Presentation
Tim is presenting an introduction to Coin Trader at the San Francisco Bitcoin Devs meetup on June 23rd, 2014 at 20/Mission.  See the [event page](http://www.meetup.com/SF-Bitcoin-Devs/events/187096772/) for more info.

# Setup
1. [Install Java](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
2. [Install Maven](http://maven.apache.org/download.cgi)
3. [Install MySql](http://dev.mysql.com/downloads/mysql/)
 1. Create a database
  1. ```mysql -u root -e `create database cointrader;` ``` _this is mysql root not system root_
4. `git clone https://github.com/timolson/cointrader.git`
5. `cd cointrader`
6. Build with maven (the default goal is `package`):
 1. `mvn`
7. OPTIONAL: Create a file `cointrader.properties` in the current directory.  You may configure additional settings here, like a database username and password.  See cointrader-default.properties for information.
8. Initialize the database with:
 1. `./cointrader.sh reset-database`
9. Start a process to save market data in the db:
 1. `./cointrader.sh save-data &`
 2. for example, to run the data collector, invoke
  1. `java -jar code/target/cointrader-0.2-SNAPSHOT-jar-with-dependencies.jar save-data`
10. If you get errors about "sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target", it is because BTC-e uses an SSL cert whose root CA is not recognized by Java 7.  To fix this problem, run `installCert.sh`

## Basic Run Modes
* Usage Help
 * `./cointrader.sh help`
* Drop and Rebuild Database
 * `./cointrader.sh reset-database`
* Interactive Console
 * `./cointrader.sh console`
* Collect Data
 * `./cointrader.sh save-data`
* Report Data Count
 * `./cointrader.sh report-data`
* Generate CSV file of 1-minute ticks
 * `./cointrader.sh dump-ticks <filename>`
* Ad-Hoc JPA Queries
 * `./cointrader.sh report-jpa 'select t from Trade t'`

# Console Commands
To run the interactive console, type `./cointrader.sh console`.  The console will collect `MarketData` from the `Exchange`s according to the listing settings in your `cointrader.properties` configuration, but it will not persist this data to the database.  Instead, use the `save-data` run mode to persist data.  When running the console, the `MockOrderService` will be attached to provide fake executions of any orders placed with the buy/sell command. The `MarketData` will be live, but your executions will be simulated.

## `help`
Just try it :)

## `watch/unwatch {listing}`
When a watch is set, all `Book` and `Trade` data for the given `Listing` is echoed to the console as soon as it arrives.

## `buy/sell {volume} {market} [limit {limitPrice}] [stop {stopPrice}]`
Use this command to place an order.  Currently you must specify the exact `Market` (including the `Exchange`) where the `Order` should be placed.  Stops are not yet supported.  ONLY MOCK EXECUTIONS ARE CURRENTLY PROVIDED.  You can play with this all day long: Coin Trader will not trade live money yet.

## `csv {filename}`
Saves a file of 1-minute `Tick` data including each side of the `Book` to depth 100.

## `data [summary]`
Prints a summary table of how much `MarketData` (`Trade`s and `Book`s) has been saved to the database for each `Market`.  Note that the console does NOT save data to the database.  Use the `save-data` run mode from the command line to persist `MarketData`

## `exchanges`
Shows a list of the available `Exchange` symbols

## `listings`
Shows a list of the available `Listing` symbols

## `markets`
Shows a list of the available `Market` symbols

## `jpa {query}`
Performs the specified JPQL query and prints the results in an ASCII table on the console.

## `exit`
Terminates the console process.

# Schema

## [Diagram](https://raw.githubusercontent.com/timolson/cointrader/master/diagram.png)

## `Amount`
`Amount` is used to represent all numbers for prices and volumes.  If the `Amount` relates to a specific `Market`, then it will be a `DiscreteAmount` which can only take on values which are multiples of the `Market`'s price or volume basis (see [`DiscreteAmount`](#discreteamount).)  `Amount`s which are not tied to a `Market`, `Currency`, or other object which has a discrete basis are represented by `DecimalAmount`s, which uses 128-bit decimal accounting.  Any operations on an `Amount` which may result in rounding or remainders are required to supply a `RemainderHandler` which controls rounding modes and handles any remainders after the operation is performed.

## `Account`
An `Account` represents _external_ accounting: an `Account` is held with an `Exchange` 
differs from a `Fund` in a couple ways: `Account`s do not have an `Owner`, and they are reconciled 1-for-1 against external records (account data gathered from XChange). `Account`s generally relate to external holdings, but there may be `Account`s attached to `Exchanges.SELF`, meaning the account is internal to this organizition.

## `Bar`
The common OHLC or open/high/low/close for a standard duration of time like one minute.  These can be generated from `Trade`s or `Tick`s and are not collected from data providers.

## `Book`
All the `Bid`s and `Ask`s for a `Market` at a given point in time.  `Book`s are one of the two main types of `MarketData` we collect from the `Market`s, the other being `Trade`s.

## `Context`
This is the main hub of Coin Trader.  A `Context` has an `Esper` instance, and it also provides dependency injection for any instances attached to the `Context`  It also holds a `Configuration` instance.  All operations in Coin Trader are modeled as the creation of a `Context` followed by attaching various modules to the `Context`.  When a module class is attached, any @When annotations it has are bound to the `Esper` instance, and any `@Inject` fields in the module class are populated with instances of the correct type which are already attached to this `Context`.

## `Currency`
This class is used instead of `java.util.Currency` because the builtin `java.util.Currency` class cannot handle non-ISO currency codes like "DOGE" and "42".  We also track whether a `Currency` is fiat or crypto, and define the smallest unit of settlement, called the basis (see `DiscreteAmount`.)  A collection of `Currency` singletons is found in `Currencies`.

## `DecimalAmount`
`DecimalAmount`s are used when the `Amount` to be represented are free-floating and tied to a discrete basis. 

## `DiscreteAmount`
This class is used to represent all prices and volumes.  It acts like an integer counter, except the base step-size is not necessarily 1 (whole numbers).  A `DiscreteAmount` has both a `long count` and a `double basis`.  The `basis` is the "pip size" or what the minimum increment is, and the `count` is the number of integer multiples of the value, so that the final value of the `DiscreteAmount` is `count*basis`.  The minimum increment is `(count+1)*basis`.  This sophistication is required to handle things like trading Swiss Francs, which are rounded to the nearest nickel (0.05).  To represent CHF 0.20 as a `DiscreteAmount`, we use `basis=0.05` and `count=4`, meaning we have four nickels or 0.20.  This approach is also used for trading volumes, so that we can understand the minimum trade amounts.  `Market`s record both a `priceBasis` and a `volumeBasis` which indicate the step sizes for trading a particular `Listing` on that `Market`.
Operations on `DiscreteAmount`s may have remainders or rounding errors, which are optionally passed to a delegate `RemainderHandler`, which may apply the fractional amount to another account, ignore the remainder, etc. See Superman 2.

## `EntityBase`
This is the base class for anything which can be persisted.  `getId()` gives a `UUID`, which is stored in the db as a `BINARY(16)`.

## `Event`
A subtype of `EntityBase`. Any subclass of `Event` may be published to `Esper`.

## `Exchange`
An `Exchange` is anywhere with `Listing`s of tradeable `Fungible`s.  A `Listing` on a specific `Exchange` is called a `Market`.  Technically, none of the existing cryptocurrency exchanges are actually exchanges in the sense of a matchmaking service; they are all broker-dealers with whom you have a deposit account.  Thus `Account`s are also linked to `Exchanges`.  A collection of `Exchange` singletons is found in `Exchanges`.

## `Fill`
A `Fill` represent part or all of an `Order` being matched in a `Market`.  They are `RemoteEvent`s and the order service will detect published `Fill`s and update the `Order`s status accordingly.

## `Fund`
`Fund`s are _internal_ accounting if you have multiple `Owner`s participating in the same Coin Trader deployment.  Each `Owner` has a `Stake` in the `Fund` representing their share.  Every `Strategy` has a matching `Fund`, and `Owner`s may participate in the `Strategy` by transferring a `Position` from their own deposit `Fund` into a `Strategy`'s `Fund`, in exchange for a `Stake` in the `Strategy`'s `Fund`.  The price of the `Stake` is marked-to-market at the time of transaction, using the best currently available data.

## `FundManager`
Every `Fund` has a `FundManager` who dictates the trading of the `Fund`'s `Position`s.  `Owner`s are the `FundManager`s of their deposit `Fund`, and every `Strategy` is a `FundManager` for the `Stategy`'s `Fund` whose positions it trades.

## `Fungible`
A `Fungible` is anything that can be replaced by another similar item of the same type.  Fungibles include `Currency`, Stocks, Bonds, Options, etc.

## `Listing`
A `Listing` has a symbol but is not related to an `Exchange`.  Generally, it represents a tradeable security like `BTC.USD` when there is no need to differentiate between the same security on different `Exchange`s.  Usually, you want to use `Market` instead of just a `Listing`, unless you are creating an order which wants to trade a `Listing` without regard to the `Account` or `Exchange` where the trading occurs.
Every `Listing` has a `baseFungible` and a `quoteFungible`.  The `baseFungible` is what you are buying/selling and the `quoteFungible` is used for payment.  For currency pairs, these are both currencies: The `Listing` for `BTC.USD` has a `baseFungible` of `Currencies.BTC` and a `quoteFungible` of `Currencies.USD`.  A `Listing` for a Japan-based stock would have the `baseFungible` be the stock like `Stocks.SONY` (stocks are not implemented) and the `quoteFungible` would be `Currencies.JPY`

## `Market`
A `Market` represents a `Listing` (BTC.USD) on a specific `Exchange` (BITSTAMP), and this is the primary class for tradeable securities.  Note that using a `Market` instead of a `Listing` allows us to differentiate between prices for the same security on different `Exchange`s, facilitating arbitrage.

## `MarketData`
`MarketData` is the parent class of `Trade`, `Book`, `Tick`, and `Bar`, and it represents any information which is joined to a `Market`  In the future, for example, we could support news feeds by subclassing `MarketData`.  See `RemoteEvent` for notes on event timings.

## `GeneralOrder`
A `GeneralOrder` specifies a volume and a `Listing` but does not specify an `Exchange`.  `GeneralOrder`s are intended to be routed and cleared in a "best effort" attempt by an order execution algorithm.  See [`SpecificOrder`](#specificorder).

## `Offer`
This is a bid or ask, generally reported within a `Book` although in the future, account transfers may also be modeled as `Offer`s.

## `Owner`
A simple way to identify the holders of internal funds.  `Owner`s are members or clients of this organization.  Each `Owner` has a deposit `Fund`, and the `Owner` may transfer positions from their deposit `Fund` to other `Funds`, receiving in exchange a `Stake`s in other `Fund`.  This is how `Owner`s may participate in various `Fund`s managed by `Strategy`s

## `Position`
A Position is a `DiscreteAmount` of a `Fungible`.  All `Positions` have both an `Account` and a `Fund`.  The total of all `Position`s in an `Account` should match the external entity's records, while the internal ownership of the `Positions` is tracked through the `Fund` via `Stake`s and `Owner`s.

## `RemoteEvent`
Many `Event`s, like `MarketData`s, happen remotely.  `RemoteEvent` allows us to record the time we received an event separately from the time the event happened.  The standard `Event.getTime()` field returns the time the event originally occured, and additionally, `RemoteEvent.getTimeReceived()` records the first instant we heard about the event in the Coin Trader system.  This will help us to understand transmission and processing delays between the markets and our trading clients.

## `SpecificOrder`
A `SpecificOrder` specifies a `Market` and a volume, and may optionally specify a price limit (stop and stop-limit orders are not supported yet).  `SpecificOrder`s carry the intent of immediate placement as-is, without breaking the order apart or routing it to alternative `Market`s.  See [`GeneralOrder`](#generalorder)

## `Stake`
`Stake`s record ownership in a `Fund` by an `Owner`

## `Strategy`
Represents an approach to trading.  Every `Strategy` has a corresponding `Fund` which holds the `Position`s the `Strategy` is allowed to trade.

## `Tick`
`Tick` reports instantaneous snapshots of the last trade price, current spread, and total volume during the `Tick`'s time window.  It is not a single `Trade` but a window in time when one or more `Trade`s may happen.  `Tick`s may be generated from a stream of `Trade`s and `Book`s, and `Tick`s are not collected from data providers.  To generate `Tick`s from `Trade` and `Book` data, attach a `TickWindow` to your `Context`.

## `Trade`
This is the most useful kind of `MarketData` to generate.  It describes a single transaction: the time, `Market`, price, and volume.

# Esper
Esper is a Complex Event Processing system which allows you to write SQL-like statements that can select time series.  For example:
`select avg(priceAsDouble) from Trade.win:time(30 sec)`
gives the average price over all `Trade`s occuring within the last 30 seconds.

[Esper Tutorial](http://esper.codehaus.org/tutorials/tutorial/tutorial.html) 

[Esper Reference](http://esper.codehaus.org/esper-4.11.0/doc/reference/en-US/html/index.html)

[EPL Language Introduction](http://esper.codehaus.org/esper-4.11.0/doc/reference/en-US/html/epl_clauses.html#epl-intro)

The Trader relies heavily on Esper as the hub of the architecture.  The 'org.cryptocoinpartners.service.Esper` class manages Esper Engine configuration and supports module loading.

WARNING: any object which has been published to Esper MUST NOT BE CHANGED after being published.  All events are "in the past" and should not be touched after creation. 

# Context Loading
Modules are simply classes `attach()`ed to a `Context`.  Modules perform any task which needs to publish or listen for events.  The `XchangeMarketData` module, for example, initializes the XChange framework and begins collection of all available market data.  The `SaveData` module detects all `MarketData` events published to the `Context` and persists them using `PersistUtil`.  The `save-data` command works like this:

## `@When` binding
When a class is attached to a `Context`, it has an instance created using the `Context`'s `Injector`, and any method on this instance which uses the `@When` annotation will be triggered for every `Event` row which triggers that `@When` clause, like this:

```java
public class MyModuleClass {

  @When( "select * from Trade" )
  public void handleNewTrade( Trade t ) 
  {
    MyEvent signal = computeSignal(t);
    esper.publish(signal);
  }

  @When("select * from MyEvent")
  public void handleCustomSignal(MyEvent signal)
  {
    /*...*/ 
  }
  
}
```

## Module Esper Files
NOTE: CURRENTLY BROKEN
When a module class is loaded, the `Context` searches the class path for a resource which has the same base filename as the module class but with an extension of `.epl`.  If such a file exists, the `Context` will load it as an Esper EPL file.  Any EPL statements which carry the `@IntoMethod` annotation will be bound to the module class's method by the same name.  For example:

```java
@IntoMethod("setAveragePrice")
select avg(priceAsDouble), count(*) from Tick
```

Will invoke this method on the Java module class of the same name:

```java
public void setAveragePrice(double price, int count);
```

## Services
When a class is attached to a `Context`, the `Context` looks at all the implemented interfaces of that class.  If any of those interfaces are tagged with the `@Service` annotation, then the `Context` creates an `Injector` binding from the service interface type to the specific instance attached to the `Context`.  For example, after the `MockOrderService` is attached to a `Context`, any subsequent classes which have a field `@Inject OrderService svc;` will be populated with the specific instance of `MockOrderService` which services that `Context`.  This is because `OrderService` is tagged with `@Service`.


# Main

## Command Line Parsing
[JCommander](http://jcommander.org/) is a command-line parser which makes it easy to attach command-line parameters to Java fields by using annotations.  The `Main` class automatically discovers any subclasses of `org.ccp.Command`, then instantiates them with the default constructor, then registers them with JCommander.  After JCommander has parsed the command-line, `Main` then invokes the `run()` method of the chosen `Command`.

## How to Create a New Command
1. Subclass `org.ccp.CommandBase` (or implement `org.ccp.Command`)
2. Specify the command name by putting this JCommander annotation above the class: `@Parameters(commandNames="my-command”)`
3. Use the singular `@Parameter` tag on any fields in your subclass to capture command-line info (see [JCommander](http://jcommander.org/) docs)
4. Implement the `run()` method

# Other Libs

## Guice
Guice powers the dependency injection performed by the Context, but Coin Trader mostly abuses the Guice model.  The Guice Injector is wrapped by `org.cryptocoinpartners.util.Injector`, but in Coin Trader, the `Context` is really the main actor.  The `Context` acts like a dependency scope and manages the underlying injections dynamically, so you don't have to worry much about Guice.  In your module classes, you might want to access the Injector like this:
```
@Inject org.cryptocoinpartners.util.Injector injector;
private void myFoo() {
  MyClass instance = injector.getInstance(MyClass.class);
}
```
Any module classes attached to a `Context` have bindings created for them (see [Services](#services)) so that subsequently attached classes may access that module by injection.

## Configuration
We use [Apache Commons Configuration](http://commons.apache.org/proper/commons-configuration/) to load system properties, `cointrader.properties`, and module’s `config.properties` files.

## Logging
We log using the slf4j api.  For any class attached to a Context or otherwise instantiated by injection, you can use this pattern:

```java
@Inject private Logger log;
private void doLog() { log.debug("log is not null because it was injected by the Context."); }
```

Otherwise the standard SLF4J idiom looks like this:
```java
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

## Joda Time
The date and time classes of [JodaTime](http://www.joda.org/joda-time/) are the basis for a new standard in Java.  Coin Trader primarily uses `Instant`s to record event times at millisecond resolution locally, but second resolution from most of the markets.

# Credits
## Cryptocoin Partners
Coin Trader was originally intended to be a proprietary project of Cryptocoin Partners, but it's better for everybody if we share an open-source foundation.  The package names have been moved to the .org TLD but retain the original cryptocoinpartners package name, since cointrader.org is owned by someone else.  Cryptocoin Partners will pursue its own strategies and trade its own accounts, but is committed to making the Coin Trader platform transparent and robust.

## Developers
* Tim Olson, lead
* Mike Olson
* Philip Chen
* Tsung-Yao Hsu
* @yzernik

## Thanks
* Tom Johnson
* @timmolter
* YOU!

# Contribute

## Code
An easy way to help is to add more `Market`s, `Listing`s, and `Currency`s.  `Currency`s are easy; we just need to know the symbol and the smallest settlement unit (e.g. satoshis are 1e-8 and pennies are 0.01).  Adding `Market`s is not difficult either; it is mostly just editing a properties file.  See the Wiki for instructions.  Maintaining the `Listing`s available on each `Market` is also easy; just edit the xchangedata.properties file.

## Donations
BTC: `1LfA1vKzCuH8ajbTWywTM5R6rjGshimTQr`

All proceeds will be used to support this open-source project with hosting, historical trade records, etc.
