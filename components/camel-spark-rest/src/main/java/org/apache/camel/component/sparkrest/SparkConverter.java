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
package org.apache.camel.component.sparkrest;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.spi.TypeConverterRegistry;
import spark.Request;
import spark.Response;

@Converter
public final class SparkConverter {

    private SparkConverter() {
    }

    /**
     * A fallback converter that allows us to easily call Java beans and use the raw Spark {@link Request} as parameter types.
     */
    @FallbackConverter
    public static Object convertToRequest(Class<?> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        // if we want to covert to Request
        if (value != null && Request.class.isAssignableFrom(type)) {

            // okay we may need to cheat a bit when we want to grab the HttpRequest as its stored on the NettyHttpMessage
            // so if the message instance is a NettyHttpMessage and its body is the value, then we can grab the
            // HttpRequest from the NettyHttpMessage
            SparkMessage msg;
            if (exchange.hasOut()) {
                msg = exchange.getOut(SparkMessage.class);
            } else {
                msg = exchange.getIn(SparkMessage.class);
            }
            if (msg != null) {
                return msg.getRequest();
            }
        }

        return null;
    }

    /**
     * A fallback converter that allows us to easily call Java beans and use the raw Spark {@link Response} as parameter types.
     */
    @FallbackConverter
    public static Object convertToResponse(Class<?> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        // if we want to covert to Response
        if (value != null && Response.class.isAssignableFrom(type)) {

            // okay we may need to cheat a bit when we want to grab the HttpRequest as its stored on the NettyHttpMessage
            // so if the message instance is a NettyHttpMessage and its body is the value, then we can grab the
            // HttpRequest from the NettyHttpMessage
            SparkMessage msg;
            if (exchange.hasOut()) {
                msg = exchange.getOut(SparkMessage.class);
            } else {
                msg = exchange.getIn(SparkMessage.class);
            }
            if (msg != null) {
                return msg.getResponse();
            }
        }

        return null;
    }

}
