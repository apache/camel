/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.language.simple;

import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.language.simple.types.SimpleTokenType;
import org.apache.camel.language.simple.types.TokenType;
import org.apache.camel.util.ObjectHelper;

/**
 * Tokenizer to create {@link SimpleToken} from the input.
 */
public final class SimpleTokenizer {

    // keep this number in sync with tokens list
    private static final int NUMBER_OF_TOKENS = 47;

    private static final SimpleTokenType[] KNOWN_TOKENS = new SimpleTokenType[NUMBER_OF_TOKENS];

    // optimise to be able to quick check for start functions
    private static final String[] FUNCTION_START = new String[] { "${", "$simple{" };
    // optimise to be able to quick check for end function
    private static final String FUNCTION_END = "}";

    static {
        // add known tokens
        KNOWN_TOKENS[0] = new SimpleTokenType(TokenType.functionStart, FUNCTION_START[0]);
        KNOWN_TOKENS[1] = new SimpleTokenType(TokenType.functionStart, FUNCTION_START[1]);
        KNOWN_TOKENS[2] = new SimpleTokenType(TokenType.functionEnd, FUNCTION_END);
        KNOWN_TOKENS[3] = new SimpleTokenType(TokenType.whiteSpace, " ");
        KNOWN_TOKENS[4] = new SimpleTokenType(TokenType.whiteSpace, "\t");
        KNOWN_TOKENS[5] = new SimpleTokenType(TokenType.whiteSpace, "\n");
        KNOWN_TOKENS[6] = new SimpleTokenType(TokenType.whiteSpace, "\r");
        KNOWN_TOKENS[7] = new SimpleTokenType(TokenType.singleQuote, "'");
        KNOWN_TOKENS[8] = new SimpleTokenType(TokenType.doubleQuote, "\"");
        KNOWN_TOKENS[9] = new SimpleTokenType(TokenType.booleanValue, "true");
        KNOWN_TOKENS[10] = new SimpleTokenType(TokenType.booleanValue, "false");
        KNOWN_TOKENS[11] = new SimpleTokenType(TokenType.nullValue, "null");
        KNOWN_TOKENS[12] = new SimpleTokenType(TokenType.escape, "\\");

        // binary operators
        KNOWN_TOKENS[13] = new SimpleTokenType(TokenType.binaryOperator, "==");
        KNOWN_TOKENS[14] = new SimpleTokenType(TokenType.binaryOperator, "=~");
        KNOWN_TOKENS[15] = new SimpleTokenType(TokenType.binaryOperator, ">=");
        KNOWN_TOKENS[16] = new SimpleTokenType(TokenType.binaryOperator, "<=");
        KNOWN_TOKENS[17] = new SimpleTokenType(TokenType.binaryOperator, ">");
        KNOWN_TOKENS[18] = new SimpleTokenType(TokenType.binaryOperator, "<");
        KNOWN_TOKENS[19] = new SimpleTokenType(TokenType.binaryOperator, "!=~");
        KNOWN_TOKENS[20] = new SimpleTokenType(TokenType.binaryOperator, "!=");
        KNOWN_TOKENS[21] = new SimpleTokenType(TokenType.binaryOperator, "not is");
        KNOWN_TOKENS[22] = new SimpleTokenType(TokenType.binaryOperator, "!is");
        KNOWN_TOKENS[23] = new SimpleTokenType(TokenType.binaryOperator, "is");
        KNOWN_TOKENS[24] = new SimpleTokenType(TokenType.binaryOperator, "not contains");
        KNOWN_TOKENS[25] = new SimpleTokenType(TokenType.binaryOperator, "!contains");
        KNOWN_TOKENS[26] = new SimpleTokenType(TokenType.binaryOperator, "contains");
        KNOWN_TOKENS[27] = new SimpleTokenType(TokenType.binaryOperator, "!~~");
        KNOWN_TOKENS[28] = new SimpleTokenType(TokenType.binaryOperator, "~~");
        KNOWN_TOKENS[29] = new SimpleTokenType(TokenType.binaryOperator, "not regex");
        KNOWN_TOKENS[30] = new SimpleTokenType(TokenType.binaryOperator, "!regex");
        KNOWN_TOKENS[31] = new SimpleTokenType(TokenType.binaryOperator, "regex");
        KNOWN_TOKENS[32] = new SimpleTokenType(TokenType.binaryOperator, "not in");
        KNOWN_TOKENS[33] = new SimpleTokenType(TokenType.binaryOperator, "!in");
        KNOWN_TOKENS[34] = new SimpleTokenType(TokenType.binaryOperator, "in");
        KNOWN_TOKENS[35] = new SimpleTokenType(TokenType.binaryOperator, "not range");
        KNOWN_TOKENS[36] = new SimpleTokenType(TokenType.binaryOperator, "!range");
        KNOWN_TOKENS[37] = new SimpleTokenType(TokenType.binaryOperator, "range");
        KNOWN_TOKENS[38] = new SimpleTokenType(TokenType.binaryOperator, "startsWith");
        KNOWN_TOKENS[39] = new SimpleTokenType(TokenType.binaryOperator, "starts with");
        KNOWN_TOKENS[40] = new SimpleTokenType(TokenType.binaryOperator, "endsWith");
        KNOWN_TOKENS[41] = new SimpleTokenType(TokenType.binaryOperator, "ends with");

        // unary operators
        KNOWN_TOKENS[42] = new SimpleTokenType(TokenType.unaryOperator, "++");
        KNOWN_TOKENS[43] = new SimpleTokenType(TokenType.unaryOperator, "--");

        // logical operators
        KNOWN_TOKENS[44] = new SimpleTokenType(TokenType.logicalOperator, "&&");
        KNOWN_TOKENS[45] = new SimpleTokenType(TokenType.logicalOperator, "||");

        //binary operator
        // it is added as the last item because unary -- has the priority
        // if unary not found it is highly possible - operator is run into.
        KNOWN_TOKENS[46] = new SimpleTokenType(TokenType.minusValue, "-");
    }

    private SimpleTokenizer() {
        // static methods
    }

    /**
     * Does the expression include a simple function.
     *
     * @param  expression the expression
     * @return            <tt>true</tt> if one or more simple function is included in the expression
     */
    public static boolean hasFunctionStartToken(String expression) {
        if (expression != null) {
            return expression.contains(FUNCTION_START[0]) || expression.contains(FUNCTION_START[1]);
        }
        return false;
    }

    /**
     * Does the expression include an escape tokens.
     *
     * @param  expression the expression
     * @return            <tt>true</tt> if one or more escape tokens is included in the expression
     */
    public static boolean hasEscapeToken(String expression) {
        if (expression != null) {
            return expression.contains("\\n") || expression.contains("\\t") || expression.contains("\\r")
                    || expression.contains("\\}");
        }
        return false;
    }

    /**
     * Create the next token
     *
     * @param  expression  the input expression
     * @param  index       the current index
     * @param  allowEscape whether to allow escapes
     * @param  filter      defines the accepted token types to be returned (character is always used as fallback)
     * @return             the created token, will always return a token
     */
    public static SimpleToken nextToken(String expression, int index, boolean allowEscape, TokenType... filter) {
        return doNextToken(expression, index, allowEscape, filter);
    }

    /**
     * Create the next token
     *
     * @param  expression  the input expression
     * @param  index       the current index
     * @param  allowEscape whether to allow escapes
     * @return             the created token, will always return a token
     */
    public static SimpleToken nextToken(String expression, int index, boolean allowEscape) {
        return doNextToken(expression, index, allowEscape);
    }

    private static SimpleToken doNextToken(String expression, int index, boolean allowEscape, TokenType... filters) {

        boolean numericAllowed = acceptType(TokenType.numericValue, filters);
        if (numericAllowed) {
            // is it a numeric value
            StringBuilder sb = new StringBuilder();
            boolean digit = true;
            while (digit && index < expression.length()) {
                digit = Character.isDigit(expression.charAt(index));
                if (digit) {
                    char ch = expression.charAt(index);
                    sb.append(ch);
                    index++;
                    continue;
                }
                // is it a dot or comma as part of a floating point number
                boolean decimalSeparator = '.' == expression.charAt(index) || ',' == expression.charAt(index);
                if (decimalSeparator && sb.length() > 0) {
                    char ch = expression.charAt(index);
                    sb.append(ch);
                    index++;
                    // assume its still a digit
                    digit = true;
                    continue;
                }
            }
            if (sb.length() > 0) {
                return new SimpleToken(new SimpleTokenType(TokenType.numericValue, sb.toString()), index);
            }
        }

        boolean escapeAllowed = allowEscape && acceptType(TokenType.escape, filters);
        if (escapeAllowed) {
            StringBuilder sb = new StringBuilder();
            char ch = expression.charAt(index);
            boolean escaped = '\\' == ch;
            if (escaped && index < expression.length() - 1) {
                // grab next character to escape
                char next = expression.charAt(++index);
                // special for new line, tabs and carriage return
                boolean special = false;
                if ('n' == next) {
                    sb.append("\n");
                    special = true;
                } else if ('t' == next) {
                    sb.append("\t");
                    special = true;
                } else if ('r' == next) {
                    sb.append("\r");
                    special = true;
                } else if ('}' == next) {
                    sb.append("}");
                    special = true;
                } else {
                    // not special just a regular character
                    sb.append(ch);
                }

                // force 2 as length if special
                return new SimpleToken(new SimpleTokenType(TokenType.character, sb.toString()), index, special ? 2 : 1);
            }
        }

        // it could be any of the known tokens
        String text = expression.substring(index);
        for (int i = 0; i < NUMBER_OF_TOKENS; i++) {
            SimpleTokenType token = KNOWN_TOKENS[i];
            if (acceptType(token.getType(), filters)
                    && acceptToken(token, text, expression, index)) {
                return new SimpleToken(token, index);
            }
        }

        // fallback and create a character token
        char ch = expression.charAt(index);
        return new SimpleToken(new SimpleTokenType(TokenType.character, String.valueOf(ch)), index);
    }

    private static boolean acceptType(TokenType type, TokenType... filters) {
        if (filters == null || filters.length == 0) {
            return true;
        }
        for (TokenType filter : filters) {
            if (type == filter) {
                return true;
            }
        }
        return false;
    }

    private static boolean acceptToken(SimpleTokenType token, String text, String expression, int index) {
        if (token.isUnary() && text.startsWith(token.getValue())) {
            int endLen = 1;

            // special check for unary as the previous must be a function end, and the next a whitespace
            // to ensure unary operators is only applied on functions as intended
            int len = token.getValue().length();

            String previous = "";
            if (index - endLen >= 0) {
                previous = expression.substring(index - endLen, index);
            }
            String after = text.substring(len);
            boolean whiteSpace = ObjectHelper.isEmpty(after) || after.startsWith(" ");
            boolean functionEnd = previous.equals("}");
            return functionEnd && whiteSpace;
        }

        return text.startsWith(token.getValue());
    }

}
