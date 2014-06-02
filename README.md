cointrader
==========

Bitcoin, Litecoin, altcoin algorithmic trading platform based on Java, Esper, and timmolter/XChange

Features:
Data collection, schema, persistence, event engine, csv dump, module architecture for trading algos

Planned:
order execution, backtesting


## Introduction
Coin Trader is a Java-based backend engine architecture for algorithmically trading cryptocurrencies.
It integrates with timmolter/XChange for market data and order execution, provides persistence, 
and provides an event-based (Esper) architecture for backtesting, algorithm design, and live trading.

## Setup
1. install Java
2. install Maven
3. install MySql
 1. create a database
  1. `mysql -u root -e ‘create database trader;’`
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
  2. `java -jar code/target/trader-0.2-SNAPSHOT-jar-with-dependencies.jar ticker`
10. If you get errors about "Unable to find valid certification path..." or com.sun.security.provider.certpath.SunCertPathBuilderException, it is because BTC-e uses an expired SSL cert.  To fix this problem, follow the instructions in `cointrader/src/main/config/install-cert.txt`

## Schema
[Schema Diagram](http://drive.google.com/open?id=0BwrtnwfeGzdDU3hpbkhjdGJoRHM)

### EntityBase
This is the root class for anything which can be persisted.  getId() gives a UUID, which is stored in the db as a `BINARY(16)`.

### Event
A subtype of `EntityBase`, any subclass of `Event` may be published to Esper.

### Market
An exchange. place which holds and trades `Listing`s of `Fungible`s, 

### Listing
A `Listing` has a symbol and is related to a `Market`.  In our system, the BTC.LTC pair on BTC-e is a different `Listing` than BTC.LTC at BTC China.  A `Listing` has a base `Fungible` (the `Fungible` being bought) and a quote `Fungible` (the `Fungible` with which you pay)

### MarketListing
A `MarketListing` represents a `Listing` (BTC.USD) on a specific `Market` (BITSTAMP), and this is the primary class for tradeable securities.  Note that using `MarketListing` allows us to differentiate between prices for the same security on different exchanges, facilitating arbitrage.

### Fungible
A `Fungible` is anything that can be traded for another similar item of the same type.  Fungibles include `Currency`, Stocks, Bonds, Options, etc.

### MarketData
`MarketData` is the parent class of `Trade`, `Tick`, and `Bar`.  `MarketData` has a `getTimeReceived()` method.  The standard `Event.time` field should be set to the original instant the event (a `Trade`) occured at the remote server.  The `getTimeReceived()` field then records the *local time* the event was received and created.

### Trade
This is the most useful kind of `MarketData` to generate.  It describes one transaction: the time, security, price, and volume.  

### Tick
This records the last trade price of a `Listing` at an instant in time.  It is not a `Trade`.  Tick is a time-based pulse which reports instantaneous snapshots of the last trade price.  These may be generated from a stream of `Trades`.

### Bar
The common OHLC or open / high / low / close for a standard duration of time like one minute.  These can be generated from `Trade`s or `Tick`s

### Book
All the `Bid`s and `Ask`s for a `Listing` at a given point in time.

## Esper
Esper is a Complex Event Processing system which allows you to write SQL-like statements that can select time series.  For example:
`select avg(price) from TickEvent.win:time(30 sec)`
gives the average price over all TickEvents occuring within the last 30 seconds.

[Esper Tutorial](http://esper.codehaus.org/tutorials/tutorial/tutorial.html) 

[Esper Reference](http://esper.codehaus.org/esper-4.11.0/doc/reference/en-US/html/index.html)

[EPL Language Introduction](http://esper.codehaus.org/esper-4.11.0/doc/reference/en-US/html/epl_clauses.html#epl-intro)

The Trader relies heavily on Esper as the hub of the architecture.  The com.ccp.service.Esper class manages Esper Engine configuration and supports module loading.

WARNING: any object which has been published to Esper MUST NOT BE CHANGED after being published.  All events are "in the past" and should not be touched after creation. 

## Modules
Modules contain Java code, EPL (Esper) files, and configuration files, which are automatically detected and loaded by ModuleLoader.  The `gatherdata` module invokes `MarketDataService.subscribeAll()` to begin collection of all available data.  The `savedata` module detects all `MarketData` events in Esper and persists them through `PersistUtil` (to Hibernate).

### Configuration
Any file named `config.properties` will be loaded from the directory `src/main/java/com/cryptocoinpartners/module/`*myModuleName* using [Apache Commons Configuration](http://commons.apache.org/proper/commons-configuration/).  It is then combined with any configuration from command-line, system properties, plus custom config from the module loader.  The combined `Configuration` object is then passed to any Java `ModuleListener` subclasses found in the module package (see [Java](#heading=h.i3bp28c1wdel))

### Java
Any subclasses of `com.cryptocoinpartners.module.ModuleListenerBase` in the package `com.cryptocoinpartners.module.`*myM**oduleName* will be instantiated with the default constructor().  Then the `init(Esper e, Configuration c)` method will be called with the Esper it is attached to and the combined configuration as described in [Configuration](#heading=h.brqzlpl67t5p).  After the init method is called, any method which uses the `com.cryptocoinpartners.module.@When` annotation will be triggered for every Event row which triggers that `@When` clause, like this:

`public class MyListener extends ModuleListener {
  @When("select * from Trade")
  public void handleNewTrade(Trade t) { … }
}`

The method bodies may publish new events by using the Esper instance passed to the init method.

### Esper
Any files named `*.epl` in the module directory will be loaded into the module’s Esper instance as EPL language files.  If an EPL file has the same base filename as a Java module listener, then any EPL statements which carry the `@IntoMethod` annotation will be bound to the module listener’s singleton method by the same name.  For example:

`@IntoMethod("setAveragePrice")`

`select avg(price), count(*), * from Tick`

Will invoke this method on the Java module listener of the same name:

`public void setAveragePrice(BigDecimal price, int count, Tick tick);`

## Main

### Command Line Parsing
[JCommander](http://jcommander.org/) is a command-line parser which makes it easy to attach command-line parameters to Java fields by using annotations.  The `Main` class automatically discovers any subclasses of `com.ccp.Command`, then instantiates them with the default constructor(), then registers them with JCommander.  After JCommander has parsed the command-line, `Main` then invokes the run() method of the chosen `Command`.

### Create a New Command
* Subclass `com.ccp.CommandBase` (or implement `com.ccp.Command`)
* Specify the command name by putting a JCommander annotation above the class:
`@Parameters( commandNames="ticker”)`
* Use the singular @Parameter tag on any fields in your subclass to capture command-line info (see [JCommander](http://jcommander.org/) docs)
* Implement the run() method

## Other Libs

### Configuration
We use [Apache Commons Configuration](http://commons.apache.org/proper/commons-configuration/) to load system properties, `trader.properties`, and module’s `config.properties` files.

### Logging
We log using the slf4j api like this:

`Logger log = LoggerFactory.getLogger(MyClass.class);`
`log.debug("it works");`

The underlying log implementation is logback, and the config file is at `src/main/resources/logback.xml`

#### Log Levels

`trace`: spammy debug
`debug`: regular debug
`info`: for notable infrequent events like connected to DB or data source
`warn`: problems which can be recovered from.  notify human administrator
`error`: problems which have no recovery.  notify human administrator immediately
