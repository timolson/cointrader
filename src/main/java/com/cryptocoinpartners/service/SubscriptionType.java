package com.cryptocoinpartners.service;

/**
* @author Tim Olson
*/
public enum SubscriptionType {

    /** Indicates Point-in-Time data delivered as a Tick */
    TICK,

    /** Indicates individual trade data delivered as a Trade */
    TRADE,

    /** Indicates local book data delivered as a Book */
    BOOK,

    /** Indicates local and mirrored book data delivered as multiple Books */
    BOOK_L2
}
