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
 *   <li>EQ : ==</li>
 *   <li>GT : ></li>
 *   <li>GTE : >=</li>
 *   <li>LT : <</li>
 *   <li>LTE : <=</li>
 *   <li>NOT : !=</li>
 * </ul>
 */
public enum SimpleLangaugeOperator {

    EQ, GT, GTE, LT, LTE, NOT;

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
        } 
        return "";
    }

    @Override
    public String toString() {
        return getOperatorText(this);
    }
}
