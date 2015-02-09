package org.cryptocoinpartners.schema;

/**
 * A Balance represents an amount of money in a given asset.
 *
 *
 */
public final class Balance {

    private final Asset asset;
    private final String description;
    private final Amount amount;

    /**
     * Constructor
     * 
     * @param asset The underlying asset
     * @param amount The amount
     */
    public Balance(Asset asset, Amount amount) {

        this.asset = asset;
        this.amount = amount;
        this.description = "";
    }

    /**
     * Additional constructor with optional description
     * 
     * @param description Optional description to distinguish same asset Balances
     */
    public Balance(Asset asset, Amount amount, String description) {

        this.asset = asset;
        this.amount = amount;
        this.description = description;
    }

    public Asset getAsset() {

        return asset;
    }

    public Amount getAmount() {

        return amount;
    }

    public String getDescription() {

        return description;
    }

    @Override
    public String toString() {

        return "Balance [asset=" + asset + ", amount=" + amount + ", description=" + description + "]";
    }

    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((amount == null) ? 0 : amount.hashCode());
        result = prime * result + ((asset == null) ? 0 : asset.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Balance other = (Balance) obj;
        if (amount == null) {
            if (other.amount != null) {
                return false;
            }
        } else if (!amount.equals(other.amount)) {
            return false;
        }
        if (asset == null) {
            if (other.asset != null) {
                return false;
            }
        } else if (!asset.equals(other.asset)) {
            return false;
        }
        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        return true;
    }

}
