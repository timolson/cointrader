package com.cryptocoinpartners.schema;

/**
 * An Owner is a person or corporate entity who holds Stakes in Accounts
 * @author Tim Olson
 */
public class Owner extends EntityBase {

    public Owner(String name) {
        this.name = name;
    }


    public String getName() {
        return name;
    }


    public void setName(String name) {
        this.name = name;
    }


    private String name;
}
