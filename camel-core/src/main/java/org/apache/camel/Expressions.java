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
package org.apache.camel;

/**
 * @version $Revision: $
 */
public class Expressions {

    /**
     * Returns an expression for the header value with the given name
     *
     * @param headerName the name of the header the expression will return
     * @return an expression object which will return the header value
     */
    public static <E extends Exchange> Expression<E> headerExpression(final String headerName) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                Object header = exchange.getIn().getHeaders().getHeader(headerName);
                if (header == null) {
                    // lets try the exchange header
                    header = exchange.getHeaders().getHeader(headerName);
                }
                return header;
            }

            @Override
            public String toString() {
                return "header(" + headerName + ")";
            }
        };
    }
    /**
     * Returns an expression for the contant value
     *
     * @param value the value the expression will return
     * @return an expression object which will return the constant value
     */
    public static <E extends Exchange> Expression<E> constantExpression(final Object value) {
        return new Expression<E>() {
            public Object evaluate(E exchange) {
                return value;
            }

            @Override
            public String toString() {
                return "" + value;
            }
        };
    }
}
