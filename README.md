### Coin Trader

Coin Trader is a Java-based backend for trading cryptocurrencies, released under the Apache License.  It features:

* connectivity to many exchanges
* a control console
* simulated trading
* schema and persistence, see below (package schema)
* csv market data output (not flexible yet, package module.savetickscsv)
* ad-hoc ascii table reports (command `jpa` and `class AdHocJpaReportCommand`)
* module loader to connect data emitters, persisters, replayers, signals, strategies, etc, to Esper (package module)
* command pattern making new utilities easy to implement (package bin)

Coin Trader's future includes:
* live order execution, basic routing
* flexible data output
* basic signals
* accounting and reconciliation
* backtesting

See the [Wiki](https://github.com/timolson/cointrader/wiki/Home) for more information, or jump to [Setup](https://github.com/timolson/cointrader/wiki/).
