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
package org.apache.camel.language.simple.types;

/**
 * Types of binary operators supported
 */
public enum BinaryOperatorType {

    EQ, EQ_IGNORE, GT, GTE, LT, LTE, NOT_EQ, CONTAINS, NOT_CONTAINS, 
    CONTAINS_IGNORECASE, REGEX, NOT_REGEX,
    IN, NOT_IN, IS, NOT_IS, RANGE, NOT_RANGE, STARTS_WITH, ENDS_WITH;

    public static BinaryOperatorType asOperator(String text) {
        if ("==".equals(text)) {
            return EQ;
        } else if ("=~".equals(text)) {
            return EQ_IGNORE;
        } else if (">".equals(text)) {
            return GT;
        } else if (">=".equals(text)) {
            return GTE;
        } else if ("<".equals(text)) {
            return LT;
        } else if ("<=".equals(text)) {
            return LTE;
        } else if ("!=".equals(text)) {
            return NOT_EQ;
        } else if ("contains".equals(text)) {
            return CONTAINS;
        } else if ("not contains".equals(text)) {
            return NOT_CONTAINS;
        } else if ("~~".equals(text)) {
            return CONTAINS_IGNORECASE;
        } else if ("regex".equals(text)) {
            return REGEX;
        } else if ("not regex".equals(text)) {
            return NOT_REGEX;
        } else if ("in".equals(text)) {
            return IN;
        } else if ("not in".equals(text)) {
            return NOT_IN;
        } else if ("is".equals(text)) {
            return IS;
        } else if ("not is".equals(text)) {
            return NOT_IS;
        } else if ("range".equals(text)) {
            return RANGE;
        } else if ("not range".equals(text)) {
            return NOT_RANGE;
        } else if ("starts with".equals(text)) {
            return STARTS_WITH;
        } else if ("ends with".equals(text)) {
            return ENDS_WITH;
        }
        throw new IllegalArgumentException("Operator not supported: " + text);
    }

    public static String getOperatorText(BinaryOperatorType operator) {
        if (operator == EQ) {
            return "==";
        } else if (operator == EQ_IGNORE) {
            return "=~";
        } else if (operator == GT) {
            return ">";
        } else if (operator == GTE) {
            return ">=";
        } else if (operator == LT) {
            return "<";
        } else if (operator == LTE) {
            return "<=";
        } else if (operator == NOT_EQ) {
            return "!=";
        } else if (operator == CONTAINS) {
            return "contains";
        } else if (operator == NOT_CONTAINS) {
            return "not contains";
        } else if (operator == CONTAINS_IGNORECASE) {
            return "~~";
        } else if (operator == REGEX) {
            return "regex";
        } else if (operator == NOT_REGEX) {
            return "not regex";
        } else if (operator == IN) {
            return "in";
        } else if (operator == NOT_IN) {
            return "not in";
        } else if (operator == IS) {
            return "is";
        } else if (operator == NOT_IS) {
            return "not is";
        } else if (operator == RANGE) {
            return "range";
        } else if (operator == NOT_RANGE) {
            return "not range";
        } else if (operator == STARTS_WITH) {
            return "starts with";
        } else if (operator == ENDS_WITH) {
            return "ends with";
        }
        return "";
    }

    /**
     * Parameter types a binary operator supports on the right hand side.
     * <ul>
     *     <li>Literal - Only literals enclosed by single quotes</li>
     *     <li>LiteralWithFunction - literals which may have embedded functions enclosed by single quotes</li>
     *     <li>Function - A function</li>
     *     <li>NumericValue - A numeric value</li>
     *     <li>BooleanValue - A boolean value</li>
     *     <li>NullValue - A null value</li>
     * </ul>
     */
    public enum ParameterType {
        Literal, LiteralWithFunction, Function, NumericValue, BooleanValue, NullValue, MinusValue;

        public boolean isLiteralSupported() {
            return this == Literal;
        }

        public boolean isLiteralWithFunctionSupport() {
            return this == LiteralWithFunction;
        }

        public boolean isFunctionSupport() {
            return this == Function;
        }

        public boolean isNumericValueSupported() {
            return this == NumericValue;
        }

        public boolean isBooleanValueSupported() {
            return this == BooleanValue;
        }

        public boolean isNullValueSupported() {
            return this == NullValue;
        }
        
        public boolean isMinusValueSupported() {
            return this == MinusValue;
        }
    }

    /**
     * Returns the types of right hand side parameters this operator supports.
     *
     * @param operator the operator
     * @return <tt>null</tt> if accepting all types, otherwise the array of accepted types
     */
    public static ParameterType[] supportedParameterTypes(BinaryOperatorType operator) {
        if (operator == EQ) {
            return null;
        } else if (operator == EQ_IGNORE) {
            return null;
        } else if (operator == GT) {
            return null;
        } else if (operator == GTE) {
            return null;
        } else if (operator == LT) {
            return null;
        } else if (operator == LTE) {
            return null;
        } else if (operator == NOT_EQ) {
            return null;
        } else if (operator == CONTAINS) {
            return null;
        } else if (operator == NOT_CONTAINS) {
            return null;
        } else if (operator == CONTAINS_IGNORECASE) {
            return null;
        } else if (operator == REGEX) {
            return new ParameterType[]{ParameterType.Literal, ParameterType.Function};
        } else if (operator == NOT_REGEX) {
            return new ParameterType[]{ParameterType.Literal, ParameterType.Function};
        } else if (operator == IN) {
            return null;
        } else if (operator == NOT_IN) {
            return null;
        } else if (operator == IS) {
            return new ParameterType[]{ParameterType.LiteralWithFunction, ParameterType.Function};
        } else if (operator == NOT_IS) {
            return new ParameterType[]{ParameterType.LiteralWithFunction, ParameterType.Function};
        } else if (operator == RANGE) {
            return new ParameterType[]{ParameterType.LiteralWithFunction, ParameterType.Function};
        } else if (operator == NOT_RANGE) {
            return new ParameterType[]{ParameterType.LiteralWithFunction, ParameterType.Function};
        } else if (operator == STARTS_WITH) {
            return null;
        } else if (operator == ENDS_WITH) {
            return null;
        }
        return null;
    }

    @Override
    public String toString() {
        return getOperatorText(this);
    }

}
