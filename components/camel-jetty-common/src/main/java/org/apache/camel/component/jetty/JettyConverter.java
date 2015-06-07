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
package org.apache.camel.component.jetty;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.spi.TypeConverterRegistry;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

/**
 * @version 
 */
@Converter
public final class JettyConverter {
    private JettyConverter() {
        //Helper class
    }

    @Converter
    public static String toString(Response response) {
        return response.toString();
    }

    @FallbackConverter
    @SuppressWarnings("unchecked")
    public static <T> T convertTo(Class<T> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        if (value != null) {
            // should not try to convert Request as its not possible
            if (Request.class.isAssignableFrom(value.getClass())) {
                return (T) Void.TYPE;
            }
        }

        return null;
    }

}
