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
package org.apache.camel.converter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverters;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomEnumTypeConverterTest extends ContextTestSupport {

    @Test
    public void testCustomEnumTypeConverterGetsCalled() {
        CustomEnumTypeConverter customEnumTypeConverter = new CustomEnumTypeConverter();

        context.getTypeConverterRegistry().addTypeConverters(customEnumTypeConverter);

        Exchange exchange = new DefaultExchange(context);

        StatusCodeEnum result = context.getTypeConverter().convertTo(StatusCodeEnum.class, exchange, 200);
        assertEquals(StatusCodeEnum.OK, result);

        result = context.getTypeConverter().convertTo(StatusCodeEnum.class, exchange, 404);
        assertEquals(StatusCodeEnum.NOT_FOUND, result);
    }

    public static class CustomEnumTypeConverter implements TypeConverters {
        @Converter
        public StatusCodeEnum toStatusCodeEnum(int i) {
            return StatusCodeEnum.fromCode(i);
        }
    }

    public enum StatusCodeEnum {
        OK(200),
        NOT_FOUND(404);

        private final int statusCode;

        StatusCodeEnum(int statusCode) {
            this.statusCode = statusCode;
        }

        static StatusCodeEnum fromCode(int statusCode) {
            switch (statusCode) {
                case 200:
                    return OK;
                case 404:
                    return NOT_FOUND;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }
}
