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
package org.apache.camel.util.json;

/**
 * DeserializationException explains how and where the problem occurs in the
 * source JSON text during deserialization.
 *
 * @since 2.0.0
 */
public class DeserializationException extends Exception {
    /** The kinds of exceptions that can trigger a DeserializationException. */
    enum Problems {
        @SuppressWarnings("javadoc")
        DISALLOWED_TOKEN, @SuppressWarnings("javadoc")
        UNEXPECTED_CHARACTER, @SuppressWarnings("javadoc")
        UNEXPECTED_EXCEPTION, @SuppressWarnings("javadoc")
        UNEXPECTED_TOKEN;
    }

    private static final long serialVersionUID = -7880698968187728547L;
    private final int position;
    private final Problems problemType;
    private final Object unexpectedObject;

    /**
     * @param position where the exception occurred.
     * @param problemType how the exception occurred.
     * @param unexpectedObject what caused the exception.
     */
    public DeserializationException(final int position, final Problems problemType, final Object unexpectedObject) {
        this.position = position;
        this.problemType = problemType;
        this.unexpectedObject = unexpectedObject;
    }

    @Override
    public String getMessage() {
        final StringBuilder sb = new StringBuilder();
        switch (this.problemType) {
            case DISALLOWED_TOKEN:
                sb.append("The disallowed token (").append(this.unexpectedObject).append(") was found at position ").append(this.position)
                        .append(". If this is in error, try again with a parse that allows the token instead. Otherwise, fix the parsable string and try again.");
                break;
            case UNEXPECTED_CHARACTER:
                sb.append("The unexpected character (").append(this.unexpectedObject).append(") was found at position ").append(this.position)
                        .append(". Fix the parsable string and try again.");
                break;
            case UNEXPECTED_TOKEN:
                sb.append("The unexpected token ").append(this.unexpectedObject).append(" was found at position ").append(this.position)
                        .append(". Fix the parsable string and try again.");
                break;
            case UNEXPECTED_EXCEPTION:
                sb.append("Please report this to the library's maintainer. The unexpected exception that should be addressed before trying again occurred at position ")
                        .append(this.position).append(": ").append(this.unexpectedObject);
                break;
            default:
                sb.append("Please report this to the library's maintainer. An error at position ").append(this.position)
                        .append(" occurred. There are no recovery recommendations available.");
                break;
        }
        return sb.toString();
    }

    /** @return an index of the string character the error type occurred at. */
    public int getPosition() {
        return this.position;
    }

    /** @return the enumeration for how the exception occurred. */
    public Problems getProblemType() {
        return this.problemType;
    }

    /** @return a representation of what caused the exception. */
    public Object getUnexpectedObject() {
        return this.unexpectedObject;
    }
}
