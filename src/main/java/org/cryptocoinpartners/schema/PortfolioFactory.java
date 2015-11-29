package org.cryptocoinpartners.schema;


public interface PortfolioFactory {

    Portfolio create(String name, PortfolioManager manager);

}
