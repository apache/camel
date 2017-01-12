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

import org.apache.camel.ExpressionIllegalSyntaxException;

/**
 * Syntax error in the simple language expression.
 */
public class SimpleIllegalSyntaxException extends ExpressionIllegalSyntaxException {
    private static final long serialVersionUID = 1L;
    private final int index;
    private final String message;

    public SimpleIllegalSyntaxException(String expression, int index, String message) {
        super(expression);
        this.index = index;
        this.message = message;
    }

    public SimpleIllegalSyntaxException(String expression, int index, String message, Throwable cause) {
        super(expression, cause);
        this.index = index;
        this.message = message;
    }

    /**
     * Index where the parsing error occurred
     *
     * @return index of the parsing error in the input, returns <tt>-1</tt> if the cause of the problem
     * is not applicable to specific index in the input
     */
    public int getIndex() {
        return index;
    }

    /**
     * Gets a short error message.
     */
    public String getShortMessage() {
        if (message == null) {
            return "[null]";
        }
        return message;
    }

    @Override
    public String getMessage() {
        if (message == null) {
            return "[null]";
        }

        StringBuilder sb = new StringBuilder(message);
        if (index > -1) {
            sb.append(" at location ").append(index);
            // create a nice looking message with indicator where the problem is
            sb.append("\n").append(getExpression()).append("\n");
            for (int i = 0; i < index; i++) {
                sb.append(" ");
            }
            sb.append("*\n");
        }
        return sb.toString();
    }

}
