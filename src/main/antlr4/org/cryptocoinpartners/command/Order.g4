grammar Order;
import Base;


args:

volume (market|listing) ('stop' stopPrice)? ('limit' limitPrice)?

;


market : Market;

listing : Listing;

volume : Amount;

stopPrice : Amount;

limitPrice : Amount;
