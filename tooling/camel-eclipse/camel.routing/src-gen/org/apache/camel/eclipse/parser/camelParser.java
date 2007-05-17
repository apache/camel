// $ANTLR 3.0b6 ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g 2007-04-05 12:20:02

package org.apache.camel.eclipse.parser; 

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import org.eclipse.emf.ecore.EObject;
import org.openarchitectureware.xtext.loc.LocationTool;
import org.openarchitectureware.xtext.parser.*;


import org.antlr.runtime.*;
import java.util.Stack;
import java.util.List;
import java.util.ArrayList;

public class camelParser extends Parser {
    public static final String[] tokenNames = new String[] {
        "<invalid>", "<EOR>", "<DOWN>", "<UP>", "ID", "STRING", "INT", "ML_COMMENT", "SL_COMMENT", "WS", "'from'", "';'", "'to'", "'process'"
    };
    public static final int INT=6;
    public static final int WS=9;
    public static final int EOF=-1;
    public static final int STRING=5;
    public static final int ML_COMMENT=7;
    public static final int SL_COMMENT=8;
    public static final int ID=4;

        public camelParser(TokenStream input) {
            super(input);
        }
        

    public String[] getTokenNames() { return tokenNames; }
    public String getGrammarFileName() { return "..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g"; }

    
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



    // $ANTLR start parse
    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:77:1: parse returns [Object o] : result= ruleRoutes EOF ;
    public Object parse() throws RecognitionException {
        Object o = null;

        Object result = null;


        try {
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:78:4: (result= ruleRoutes EOF )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:78:4: result= ruleRoutes EOF
            {
            pushFollow(FOLLOW_ruleRoutes_in_parse47);
            result=ruleRoutes();
            _fsp--;

            match(input,EOF,FOLLOW_EOF_in_parse49); 
            o =result;

            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return o;
    }
    // $ANTLR end parse


    // $ANTLR start ruleRoutes
    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:81:1: ruleRoutes returns [Object result] : (a_routes= ruleRoute )* ;
    public Object ruleRoutes() throws RecognitionException {
        Object result = null;

        Object a_routes = null;


        try {
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:82:6: ( (a_routes= ruleRoute )* )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:82:6: (a_routes= ruleRoute )*
            {
             List routesList = new ArrayList(); 
            	      Token start = input.LT(1);
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:84:3: (a_routes= ruleRoute )*
            loop1:
            do {
                int alt1=2;
                int LA1_0 = input.LA(1);
                if ( (LA1_0==10) ) {
                    alt1=1;
                }


                switch (alt1) {
            	case 1 :
            	    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:84:4: a_routes= ruleRoute
            	    {
            	    pushFollow(FOLLOW_ruleRoute_in_ruleRoutes80);
            	    a_routes=ruleRoute();
            	    _fsp--;

            	    routesList.add(a_routes);

            	    }
            	    break;

            	default :
            	    break loop1;
                }
            } while (true);

             result = factory.create("new Routes", "routes.addAll(arg1)",routesList);
            		loc(start, input.LT(0)==null?input.LT(-1):input.LT(0), result); 

            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return result;
    }
    // $ANTLR end ruleRoutes


    // $ANTLR start ruleRoute
    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:89:1: ruleRoute returns [Object result] : 'from' a_from_0_0_1= ID (a_processors= ruleProcessor )* ';' ;
    public Object ruleRoute() throws RecognitionException {
        Object result = null;

        Token a_from_0_0_1=null;
        Object a_processors = null;


        try {
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:90:6: ( 'from' a_from_0_0_1= ID (a_processors= ruleProcessor )* ';' )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:90:6: 'from' a_from_0_0_1= ID (a_processors= ruleProcessor )* ';'
            {
             List processorsList = new ArrayList(); 
            	      Token start = input.LT(1);
            match(input,10,FOLLOW_10_in_ruleRoute110); 
            a_from_0_0_1=(Token)input.LT(1);
            match(input,ID,FOLLOW_ID_in_ruleRoute114); 
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:92:26: (a_processors= ruleProcessor )*
            loop2:
            do {
                int alt2=2;
                int LA2_0 = input.LA(1);
                if ( ((LA2_0>=12 && LA2_0<=13)) ) {
                    alt2=1;
                }


                switch (alt2) {
            	case 1 :
            	    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:92:27: a_processors= ruleProcessor
            	    {
            	    pushFollow(FOLLOW_ruleProcessor_in_ruleRoute119);
            	    a_processors=ruleProcessor();
            	    _fsp--;

            	    processorsList.add(a_processors);

            	    }
            	    break;

            	default :
            	    break loop2;
                }
            } while (true);

            match(input,11,FOLLOW_11_in_ruleRoute124); 
             result = factory.create("new Route", "setFrom(arg1)->processors.addAll(arg2)",value(a_from_0_0_1),processorsList);
            		loc(start, input.LT(0)==null?input.LT(-1):input.LT(0), result); 

            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return result;
    }
    // $ANTLR end ruleRoute


    // $ANTLR start ruleProcessor
    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:97:1: ruleProcessor returns [Object result] : (a0= ruleSend | a1= ruleProcess );
    public Object ruleProcessor() throws RecognitionException {
        Object result = null;

        Object a0 = null;

        Object a1 = null;


        try {
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:99:6: (a0= ruleSend | a1= ruleProcess )
            int alt3=2;
            int LA3_0 = input.LA(1);
            if ( (LA3_0==12) ) {
                alt3=1;
            }
            else if ( (LA3_0==13) ) {
                alt3=2;
            }
            else {
                NoViableAltException nvae =
                    new NoViableAltException("97:1: ruleProcessor returns [Object result] : (a0= ruleSend | a1= ruleProcess );", 3, 0, input);

                throw nvae;
            }
            switch (alt3) {
                case 1 :
                    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:99:6: a0= ruleSend
                    {
                    pushFollow(FOLLOW_ruleSend_in_ruleProcessor154);
                    a0=ruleSend();
                    _fsp--;

                    result =a0;

                    }
                    break;
                case 2 :
                    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:101:6: a1= ruleProcess
                    {
                    pushFollow(FOLLOW_ruleProcess_in_ruleProcessor175);
                    a1=ruleProcess();
                    _fsp--;

                    result =a1;

                    }
                    break;

            }
        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return result;
    }
    // $ANTLR end ruleProcessor


    // $ANTLR start ruleSend
    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:104:1: ruleSend returns [Object result] : 'to' a_uri_0_0_1= ID ;
    public Object ruleSend() throws RecognitionException {
        Object result = null;

        Token a_uri_0_0_1=null;

        try {
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:105:6: ( 'to' a_uri_0_0_1= ID )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:105:6: 'to' a_uri_0_0_1= ID
            {
              
            	      Token start = input.LT(1);
            match(input,12,FOLLOW_12_in_ruleSend200); 
            a_uri_0_0_1=(Token)input.LT(1);
            match(input,ID,FOLLOW_ID_in_ruleSend204); 
             result = factory.create("new Send", "setUri(arg1)",value(a_uri_0_0_1));
            		loc(start, input.LT(0)==null?input.LT(-1):input.LT(0), result); 

            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return result;
    }
    // $ANTLR end ruleSend


    // $ANTLR start ruleProcess
    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:112:1: ruleProcess returns [Object result] : 'process' a_type_0_0_1= ID ;
    public Object ruleProcess() throws RecognitionException {
        Object result = null;

        Token a_type_0_0_1=null;

        try {
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:113:6: ( 'process' a_type_0_0_1= ID )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:113:6: 'process' a_type_0_0_1= ID
            {
              
            	      Token start = input.LT(1);
            match(input,13,FOLLOW_13_in_ruleProcess231); 
            a_type_0_0_1=(Token)input.LT(1);
            match(input,ID,FOLLOW_ID_in_ruleProcess235); 
             result = factory.create("new Process", "setType(arg1)",value(a_type_0_0_1));
            		loc(start, input.LT(0)==null?input.LT(-1):input.LT(0), result); 

            }

        }
        catch (RecognitionException re) {
            reportError(re);
            recover(input,re);
        }
        finally {
        }
        return result;
    }
    // $ANTLR end ruleProcess


 

    public static final BitSet FOLLOW_ruleRoutes_in_parse47 = new BitSet(new long[]{0x0000000000000000L});
    public static final BitSet FOLLOW_EOF_in_parse49 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_ruleRoute_in_ruleRoutes80 = new BitSet(new long[]{0x0000000000000402L});
    public static final BitSet FOLLOW_10_in_ruleRoute110 = new BitSet(new long[]{0x0000000000000010L});
    public static final BitSet FOLLOW_ID_in_ruleRoute114 = new BitSet(new long[]{0x0000000000003800L});
    public static final BitSet FOLLOW_ruleProcessor_in_ruleRoute119 = new BitSet(new long[]{0x0000000000003800L});
    public static final BitSet FOLLOW_11_in_ruleRoute124 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_ruleSend_in_ruleProcessor154 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_ruleProcess_in_ruleProcessor175 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_12_in_ruleSend200 = new BitSet(new long[]{0x0000000000000010L});
    public static final BitSet FOLLOW_ID_in_ruleSend204 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_13_in_ruleProcess231 = new BitSet(new long[]{0x0000000000000010L});
    public static final BitSet FOLLOW_ID_in_ruleProcess235 = new BitSet(new long[]{0x0000000000000002L});

}