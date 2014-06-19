grammar Csv;
import Base;


args:

filename ('from' startDate)? (('to'|'til') endDate)? ('by' tickDuration)?

;


filename: String ;

startDate: String ;

endDate: String ;

tickDuration: String ;