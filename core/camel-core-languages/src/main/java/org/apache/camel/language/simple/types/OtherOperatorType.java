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
 * Types of other operators supported
 */
public enum OtherOperatorType {

    CHAIN,
    CHAIN_NULL_SAFE,
    ELVIS;

    public static OtherOperatorType asOperator(String text) {
        if ("~>".equals(text)) {
            return CHAIN;
        } else if ("?~>".equals(text)) {
            return CHAIN_NULL_SAFE;
        } else if ("?:".equals(text)) {
            return ELVIS;
        }
        throw new IllegalArgumentException("Operator not supported: " + text);
    }

    public static String getOperatorText(OtherOperatorType operator) {
        if (operator == CHAIN) {
            return "~>";
        } else if (operator == CHAIN_NULL_SAFE) {
            return "?~>";
        } else if (operator == ELVIS) {
            return "?:";
        }
        return "";
    }

    @Override
    public String toString() {
        return getOperatorText(this);
    }

}
