grammar Order;
import Base;


args:

volume market ('stop' stopPrice)? ('limit' limitPrice)?

;


market : Market;

volume : Amount;

stopPrice : Amount;

limitPrice : Amount;
