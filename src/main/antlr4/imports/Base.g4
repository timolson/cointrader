grammar Base;

WS: // ignore whitespace
[ \r\t\n]+
-> skip
;

Amount:
[0-9]+
|
[0-9]* '.' [0-9]+
;

Currency:
[a-zA-Z][a-zA-Z]([a-zA-Z]([a-zA-Z])?)? // 2-4 alphas.. todo isn't there a better way to do this [a-zA-Z]{2-4} doesn't work
;

Exchange:
[a-zA-Z_]+
;

Listing:
Currency '.' Currency
;

Market:
Exchange ':' Listing
;
