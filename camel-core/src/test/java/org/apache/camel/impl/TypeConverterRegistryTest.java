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
package org.apache.camel.impl;

import java.io.File;
import java.io.InputStream;

import junit.framework.TestCase;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.support.TypeConverterSupport;

/**
 * @version 
 */
public class TypeConverterRegistryTest extends TestCase {

    public void testDefaultTypeConverterRegistry() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        assertNotNull(ctx.getTypeConverterRegistry());

        // file to input stream is a default converter in Camel
        TypeConverter tc = ctx.getTypeConverterRegistry().lookup(InputStream.class, File.class);
        assertNotNull(tc);
    }

    public void testAddTypeConverter() {
        DefaultCamelContext context = new DefaultCamelContext();

        // START SNIPPET: e1
        // add our own type converter manually that converts from String -> MyOrder using MyOrderTypeConverter
        context.getTypeConverterRegistry().addTypeConverter(MyOrder.class, String.class, new MyOrderTypeConverter());
        // END SNIPPET: e1

        // START SNIPPET: e3
        MyOrder order = context.getTypeConverter().convertTo(MyOrder.class, "123");
        // END SNIPPET: e3

        assertNotNull(order);
        assertEquals(123, order.getId());
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

    // START SNIPPET: e2
    private static class MyOrderTypeConverter extends TypeConverterSupport {

        @SuppressWarnings("unchecked")
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
            // converter from value to the MyOrder bean
            MyOrder order = new MyOrder();
            order.setId(Integer.parseInt(value.toString()));
            return (T) order;
        }
    }
    // END SNIPPET: e2

}
