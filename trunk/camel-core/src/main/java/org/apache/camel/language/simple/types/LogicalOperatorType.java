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
 * Types of logical operators supported
 */
public enum LogicalOperatorType {

    // TODO: and|or is @deprecated and to be removed in Camel 3.0

    AND, OR;

    public static LogicalOperatorType asOperator(String text) {
        if ("&&".equals(text) || "and".equals(text)) {
            return AND;
        } else if ("||".equals(text) || "or".equals(text)) {
            return OR;
        }
        throw new IllegalArgumentException("Operator not supported: " + text);
    }

    public String getOperatorText(LogicalOperatorType operator) {
        if (operator == AND) {
            return "&&";
        } else if (operator == OR) {
            return "||";
        }
        return "";
    }

    @Override
    public String toString() {
        return getOperatorText(this);
    }

}
