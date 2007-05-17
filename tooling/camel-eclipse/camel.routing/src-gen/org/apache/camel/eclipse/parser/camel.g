grammar camel;

@lexer::header {
package org.apache.camel.eclipse.parser; 
}

@parser::header {
package org.apache.camel.eclipse.parser; 

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import org.eclipse.emf.ecore.EObject;
import org.openarchitectureware.xtext.loc.LocationTool;
import org.openarchitectureware.xtext.parser.*;
}

@parser::members {
	private Object value(Object obj) {
		if (obj == null)
			return null;
		if (obj instanceof Token) {
			Token t = (Token) obj;
			if (t.getType() == camelLexer.INT)
				return Integer.valueOf(t.getText());
			if (t.getType() == camelLexer.STRING)
				return t.getText().substring(1, t.getText().length() - 1);
			if (t.getText().startsWith("^"))
				return t.getText().substring(1, t.getText().length());
			return t.getText();
		}
		return obj;
	}
	
	private Object value(Object a, Object... b) {
		if (a!=null)
			return value(a);
		for (int i = 0; i < b.length; i++) {
			Object object = b[i];
			if (object!=null) 
				return value(object);
		}
		return null;
	}
	
	private void loc(Token start,Token end,Object ele) {
		int s = start.getTokenIndex();
		if (start instanceof CommonToken) {
			s = ((CommonToken)start).getStartIndex();
		}
		int l = start.getLine();
		int e = end.getTokenIndex();
		if (end instanceof CommonToken) {
			e = ((CommonToken)end).getStopIndex()+1;
		}
		LocationTool.setLocation(ele,s,e,l);
	}
	private EcoreModelFactory factory;
	public camelParser(TokenStream stream, EcoreModelFactory factory) {
		this(stream);
		this.factory = factory;
	}
	
	private List<ErrorMsg> errors = new ArrayList<ErrorMsg>();
	public List<ErrorMsg> getErrors() {
		return errors;
	}
	
	public String getErrorMessage(RecognitionException e, String[] tokenNames) { 
	    errors.add(ErrorMsg.create(e,tokenNames));
		return super.getErrorMessage(e,tokenNames); 
	} 
}


parse returns [Object o] :
   result=ruleRoutes EOF {$o=result;};
   

ruleRoutes returns [Object result] : 
	    { List routesList = new ArrayList(); 
	      Token start = input.LT(1);}
		(a_routes=ruleRoute{routesList.add(a_routes);})*
		{ result = factory.create("new Routes", "routes.addAll(arg1)",routesList);
		loc(start, input.LT(0)==null?input.LT(-1):input.LT(0), result); }
;

ruleRoute returns [Object result] : 
	    { List processorsList = new ArrayList(); 
	      Token start = input.LT(1);}
		'from' a_from_0_0_1=ID (a_processors=ruleProcessor{processorsList.add(a_processors);})* ';'
		{ result = factory.create("new Route", "setFrom(arg1)->processors.addAll(arg2)",value(a_from_0_0_1),processorsList);
		loc(start, input.LT(0)==null?input.LT(-1):input.LT(0), result); }
;

ruleProcessor returns [Object result] : 
    
    	a0=ruleSend{$result=a0;} 
    |    
    	a1=ruleProcess{$result=a1;} 
;

ruleSend returns [Object result] : 
	    {  
	      Token start = input.LT(1);}
		'to' a_uri_0_0_1=ID
		{ result = factory.create("new Send", "setUri(arg1)",value(a_uri_0_0_1));
		loc(start, input.LT(0)==null?input.LT(-1):input.LT(0), result); }
;

ruleProcess returns [Object result] : 
	    {  
	      Token start = input.LT(1);}
		'process' a_type_0_0_1=ID
		{ result = factory.create("new Process", "setType(arg1)",value(a_type_0_0_1));
		loc(start, input.LT(0)==null?input.LT(-1):input.LT(0), result); }
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

