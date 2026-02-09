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
 * Types of init operators supported
 */
public enum InitOperatorType {

    ASSIGNMENT,
    CHAIN_ASSIGNMENT;

    public static InitOperatorType asOperator(String text) {
        if (":=".equals(text)) {
            return ASSIGNMENT;
        } else if ("~:=".equals(text)) {
            return CHAIN_ASSIGNMENT;
        }
        throw new IllegalArgumentException("Operator not supported: " + text);
    }

    public static String getOperatorText(InitOperatorType operator) {
        if (operator == ASSIGNMENT) {
            return ":=";
        } else if (operator == CHAIN_ASSIGNMENT) {
            return "~:=";
        }
        return "";
    }

    @Override
    public String toString() {
        return getOperatorText(this);
    }

}
