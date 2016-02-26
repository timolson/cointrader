-- Select Last trade time by market
select FROM_UNIXTIME( max(time)/1000) from trade where market=unhex('F060AFF64B1C48E0A15CB3AAA1479527')

-- Force delete all tables used by a strategy
SET foreign_key_checks = 0;
drop table fill;
drop table specific_order;
drop table general_order;
drop table order_update;
drop table transaction;
drop table  position;
drop table  `portfolio`;
SET foreign_key_checks = 1;

-- Total long/short position
select sum(f.`open_volume_count`), sum(f.`volume_count`)
from fill f
left join specific_order so on f.`order`=so.`id`
where so.`comment`  like '%long%' union
select sum(f.`open_volume_count`), sum(f.`volume_count`)
from fill f
left join specific_order so on f.`order`=so.`id`
where so.`comment`  like '%short%'

-- Select all markets,exhanges and listings
select hex(market.id),  exchange.`symbol`, base.`symbol` as basesymbol,  quote.`symbol` as quotesymbol, prompt.`symbol` as prompt from market, exchange,  listing left join prompt on listing.`prompt`=prompt.id left join currency base on listing.`base`=base.`id` left join currency quote on listing.`quote`=quote.`id`   where market.`exchange`=exchange.`id` and listing.`id`=market.`listing`

-- Select all fills and assoicated orders
select FROM_UNIXTIME( so.time/1000), FROM_UNIXTIME( f.time/1000), (f.time-so.time)/1000 as restingTimeSec, hex(f.id),hex(so.parent_fill), hex(go.id),hex(so.id),hex(f.position), placement_count,f.`price_count`, so.`limit_price_count`,f.`open_volume_count`,f.`volume_count`,so.volume_count,go.`volume_decimal`, go.`stop_amount_decimal`, go.`stop_price_decimal`,go.`target_price_decimal`,so.`comment` 
from fill f 
left join specific_order so on f.`order`=so.`id`
left join general_order go on so.`parent_order`=go.`id`  
order by f.time asc;

-- Realised PnL grouped by day
select extract(year from  FROM_UNIXTIME( t.time/1000)) as yr, extract(month from  FROM_UNIXTIME( t.time/1000)) as mon,extract(day from  FROM_UNIXTIME( t.time/1000)) as day,
       sum(amount_count)*0.00000001 as BTC, sum((f.price_count*0.01)*(amount_count*0.00000001)) as USD, (sum((f.price_count*0.01)*(amount_count*0.00000001))/2000)*100 as Percent
from transaction t left join fill f on t.fill=f.`id` where type =11
group by extract(year from FROM_UNIXTIME( t.time/1000)), extract(month from FROM_UNIXTIME( t.time/1000)),extract(day from FROM_UNIXTIME( t.time/1000));

-- Realised PnL grouped by month
select extract(year from  FROM_UNIXTIME( t.time/1000)) as yr, extract(month from  FROM_UNIXTIME( t.time/1000)) as mon,
       sum(amount_count)*0.00000001 as BTC, sum((f.price_count*0.01)*(amount_count*0.00000001)) as USD, (sum((f.price_count*0.01)*(amount_count*0.00000001))/2000)*100 as Percent
from transaction t left join fill f on t.fill=f.`id` where type =11
group by extract(year from FROM_UNIXTIME( t.time/1000)), extract(month from FROM_UNIXTIME( t.time/1000));

-- Total Realised PnL 
select count(*),sum(amount_count)*0.00000001,sum((f.price_count*0.01)*(amount_count*0.00000001)),max(amount_count)*0.00000001,max((f.price_count*0.01)*(amount_count*0.00000001)), min(amount_count)*0.00000001,min((f.price_count*0.01)*(amount_count*0.00000001))  from transaction t
left join fill f on t.fill=f.`id`
 where type =11;

 -- Count of orders in each order state  
-- NEW=0, TRIGGER=1, ROUTED=2,PLACED=3,PARTFILLED=4,FILLED=5, CANCELLING=6,CANCELLED=7, EXPIRED=8, REJECTED=9
select  state,count(a.state)
from order_update as a
    inner join (
        select `order`, max(sequence) as 'last'
        from order_update 
        group by `order`) as b
    on a.`order` = b.`order` and 
       a.sequence = b.last group by a.state
       
       
 