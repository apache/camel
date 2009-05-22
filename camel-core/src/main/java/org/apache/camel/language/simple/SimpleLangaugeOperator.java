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

/**
 * Operators supported by simple language
 * <ul>
 *   <li>== : equlas</li>
 *   <li>> : greather than</li>
 *   <li>>= : greather than or equals</li>
 *   <li>< : less than</li>
 *   <li><= : less than or equals</li>
 *   <li>!= : not</li>
 *   <li>contains : tested for if it contains the value</li>
 *   <li>not contains : tested for if it does not contain the value</li>
 *   <li>regex : matching a regular expression</li>
 *   <li>not regex : not matching a regular expression</li>
 *   <li>in : tested for in a list of values separated by comma</li>
 *   <li>not in : tested for not in a list of values separated by comma</li>
 *   <li>is : tested for if type is an instanceof the given type</li>
 *   <li>not is: tested for not if type is an instanceof the given type</li>
 *   <li>range : tested for if it is within the provided range</li>
 *   <li>not range : tested for not if it is within the provided range</li>
 *   <li>and : and operator to combine two groups of expressions</li>
 *   <li>or : or operator to combine two groups of expressions</li>
 * </ul>
 * <p/>
 * The <tt>and</tt> and <tt>or</tt> operator is special as they are used as optional operator to combine two expressions.
 * This allows you to build combiled expressions. Currently only one and/or operator is supported, but this might change
 * in the future.
 * <br/>
 * For example we can create this compound expression that has two groups that is combined with the and operator:
 * <tt>${in.header.action} == 'login' and ${in.header.password} != null</tt>
 * <br/>
 */
public enum SimpleLangaugeOperator {

    EQ, GT, GTE, LT, LTE, NOT, CONTAINS, NOT_CONTAINS, REGEX, NOT_REGEX,
    IN, NOT_IN, IS, NOT_IS, RANGE, NOT_RANGE, AND, OR;

    public static SimpleLangaugeOperator asOperator(String text) {
        if ("==".equals(text)) {
            return EQ;
        } else if (">".equals(text)) {
            return GT;
        } else if (">=".equals(text)) {
            return GTE;
        } else if ("<".equals(text)) {
            return LT;
        } else if ("<=".equals(text)) {
            return LTE;
        } else if ("!=".equals(text)) {
            return NOT;
        } else if ("contains".equals(text)) {
            return CONTAINS;
        } else if ("not contains".equals(text)) {
            return NOT_CONTAINS;
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
        } else if ("and".equals(text)) {
            return AND;
        } else if ("or".equals(text)) {
            return OR;
        }
        throw new IllegalArgumentException("Operator not supported: " + text);
    }

    public String getOperatorText(SimpleLangaugeOperator operator) {
        if (operator == EQ) {
            return "==";
        } else if (operator == GT) {
            return ">";
        } else if (operator == GTE) {
            return ">=";
        } else if (operator == LT) {
            return "<";
        } else if (operator == LTE) {
            return "<=";
        } else if (operator == NOT) {
            return "!=";
        } else if (operator == CONTAINS) {
            return "contains";
        } else if (operator == NOT_CONTAINS) {
            return "not contains";
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
        } else if (operator == AND) {
            return "and";
        } else if (operator == OR) {
            return "or";
        }
        return "";
    }

    @Override
    public String toString() {
        return getOperatorText(this);
    }
}
