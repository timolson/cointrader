package org.cryptocoinpartners.schema;

import javax.annotation.Nullable;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import org.cryptocoinpartners.schema.dao.Dao;

/**
 * This event is posted when there are any problems retreiving market data
 *
 * @author Tim Olson
 */
@MappedSuperclass
public class MarketDataError extends Event {

    public MarketDataError(Market market) {
        this(market, null);
    }

    public MarketDataError(Market market, @Nullable Exception exception) {
        this.exception = exception;
        this.market = market;
    }

    @Nullable
    public Exception getException() {
        return exception;
    }

    @ManyToOne
    public Market getMarket() {
        return market;
    }

    protected MarketDataError() {
    }

    protected void setException(@Nullable Exception exception) {
        this.exception = exception;
    }

    protected void setMarket(Market market) {
        this.market = market;
    }

    private Exception exception;
    private Market market;

    @Override
    public void persit() {
        // TODO Auto-generated method stub

    }

    @Override
    public void detach() {
        // TODO Auto-generated method stub

    }

    @Override
    public void merge() {
        // TODO Auto-generated method stub

    }

    @Override
    @Transient
    public Dao getDao() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    @Transient
    public void setDao(Dao dao) {

        // TODO Auto-generated method stub
        //  return null;
    }

    @Override
    public void delete() {
        // TODO Auto-generated method stub

    }

    @Override
    public EntityBase refresh() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void prePersist() {
        // TODO Auto-generated method stub

    }

    @Override
    public void postPersist() {
        // TODO Auto-generated method stub

    }
}
