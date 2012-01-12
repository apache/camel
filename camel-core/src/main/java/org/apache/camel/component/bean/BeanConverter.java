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
package org.apache.camel.component.bean;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.FallbackConverter;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConverterRegistry;

/**
 * A set of converter methods for working with beans
 */
@Converter
public final class BeanConverter {

    private BeanConverter() {
        // Helper Class
    }

    @FallbackConverter
    public static Object convertTo(Class<?> type, Exchange exchange, Object value, TypeConverterRegistry registry) {
        // use a fallback type converter so we can convert the embedded body if the value is BeanInvocation
        if (BeanInvocation.class.isAssignableFrom(value.getClass())) {

            BeanInvocation bi = (BeanInvocation) value;
            if (bi.getArgs() == null || bi.getArgs().length != 1) {
                // not possible to convert at this time as we try to convert the data passed in at first argument
                return Void.TYPE;
            }

            Class<?> from = bi.getArgs()[0].getClass();
            Object body = bi.getArgs()[0];

            // maybe from is already the type we want
            if (type.isAssignableFrom(from)) {
                return body;
            }

            // no then try to lookup a type converter
            TypeConverter tc = registry.lookup(type, from);
            if (tc != null) {
                return tc.convertTo(type, exchange, body);
            }
        }

        return null;
    }

}
