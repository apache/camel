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

import java.util.Arrays;

import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.language.simple.types.SimpleTokenType;
import org.apache.camel.language.simple.types.TokenType;

/**
 * Tokenizer for init blocks to create {@link SimpleToken} from the input.
 */
public class SimpleInitBlockTokenizer extends SimpleTokenizer {

    // keep this number in sync with tokens list
    private static final int NUMBER_OF_TOKENS = 4;

    private static final SimpleTokenType[] INIT_TOKENS = new SimpleTokenType[NUMBER_OF_TOKENS];

    // optimize to be able to quick check for init block
    public static final String INIT_START = "$init{";
    // optimize to be able to quick check for init block
    public static final String INIT_END = "}init$";

    static {
        // init
        INIT_TOKENS[0] = new SimpleTokenType(TokenType.initVariable, "$");
        INIT_TOKENS[1] = new SimpleTokenType(TokenType.initOperator, ":=");
        INIT_TOKENS[2] = new SimpleTokenType(TokenType.initOperator, "~:=");
        INIT_TOKENS[3] = new SimpleTokenType(TokenType.initFunctionEnd, ";\n");
    }

    private boolean acceptInitTokens = true; // flag to turn on|off
    private boolean newLine = true;

    /**
     * Does the expression include a simple init block.
     *
     * @param  expression the expression
     * @return            <tt>true</tt> if init block exists
     */
    public static boolean hasInitBlock(String expression) {
        if (expression != null) {
            return expression.startsWith(INIT_START) && expression.contains(INIT_END);
        }
        return false;
    }

    protected void setAcceptInitTokens(boolean accept, int index) {
        this.acceptInitTokens = accept;
        if (!accept) {
            this.newLine = false;
        }
    }

    protected boolean hasNewLine() {
        return newLine;
    }

    @Override
    protected void onToken(SimpleTokenType token, int index) {
        if (token.isNewLine()) {
            this.newLine = true;
        }
    }

    @Override
    protected SimpleToken customToken(String expression, int index, boolean allowEscape, TokenType... filters) {
        // when discovering init tokens we must have seen a new-line prior
        if (!acceptInitTokens) {
            return null;
        }
        if (!newLine) {
            return null;
        }

        // it could be any of the known tokens
        String text = expression.substring(index);
        for (int i = 0; i < NUMBER_OF_TOKENS; i++) {
            SimpleTokenType token = INIT_TOKENS[i];
            if (acceptType(token.getType(), filters)
                    && acceptToken(token, text, expression, index)) {
                return new SimpleToken(token, index);
            }
        }

        boolean initVar = Arrays.asList(filters).contains(TokenType.initVariable);
        if (initVar) {
            // if we are filtering to find an init variable then we need to ignore when not found
            char ch = text.charAt(0);
            return new SimpleToken(new SimpleTokenType(TokenType.ignore, String.valueOf(ch)), index);
        }

        return null;
    }

}
