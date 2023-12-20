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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.BulkTypeConverters;
import org.apache.camel.spi.TypeConvertible;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CustomBulkTypeConvertersTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        final CustomBulkTypeConverters customBulkTypeConverters = new CustomBulkTypeConverters();
        context.getTypeConverterRegistry().addBulkTypeConverters(customBulkTypeConverters);
        context.getTypeConverterRegistry().addConverter(new TypeConvertible<>(String.class, MyOrder.class),
                customBulkTypeConverters);
        context.getTypeConverterRegistry().addConverter(new TypeConvertible<>(Integer.class, MyOrder.class),
                customBulkTypeConverters);
        return context;
    }

    @Test
    public void testCoreTypeConverter() throws Exception {
        MyOrder order = context.getTypeConverter().convertTo(MyOrder.class, "123");
        assertEquals(123, order.getId());

        order = context.getTypeConverter().convertTo(MyOrder.class, 44);
        assertEquals(44, order.getId());

        order = context.getTypeConverter().convertTo(MyOrder.class, "Hello".getBytes());
        assertNull(order);
    }

    private static class MyOrder {
        private int id;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    private static class CustomBulkTypeConverters implements BulkTypeConverters {

        @Override
        public TypeConverter lookup(Class<?> toType, Class<?> fromType) {
            return null;
        }

        @Override
        public <T> T convertTo(Class<?> from, Class<T> to, Exchange exchange, Object value) throws TypeConversionException {
            if (from == String.class && to == MyOrder.class) {
                MyOrder order = new MyOrder();
                order.setId(Integer.parseInt(value.toString()));
                return (T) order;
            } else if (from == Integer.class && to == MyOrder.class) {
                MyOrder order = new MyOrder();
                order.setId((Integer) value);
                return (T) order;
            }
            return null;
        }

        @Override
        public int size() {
            return 2;
        }
    }

}
