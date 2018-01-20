grammar Order;
import Base;


args:

volume (market|listing) ('stop' stopPrice)? ('limit' limitPrice)? ('position' positionEffect)?

;

market : Market;

listing : Listing;

volume : Amount;

stopPrice : Amount;

limitPrice : Amount;

positionEffect : PositionEffect;
