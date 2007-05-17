// $ANTLR 3.0b6 ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g 2007-04-05 12:20:03

package org.apache.camel.eclipse.parser; 


import org.antlr.runtime.*;
import java.util.Stack;
import java.util.List;
import java.util.ArrayList;

public class camelLexer extends Lexer {
    public static final int T10=10;
    public static final int T11=11;
    public static final int INT=6;
    public static final int EOF=-1;
    public static final int WS=9;
    public static final int STRING=5;
    public static final int T12=12;
    public static final int Tokens=14;
    public static final int ML_COMMENT=7;
    public static final int T13=13;
    public static final int SL_COMMENT=8;
    public static final int ID=4;
    public camelLexer() {;} 
    public camelLexer(CharStream input) {
        super(input);
    }
    public String getGrammarFileName() { return "..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g"; }

    // $ANTLR start T10
    public void mT10() throws RecognitionException {
        try {
            ruleNestingLevel++;
            int _type = T10;
            int _start = getCharIndex();
            int _line = getLine();
            int _charPosition = getCharPositionInLine();
            int _channel = Token.DEFAULT_CHANNEL;
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:6:7: ( 'from' )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:6:7: 'from'
            {
            match("from"); 


            }



                    if ( token==null && ruleNestingLevel==1 ) {
                        emit(_type,_line,_charPosition,_channel,_start,getCharIndex()-1);
                    }

                        }
        finally {
            ruleNestingLevel--;
        }
    }
    // $ANTLR end T10

    // $ANTLR start T11
    public void mT11() throws RecognitionException {
        try {
            ruleNestingLevel++;
            int _type = T11;
            int _start = getCharIndex();
            int _line = getLine();
            int _charPosition = getCharPositionInLine();
            int _channel = Token.DEFAULT_CHANNEL;
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:7:7: ( ';' )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:7:7: ';'
            {
            match(';'); 

            }



                    if ( token==null && ruleNestingLevel==1 ) {
                        emit(_type,_line,_charPosition,_channel,_start,getCharIndex()-1);
                    }

                        }
        finally {
            ruleNestingLevel--;
        }
    }
    // $ANTLR end T11

    // $ANTLR start T12
    public void mT12() throws RecognitionException {
        try {
            ruleNestingLevel++;
            int _type = T12;
            int _start = getCharIndex();
            int _line = getLine();
            int _charPosition = getCharPositionInLine();
            int _channel = Token.DEFAULT_CHANNEL;
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:8:7: ( 'to' )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:8:7: 'to'
            {
            match("to"); 


            }



                    if ( token==null && ruleNestingLevel==1 ) {
                        emit(_type,_line,_charPosition,_channel,_start,getCharIndex()-1);
                    }

                        }
        finally {
            ruleNestingLevel--;
        }
    }
    // $ANTLR end T12

    // $ANTLR start T13
    public void mT13() throws RecognitionException {
        try {
            ruleNestingLevel++;
            int _type = T13;
            int _start = getCharIndex();
            int _line = getLine();
            int _charPosition = getCharPositionInLine();
            int _channel = Token.DEFAULT_CHANNEL;
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:9:7: ( 'process' )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:9:7: 'process'
            {
            match("process"); 


            }



                    if ( token==null && ruleNestingLevel==1 ) {
                        emit(_type,_line,_charPosition,_channel,_start,getCharIndex()-1);
                    }

                        }
        finally {
            ruleNestingLevel--;
        }
    }
    // $ANTLR end T13

    // $ANTLR start STRING
    public void mSTRING() throws RecognitionException {
        try {
            ruleNestingLevel++;
            int _type = STRING;
            int _start = getCharIndex();
            int _line = getLine();
            int _charPosition = getCharPositionInLine();
            int _channel = Token.DEFAULT_CHANNEL;
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:123:5: ( '\"' ( '\\\\' ('b'|'t'|'n'|'f'|'r'|'\\\"'|'\\''|'\\\\') | ~ ('\\\\'|'\"'))* '\"' | '\\'' ( '\\\\' ('b'|'t'|'n'|'f'|'r'|'\\\"'|'\\''|'\\\\') | ~ ('\\\\'|'\\''))* '\\'' )
            int alt3=2;
            int LA3_0 = input.LA(1);
            if ( (LA3_0=='\"') ) {
                alt3=1;
            }
            else if ( (LA3_0=='\'') ) {
                alt3=2;
            }
            else {
                NoViableAltException nvae =
                    new NoViableAltException("122:1: STRING : ( '\"' ( '\\\\' ('b'|'t'|'n'|'f'|'r'|'\\\"'|'\\''|'\\\\') | ~ ('\\\\'|'\"'))* '\"' | '\\'' ( '\\\\' ('b'|'t'|'n'|'f'|'r'|'\\\"'|'\\''|'\\\\') | ~ ('\\\\'|'\\''))* '\\'' );", 3, 0, input);

                throw nvae;
            }
            switch (alt3) {
                case 1 :
                    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:123:5: '\"' ( '\\\\' ('b'|'t'|'n'|'f'|'r'|'\\\"'|'\\''|'\\\\') | ~ ('\\\\'|'\"'))* '\"'
                    {
                    match('\"'); 
                    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:123:9: ( '\\\\' ('b'|'t'|'n'|'f'|'r'|'\\\"'|'\\''|'\\\\') | ~ ('\\\\'|'\"'))*
                    loop1:
                    do {
                        int alt1=3;
                        int LA1_0 = input.LA(1);
                        if ( (LA1_0=='\\') ) {
                            alt1=1;
                        }
                        else if ( ((LA1_0>='\u0000' && LA1_0<='!')||(LA1_0>='#' && LA1_0<='[')||(LA1_0>=']' && LA1_0<='\uFFFE')) ) {
                            alt1=2;
                        }


                        switch (alt1) {
                    	case 1 :
                    	    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:123:11: '\\\\' ('b'|'t'|'n'|'f'|'r'|'\\\"'|'\\''|'\\\\')
                    	    {
                    	    match('\\'); 
                    	    if ( input.LA(1)=='\"'||input.LA(1)=='\''||input.LA(1)=='\\'||input.LA(1)=='b'||input.LA(1)=='f'||input.LA(1)=='n'||input.LA(1)=='r'||input.LA(1)=='t' ) {
                    	        input.consume();

                    	    }
                    	    else {
                    	        MismatchedSetException mse =
                    	            new MismatchedSetException(null,input);
                    	        recover(mse);    throw mse;
                    	    }


                    	    }
                    	    break;
                    	case 2 :
                    	    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:123:55: ~ ('\\\\'|'\"')
                    	    {
                    	    if ( (input.LA(1)>='\u0000' && input.LA(1)<='!')||(input.LA(1)>='#' && input.LA(1)<='[')||(input.LA(1)>=']' && input.LA(1)<='\uFFFE') ) {
                    	        input.consume();

                    	    }
                    	    else {
                    	        MismatchedSetException mse =
                    	            new MismatchedSetException(null,input);
                    	        recover(mse);    throw mse;
                    	    }


                    	    }
                    	    break;

                    	default :
                    	    break loop1;
                        }
                    } while (true);

                    match('\"'); 

                    }
                    break;
                case 2 :
                    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:124:5: '\\'' ( '\\\\' ('b'|'t'|'n'|'f'|'r'|'\\\"'|'\\''|'\\\\') | ~ ('\\\\'|'\\''))* '\\''
                    {
                    match('\''); 
                    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:124:10: ( '\\\\' ('b'|'t'|'n'|'f'|'r'|'\\\"'|'\\''|'\\\\') | ~ ('\\\\'|'\\''))*
                    loop2:
                    do {
                        int alt2=3;
                        int LA2_0 = input.LA(1);
                        if ( (LA2_0=='\\') ) {
                            alt2=1;
                        }
                        else if ( ((LA2_0>='\u0000' && LA2_0<='&')||(LA2_0>='(' && LA2_0<='[')||(LA2_0>=']' && LA2_0<='\uFFFE')) ) {
                            alt2=2;
                        }


                        switch (alt2) {
                    	case 1 :
                    	    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:124:12: '\\\\' ('b'|'t'|'n'|'f'|'r'|'\\\"'|'\\''|'\\\\')
                    	    {
                    	    match('\\'); 
                    	    if ( input.LA(1)=='\"'||input.LA(1)=='\''||input.LA(1)=='\\'||input.LA(1)=='b'||input.LA(1)=='f'||input.LA(1)=='n'||input.LA(1)=='r'||input.LA(1)=='t' ) {
                    	        input.consume();

                    	    }
                    	    else {
                    	        MismatchedSetException mse =
                    	            new MismatchedSetException(null,input);
                    	        recover(mse);    throw mse;
                    	    }


                    	    }
                    	    break;
                    	case 2 :
                    	    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:124:56: ~ ('\\\\'|'\\'')
                    	    {
                    	    if ( (input.LA(1)>='\u0000' && input.LA(1)<='&')||(input.LA(1)>='(' && input.LA(1)<='[')||(input.LA(1)>=']' && input.LA(1)<='\uFFFE') ) {
                    	        input.consume();

                    	    }
                    	    else {
                    	        MismatchedSetException mse =
                    	            new MismatchedSetException(null,input);
                    	        recover(mse);    throw mse;
                    	    }


                    	    }
                    	    break;

                    	default :
                    	    break loop2;
                        }
                    } while (true);

                    match('\''); 

                    }
                    break;

            }


                    if ( token==null && ruleNestingLevel==1 ) {
                        emit(_type,_line,_charPosition,_channel,_start,getCharIndex()-1);
                    }

                        }
        finally {
            ruleNestingLevel--;
        }
    }
    // $ANTLR end STRING

    // $ANTLR start ID
    public void mID() throws RecognitionException {
        try {
            ruleNestingLevel++;
            int _type = ID;
            int _start = getCharIndex();
            int _line = getLine();
            int _charPosition = getCharPositionInLine();
            int _channel = Token.DEFAULT_CHANNEL;
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:129:2: ( ( '^' )? ('a'..'z'|'A'..'Z'|'_') ( ('a'..'z'|'A'..'Z'|'_'|'0'..'9'))* )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:129:2: ( '^' )? ('a'..'z'|'A'..'Z'|'_') ( ('a'..'z'|'A'..'Z'|'_'|'0'..'9'))*
            {
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:129:2: ( '^' )?
            int alt4=2;
            int LA4_0 = input.LA(1);
            if ( (LA4_0=='^') ) {
                alt4=1;
            }
            switch (alt4) {
                case 1 :
                    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:129:3: '^'
                    {
                    match('^'); 

                    }
                    break;

            }

            if ( (input.LA(1)>='A' && input.LA(1)<='Z')||input.LA(1)=='_'||(input.LA(1)>='a' && input.LA(1)<='z') ) {
                input.consume();

            }
            else {
                MismatchedSetException mse =
                    new MismatchedSetException(null,input);
                recover(mse);    throw mse;
            }

            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:129:32: ( ('a'..'z'|'A'..'Z'|'_'|'0'..'9'))*
            loop5:
            do {
                int alt5=2;
                int LA5_0 = input.LA(1);
                if ( ((LA5_0>='0' && LA5_0<='9')||(LA5_0>='A' && LA5_0<='Z')||LA5_0=='_'||(LA5_0>='a' && LA5_0<='z')) ) {
                    alt5=1;
                }


                switch (alt5) {
            	case 1 :
            	    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:129:33: ('a'..'z'|'A'..'Z'|'_'|'0'..'9')
            	    {
            	    if ( (input.LA(1)>='0' && input.LA(1)<='9')||(input.LA(1)>='A' && input.LA(1)<='Z')||input.LA(1)=='_'||(input.LA(1)>='a' && input.LA(1)<='z') ) {
            	        input.consume();

            	    }
            	    else {
            	        MismatchedSetException mse =
            	            new MismatchedSetException(null,input);
            	        recover(mse);    throw mse;
            	    }


            	    }
            	    break;

            	default :
            	    break loop5;
                }
            } while (true);


            }



                    if ( token==null && ruleNestingLevel==1 ) {
                        emit(_type,_line,_charPosition,_channel,_start,getCharIndex()-1);
                    }

                        }
        finally {
            ruleNestingLevel--;
        }
    }
    // $ANTLR end ID

    // $ANTLR start INT
    public void mINT() throws RecognitionException {
        try {
            ruleNestingLevel++;
            int _type = INT;
            int _start = getCharIndex();
            int _line = getLine();
            int _charPosition = getCharPositionInLine();
            int _channel = Token.DEFAULT_CHANNEL;
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:135:2: ( ( '0' .. '9' )+ )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:135:2: ( '0' .. '9' )+
            {
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:135:2: ( '0' .. '9' )+
            int cnt6=0;
            loop6:
            do {
                int alt6=2;
                int LA6_0 = input.LA(1);
                if ( ((LA6_0>='0' && LA6_0<='9')) ) {
                    alt6=1;
                }


                switch (alt6) {
            	case 1 :
            	    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:135:3: '0' .. '9'
            	    {
            	    matchRange('0','9'); 

            	    }
            	    break;

            	default :
            	    if ( cnt6 >= 1 ) break loop6;
                        EarlyExitException eee =
                            new EarlyExitException(6, input);
                        throw eee;
                }
                cnt6++;
            } while (true);


            }



                    if ( token==null && ruleNestingLevel==1 ) {
                        emit(_type,_line,_charPosition,_channel,_start,getCharIndex()-1);
                    }

                        }
        finally {
            ruleNestingLevel--;
        }
    }
    // $ANTLR end INT

    // $ANTLR start ML_COMMENT
    public void mML_COMMENT() throws RecognitionException {
        try {
            ruleNestingLevel++;
            int _type = ML_COMMENT;
            int _start = getCharIndex();
            int _line = getLine();
            int _charPosition = getCharPositionInLine();
            int _channel = Token.DEFAULT_CHANNEL;
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:141:9: ( '/*' ( options {greedy=false; } : . )* '*/' )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:141:9: '/*' ( options {greedy=false; } : . )* '*/'
            {
            match("/*"); 

            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:141:14: ( options {greedy=false; } : . )*
            loop7:
            do {
                int alt7=2;
                int LA7_0 = input.LA(1);
                if ( (LA7_0=='*') ) {
                    int LA7_1 = input.LA(2);
                    if ( (LA7_1=='/') ) {
                        alt7=2;
                    }
                    else if ( ((LA7_1>='\u0000' && LA7_1<='.')||(LA7_1>='0' && LA7_1<='\uFFFE')) ) {
                        alt7=1;
                    }


                }
                else if ( ((LA7_0>='\u0000' && LA7_0<=')')||(LA7_0>='+' && LA7_0<='\uFFFE')) ) {
                    alt7=1;
                }


                switch (alt7) {
            	case 1 :
            	    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:141:42: .
            	    {
            	    matchAny(); 

            	    }
            	    break;

            	default :
            	    break loop7;
                }
            } while (true);

            match("*/"); 

            skip();

            }



                    if ( token==null && ruleNestingLevel==1 ) {
                        emit(_type,_line,_charPosition,_channel,_start,getCharIndex()-1);
                    }

                        }
        finally {
            ruleNestingLevel--;
        }
    }
    // $ANTLR end ML_COMMENT

    // $ANTLR start SL_COMMENT
    public void mSL_COMMENT() throws RecognitionException {
        try {
            ruleNestingLevel++;
            int _type = SL_COMMENT;
            int _start = getCharIndex();
            int _line = getLine();
            int _charPosition = getCharPositionInLine();
            int _channel = Token.DEFAULT_CHANNEL;
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:147:7: ( '//' (~ ('\\n'|'\\r'))* ( '\\r' )? '\\n' )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:147:7: '//' (~ ('\\n'|'\\r'))* ( '\\r' )? '\\n'
            {
            match("//"); 

            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:147:12: (~ ('\\n'|'\\r'))*
            loop8:
            do {
                int alt8=2;
                int LA8_0 = input.LA(1);
                if ( ((LA8_0>='\u0000' && LA8_0<='\t')||(LA8_0>='\u000B' && LA8_0<='\f')||(LA8_0>='\u000E' && LA8_0<='\uFFFE')) ) {
                    alt8=1;
                }


                switch (alt8) {
            	case 1 :
            	    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:147:12: ~ ('\\n'|'\\r')
            	    {
            	    if ( (input.LA(1)>='\u0000' && input.LA(1)<='\t')||(input.LA(1)>='\u000B' && input.LA(1)<='\f')||(input.LA(1)>='\u000E' && input.LA(1)<='\uFFFE') ) {
            	        input.consume();

            	    }
            	    else {
            	        MismatchedSetException mse =
            	            new MismatchedSetException(null,input);
            	        recover(mse);    throw mse;
            	    }


            	    }
            	    break;

            	default :
            	    break loop8;
                }
            } while (true);

            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:147:26: ( '\\r' )?
            int alt9=2;
            int LA9_0 = input.LA(1);
            if ( (LA9_0=='\r') ) {
                alt9=1;
            }
            switch (alt9) {
                case 1 :
                    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:147:26: '\\r'
                    {
                    match('\r'); 

                    }
                    break;

            }

            match('\n'); 
            skip();

            }



                    if ( token==null && ruleNestingLevel==1 ) {
                        emit(_type,_line,_charPosition,_channel,_start,getCharIndex()-1);
                    }

                        }
        finally {
            ruleNestingLevel--;
        }
    }
    // $ANTLR end SL_COMMENT

    // $ANTLR start WS
    public void mWS() throws RecognitionException {
        try {
            ruleNestingLevel++;
            int _type = WS;
            int _start = getCharIndex();
            int _line = getLine();
            int _charPosition = getCharPositionInLine();
            int _channel = Token.DEFAULT_CHANNEL;
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:152:6: ( ( (' '|'\\t'|'\\r'|'\\n'))+ )
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:152:6: ( (' '|'\\t'|'\\r'|'\\n'))+
            {
            // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:152:6: ( (' '|'\\t'|'\\r'|'\\n'))+
            int cnt10=0;
            loop10:
            do {
                int alt10=2;
                int LA10_0 = input.LA(1);
                if ( ((LA10_0>='\t' && LA10_0<='\n')||LA10_0=='\r'||LA10_0==' ') ) {
                    alt10=1;
                }


                switch (alt10) {
            	case 1 :
            	    // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:152:7: (' '|'\\t'|'\\r'|'\\n')
            	    {
            	    if ( (input.LA(1)>='\t' && input.LA(1)<='\n')||input.LA(1)=='\r'||input.LA(1)==' ' ) {
            	        input.consume();

            	    }
            	    else {
            	        MismatchedSetException mse =
            	            new MismatchedSetException(null,input);
            	        recover(mse);    throw mse;
            	    }


            	    }
            	    break;

            	default :
            	    if ( cnt10 >= 1 ) break loop10;
                        EarlyExitException eee =
                            new EarlyExitException(10, input);
                        throw eee;
                }
                cnt10++;
            } while (true);

            skip();

            }



                    if ( token==null && ruleNestingLevel==1 ) {
                        emit(_type,_line,_charPosition,_channel,_start,getCharIndex()-1);
                    }

                        }
        finally {
            ruleNestingLevel--;
        }
    }
    // $ANTLR end WS

    public void mTokens() throws RecognitionException {
        // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:1:10: ( T10 | T11 | T12 | T13 | STRING | ID | INT | ML_COMMENT | SL_COMMENT | WS )
        int alt11=10;
        switch ( input.LA(1) ) {
        case 'f':
            int LA11_1 = input.LA(2);
            if ( (LA11_1=='r') ) {
                int LA11_10 = input.LA(3);
                if ( (LA11_10=='o') ) {
                    int LA11_15 = input.LA(4);
                    if ( (LA11_15=='m') ) {
                        int LA11_18 = input.LA(5);
                        if ( ((LA11_18>='0' && LA11_18<='9')||(LA11_18>='A' && LA11_18<='Z')||LA11_18=='_'||(LA11_18>='a' && LA11_18<='z')) ) {
                            alt11=6;
                        }
                        else {
                            alt11=1;}
                    }
                    else {
                        alt11=6;}
                }
                else {
                    alt11=6;}
            }
            else {
                alt11=6;}
            break;
        case ';':
            alt11=2;
            break;
        case 't':
            int LA11_3 = input.LA(2);
            if ( (LA11_3=='o') ) {
                int LA11_11 = input.LA(3);
                if ( ((LA11_11>='0' && LA11_11<='9')||(LA11_11>='A' && LA11_11<='Z')||LA11_11=='_'||(LA11_11>='a' && LA11_11<='z')) ) {
                    alt11=6;
                }
                else {
                    alt11=3;}
            }
            else {
                alt11=6;}
            break;
        case 'p':
            int LA11_4 = input.LA(2);
            if ( (LA11_4=='r') ) {
                int LA11_12 = input.LA(3);
                if ( (LA11_12=='o') ) {
                    int LA11_17 = input.LA(4);
                    if ( (LA11_17=='c') ) {
                        int LA11_19 = input.LA(5);
                        if ( (LA11_19=='e') ) {
                            int LA11_21 = input.LA(6);
                            if ( (LA11_21=='s') ) {
                                int LA11_22 = input.LA(7);
                                if ( (LA11_22=='s') ) {
                                    int LA11_23 = input.LA(8);
                                    if ( ((LA11_23>='0' && LA11_23<='9')||(LA11_23>='A' && LA11_23<='Z')||LA11_23=='_'||(LA11_23>='a' && LA11_23<='z')) ) {
                                        alt11=6;
                                    }
                                    else {
                                        alt11=4;}
                                }
                                else {
                                    alt11=6;}
                            }
                            else {
                                alt11=6;}
                        }
                        else {
                            alt11=6;}
                    }
                    else {
                        alt11=6;}
                }
                else {
                    alt11=6;}
            }
            else {
                alt11=6;}
            break;
        case '\"':
        case '\'':
            alt11=5;
            break;
        case 'A':
        case 'B':
        case 'C':
        case 'D':
        case 'E':
        case 'F':
        case 'G':
        case 'H':
        case 'I':
        case 'J':
        case 'K':
        case 'L':
        case 'M':
        case 'N':
        case 'O':
        case 'P':
        case 'Q':
        case 'R':
        case 'S':
        case 'T':
        case 'U':
        case 'V':
        case 'W':
        case 'X':
        case 'Y':
        case 'Z':
        case '^':
        case '_':
        case 'a':
        case 'b':
        case 'c':
        case 'd':
        case 'e':
        case 'g':
        case 'h':
        case 'i':
        case 'j':
        case 'k':
        case 'l':
        case 'm':
        case 'n':
        case 'o':
        case 'q':
        case 'r':
        case 's':
        case 'u':
        case 'v':
        case 'w':
        case 'x':
        case 'y':
        case 'z':
            alt11=6;
            break;
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
            alt11=7;
            break;
        case '/':
            int LA11_8 = input.LA(2);
            if ( (LA11_8=='/') ) {
                alt11=9;
            }
            else if ( (LA11_8=='*') ) {
                alt11=8;
            }
            else {
                NoViableAltException nvae =
                    new NoViableAltException("1:1: Tokens : ( T10 | T11 | T12 | T13 | STRING | ID | INT | ML_COMMENT | SL_COMMENT | WS );", 11, 8, input);

                throw nvae;
            }
            break;
        case '\t':
        case '\n':
        case '\r':
        case ' ':
            alt11=10;
            break;
        default:
            NoViableAltException nvae =
                new NoViableAltException("1:1: Tokens : ( T10 | T11 | T12 | T13 | STRING | ID | INT | ML_COMMENT | SL_COMMENT | WS );", 11, 0, input);

            throw nvae;
        }

        switch (alt11) {
            case 1 :
                // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:1:10: T10
                {
                mT10(); 

                }
                break;
            case 2 :
                // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:1:14: T11
                {
                mT11(); 

                }
                break;
            case 3 :
                // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:1:18: T12
                {
                mT12(); 

                }
                break;
            case 4 :
                // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:1:22: T13
                {
                mT13(); 

                }
                break;
            case 5 :
                // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:1:26: STRING
                {
                mSTRING(); 

                }
                break;
            case 6 :
                // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:1:33: ID
                {
                mID(); 

                }
                break;
            case 7 :
                // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:1:36: INT
                {
                mINT(); 

                }
                break;
            case 8 :
                // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:1:40: ML_COMMENT
                {
                mML_COMMENT(); 

                }
                break;
            case 9 :
                // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:1:51: SL_COMMENT
                {
                mSL_COMMENT(); 

                }
                break;
            case 10 :
                // ..//camel.routing/src-gen//org/apache/camel/eclipse/parser/camel.g:1:62: WS
                {
                mWS(); 

                }
                break;

        }

    }


 

}