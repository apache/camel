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
 * Types of unary operators supported.
 * <p/>
 * An unary operator can only work on the left hand side, which means the operator
 * should be defined directly <i>after</i> the function it should work upon.
 */
public enum UnaryOperatorType {

    INC, DEC;

    public static UnaryOperatorType asOperator(String text) {
        if ("++".equals(text)) {
            return INC;
        } else if ("--".equals(text)) {
            return DEC;
        }
        throw new IllegalArgumentException("Operator not supported: " + text);
    }

    public String getOperatorText(UnaryOperatorType operator) {
        if (operator == INC) {
            return "++";
        } else if (operator == DEC) {
            return "--";
        }
        return "";
    }

    @Override
    public String toString() {
        return getOperatorText(this);
    }

}
