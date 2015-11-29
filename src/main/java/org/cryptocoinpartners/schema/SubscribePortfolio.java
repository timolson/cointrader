package org.cryptocoinpartners.schema;

import java.util.Arrays;

import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

public class SubscribePortfolio extends Event {

    protected Portfolio portfolio;

    public SubscribePortfolio() {
    }

    public SubscribePortfolio(final Portfolio portfolioIn) {
        this.portfolio = portfolioIn;
    }

    public SubscribePortfolio(final SubscribePortfolio otherBean) {
        this.portfolio = otherBean.getPortfolio();

    }

    public Portfolio getPortfolio() {
        return this.portfolio;
    }

    public void setPortfolio(final Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null || object.getClass() != this.getClass()) {
            return false;
        }
        // Check if the same object instance
        if (object == this) {
            return true;
        }
        SubscribePortfolio rhs = (SubscribePortfolio) object;
        return new EqualsBuilder().append(this.getPortfolio(), rhs.getPortfolio()).isEquals();
    }

    public int compareTo(final SubscribePortfolio object) {
        if (object == null) {
            return -1;
        }
        // Check if the same object instance
        if (object == this) {
            return 0;
        }
        return new CompareToBuilder().append(this.getPortfolio(), object.getPortfolio()).toComparison();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(1249046965, -82296885).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("portfolio", this.getPortfolio()).toString();
    }

    public boolean equalProperties(final Object thatObject) {
        if (thatObject == null || !this.getClass().isAssignableFrom(thatObject.getClass())) {
            return false;
        }

        final SubscribePortfolio that = (SubscribePortfolio) thatObject;

        return equal(this.getPortfolio(), that.getPortfolio());
    }

    protected static boolean equal(final Object first, final Object second) {
        final boolean equal;

        if (first == null) {
            equal = (second == null);
        } else if (first.getClass().isArray() && (second != null) && second.getClass().isArray()) {
            equal = Arrays.equals((Object[]) first, (Object[]) second);
        } else // note that the following also covers java.util.Collection and java.util.Map
        {
            equal = first.equals(second);
        }

        return equal;
    }

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

}
