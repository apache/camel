/**
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

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.language.simple.ast.SimpleFunctionExpression;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.language.simple.types.SimpleTokenType;
import org.apache.camel.language.simple.types.TokenType;
import org.apache.camel.util.ExpressionToPredicateAdapter;

/**
 * A backwards compatible parser, which supports the old simple language
 * syntax by which simple functions can be given without using start and
 * end tokens.
 * <p/>
 * For example "body" would be parsed as the body function, where as the
 * new parser would require that to be entered as "${body}".
 * <p/>
 * This parser is to be removed when the old syntax is no longer supported.
 *
 * @deprecated will be removed in Camel 3.0
 */
@Deprecated
public final class SimpleBackwardsCompatibleParser {

    private SimpleBackwardsCompatibleParser() {
        // static methods
    }

    public static Expression parseExpression(String expression, boolean allowEscape) {
        return doParseExpression(expression, allowEscape);
    }

    public static Predicate parsePredicate(String expression, boolean allowEscape) {
        Expression answer = doParseExpression(expression, allowEscape);
        if (answer != null) {
            return ExpressionToPredicateAdapter.toPredicate(answer);
        } else {
            return null;
        }
    }

    private static Expression doParseExpression(String expression, boolean allowEscape) {
        // should have no function tokens
        for (int i = 0; i < expression.length(); i++) {
            SimpleToken token = SimpleTokenizer.nextToken(expression, i, allowEscape, TokenType.functionStart, TokenType.functionEnd);
            if (token.getType().getType() == TokenType.functionStart || token.getType().getType() == TokenType.functionEnd) {
                return null;
            }
        }

        // okay there is no function tokens, then try to parse it as a simple function expression
        SimpleToken token = new SimpleToken(new SimpleTokenType(TokenType.functionStart, expression), 0);
        SimpleFunctionExpression function = new SimpleFunctionExpression(token);
        function.addText(expression);
        return function.createExpression(expression, false);
    }

}
