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
package org.apache.camel.language.simple.types;

/**
 * The different token types used by the simple parser.
 */
public final class SimpleTokenType {

    private final TokenType type;
    private final String value;

    public SimpleTokenType(TokenType type, String value) {
        this.type = type;
        this.value = value;
    }

    /**
     * Gets the type of this token
     *
     * @return the type
     */
    public TokenType getType() {
        return type;
    }

    /**
     * Gets the input value in this token
     *
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * Whether the type is ignore token
     */
    public boolean isIgnore() {
        return type == TokenType.ignore;
    }

    /**
     * Whether the type is whitespace
     */
    public boolean isWhitespace() {
        return type == TokenType.whiteSpace;
    }

    /**
     * Whether the type is a new-line character
     */
    public boolean isNewLine() {
        return isWhitespace() && "\n".equals(value);
    }

    /**
     * Whether the type is eol
     */
    public boolean isEol() {
        return type == TokenType.eol;
    }

    /**
     * Whether the type is escape
     */
    public boolean isEscape() {
        return type == TokenType.escape;
    }

    /**
     * Whether the type is single quote
     */
    public boolean isSingleQuote() {
        return type == TokenType.singleQuote;
    }

    /**
     * Whether the type is double quote
     */
    public boolean isDoubleQuote() {
        return type == TokenType.doubleQuote;
    }

    /**
     * Whether the type is a function start
     */
    public boolean isFunctionStart() {
        return type == TokenType.functionStart;
    }

    /**
     * Whether the type is a function end
     */
    public boolean isFunctionEnd() {
        return type == TokenType.functionEnd;
    }

    /**
     * Whether the type is binary operator
     */
    public boolean isBinary() {
        return type == TokenType.binaryOperator;
    }

    /**
     * Whether the type is other operator
     */
    public boolean isOther() {
        return type == TokenType.otherOperator;
    }

    /**
     * Whether the type is unary operator
     */
    public boolean isUnary() {
        return type == TokenType.unaryOperator;
    }

    /**
     * Whether the type is logical operator
     */
    public boolean isLogical() {
        return type == TokenType.logicalOperator;
    }

    /**
     * Whether the type is init operator
     */
    public boolean isInit() {
        return type == TokenType.initOperator;
    }

    /**
     * Whether the type is init variable
     */
    public boolean isInitVariable() {
        return type == TokenType.initVariable;
    }

    /**
     * Whether the type is init function end
     */
    public boolean isInitFunctionEnd() {
        return type == TokenType.initFunctionEnd;
    }

    /**
     * Whether the type is a null value
     */
    public boolean isNullValue() {
        return type == TokenType.nullValue;
    }

    /**
     * Whether the type is a minus operator
     */
    public boolean isMinusValue() {
        return type == TokenType.minusValue;
    }

    /**
     * Whether the type is a boolean value
     */
    public boolean isBooleanValue() {
        return type == TokenType.booleanValue;
    }

    /**
     * Whether the type is a numeric value (can be integer based or floating point)
     */
    public boolean isNumericValue() {
        return type == TokenType.numericValue;
    }

    /**
     * Whether the type is ternary operator
     */
    public boolean isTernary() {
        return type == TokenType.ternaryOperator;
    }

    /**
     * Whether the type is ternary start operator (?)
     */
    public boolean isTernaryStart() {
        return type == TokenType.ternaryOperator && "?".equals(value);
    }

    /**
     * Whether the type is ternary start operator (:)
     */
    public boolean isTernaryEnd() {
        return type == TokenType.ternaryOperator && ":".equals(value);
    }

    /**
     * Whether the type is chain operator
     */
    public boolean isChain() {
        return type == TokenType.chainOperator;
    }

    @Override
    public String toString() {
        return value;
    }
}
