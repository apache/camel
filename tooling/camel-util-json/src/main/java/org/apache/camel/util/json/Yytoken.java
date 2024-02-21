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
 * Represents structural entities in JSON.
 *
 * @since 2.0.0
 */
public class Yytoken {
    /** Represents the different kinds of tokens. */
    public enum Types {
        /** Tokens of this type will always have a value of ":" */
        COLON,
        /** Tokens of this type will always have a value of "," */
        COMMA,
        /**
         * Tokens of this type will always have a value that is a boolean, null, number, or string.
         */
        DATUM,
        /** Tokens of this type will always have a value of "" */
        END,
        /** Tokens of this type will always have a value of "{" */
        LEFT_BRACE,
        /** Tokens of this type will always have a value of "[" */
        LEFT_SQUARE,
        /** Tokens of this type will always have a value of "}" */
        RIGHT_BRACE,
        /** Tokens of this type will always have a value of "]" */
        RIGHT_SQUARE,
        /** Represent the value (not a parsing token but used during color print) */
        VALUE;
    }

    private final Types type;
    private final Object value;

    /**
     * @param type  represents the kind of token the instantiated token will be.
     * @param value represents the value the token is associated with, will be ignored unless type is equal to
     *              Types.DATUM.
     * @see         Types
     */
    Yytoken(final Types type, final Object value) {
        /*
         * Sanity check. Make sure the value is ignored for the proper value
         * unless it is a datum token.
         */
        switch (type) {
            case COLON:
                this.value = ":";
                break;
            case COMMA:
                this.value = ",";
                break;
            case END:
                this.value = "";
                break;
            case LEFT_BRACE:
                this.value = "{";
                break;
            case LEFT_SQUARE:
                this.value = "[";
                break;
            case RIGHT_BRACE:
                this.value = "}";
                break;
            case RIGHT_SQUARE:
                this.value = "]";
                break;
            default:
                this.value = value;
                break;
        }
        this.type = type;
    }

    /**
     * @return which of the Types the token is.
     * @see    Types
     */
    Types getType() {
        return this.type;
    }

    /**
     * @return what the token is.
     * @see    Types
     */
    Object getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return this.type + "(" + this.value + ")";
    }
}
