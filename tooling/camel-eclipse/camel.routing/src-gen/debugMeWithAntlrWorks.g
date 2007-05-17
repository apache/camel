grammar debugMeWithAntlrWorks;



parse :
   result=ruleRoutes EOF ;
   

ruleRoutes  : 
		(ruleRoute)*
;

ruleRoute  : 
		'from' ID (ruleProcessor)* ';'
;

ruleProcessor  : 
    
    	ruleSend 
    |    
    	ruleProcess 
;

ruleSend  : 
		'to' ID
;

ruleProcess  : 
		'process' ID
;



STRING :  
    '"' ( '\\' ('b'|'t'|'n'|'f'|'r'|'\"'|'\''|'\\') | ~('\\'|'"') )* '"' |
    '\'' ( '\\' ('b'|'t'|'n'|'f'|'r'|'\"'|'\''|'\\') | ~('\\'|'\'') )* '\'';



ID	:
	('^')?('a'..'z'|'A'..'Z'|'_') ('a'..'z'|'A'..'Z'|'_'|'0'..'9')*
;



INT :
	('0'..'9')+ 
;



ML_COMMENT
    :   '/*' ( options {greedy=false;} : . )* '*/' {skip();}
    ;



SL_COMMENT
    : '//' ~('\n'|'\r')* '\r'? '\n' {skip();}
    ;



WS : (' '|'\t'|'\r'|'\n')+ {skip();} ; 

