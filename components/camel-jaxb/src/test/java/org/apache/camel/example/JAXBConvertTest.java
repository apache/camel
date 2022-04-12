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
package org.apache.camel.example;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.test.junit5.ExchangeTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JAXBConvertTest extends ExchangeTestSupport {

    @Test
    public void testConverter() {
        PurchaseOrder purchaseOrder = context.getTypeConverter().convertTo(PurchaseOrder.class, exchange,
                "<purchaseOrder name='foo' amount='123.45' price='2.22'/>");

        assertNotNull(purchaseOrder, "Purchase order should not be null!");
        assertEquals("foo", purchaseOrder.getName(), "name");
        assertEquals(123.45, purchaseOrder.getAmount(), 0, "amount");
        assertEquals(2.22, purchaseOrder.getPrice(), 0, "price");
    }

    @Test
    public void testConverterTwice() {
        PurchaseOrder purchaseOrder = context.getTypeConverter().convertTo(PurchaseOrder.class, exchange,
                "<purchaseOrder name='foo' amount='123.45' price='2.22'/>");

        assertNotNull(purchaseOrder, "Purchase order should not be null!");
        assertEquals("foo", purchaseOrder.getName(), "name");
        assertEquals(123.45, purchaseOrder.getAmount(), 0, "amount");
        assertEquals(2.22, purchaseOrder.getPrice(), 0, "price");

        PurchaseOrder purchaseOrder2 = context.getTypeConverter().convertTo(PurchaseOrder.class, exchange,
                "<purchaseOrder name='bar' amount='5.12' price='3.33'/>");

        assertNotNull(purchaseOrder2, "Purchase order should not be null!");
        assertEquals("bar", purchaseOrder2.getName(), "name");
        assertEquals(5.12, purchaseOrder2.getAmount(), 0, "amount");
        assertEquals(3.33, purchaseOrder2.getPrice(), 0, "amount");
    }

    @Test
    public void testStreamShouldBeClosed() throws Exception {
        String data = "<purchaseOrder name='foo' amount='123.45' price='2.22'/>";
        InputStream is = new ByteArrayInputStream(data.getBytes());

        PurchaseOrder purchaseOrder = context.getTypeConverter().convertTo(PurchaseOrder.class, exchange, is);
        assertNotNull(purchaseOrder);
        assertEquals(-1, is.read());
    }

    @Test
    public void testStreamShouldBeClosedEvenForException() throws Exception {
        String data = "<errorOrder name='foo' amount='123.45' price='2.22'/>";
        InputStream is = new ByteArrayInputStream(data.getBytes());

        TypeConverter converter = context.getTypeConverter();

        Exception ex = Assertions.assertThrows(TypeConversionException.class,
                () -> converter.convertTo(PurchaseOrder.class, exchange, is));
        assertEquals(-1, is.read());
    }

    @Test
    public void testNoConversionForStreamCache() {
        PurchaseOrder order = new PurchaseOrder();

        TypeConverter converter = context.getTypeConverter();
        Exception ex = Assertions.assertThrows(NoTypeConversionAvailableException.class,
                () -> converter.mandatoryConvertTo(StreamCache.class, exchange, order));
    }
}
