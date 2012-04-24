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
package org.apache.camel.example;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.StreamCache;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

/**
 * @version 
 */
public class JAXBConvertTest extends Assert {
    protected CamelContext context = new DefaultCamelContext();
    protected TypeConverter converter = context.getTypeConverter();

    @Test
    public void testConverter() throws Exception {
        PurchaseOrder purchaseOrder = converter.convertTo(PurchaseOrder.class, 
            "<purchaseOrder name='foo' amount='123.45' price='2.22'/>");

        assertNotNull("Purchase order should not be null!", purchaseOrder);
        assertEquals("name", "foo", purchaseOrder.getName());
        assertEquals("amount", 123.45, purchaseOrder.getAmount(), 0);
        assertEquals("price", 2.22, purchaseOrder.getPrice(), 0);
    }

    @Test
    public void testConverterTwice() throws Exception {
        PurchaseOrder purchaseOrder = converter.convertTo(PurchaseOrder.class,
            "<purchaseOrder name='foo' amount='123.45' price='2.22'/>");

        assertNotNull("Purchase order should not be null!", purchaseOrder);
        assertEquals("name", "foo", purchaseOrder.getName());
        assertEquals("amount", 123.45, purchaseOrder.getAmount(), 0);
        assertEquals("price", 2.22, purchaseOrder.getPrice(), 0);

        PurchaseOrder purchaseOrder2 = converter.convertTo(PurchaseOrder.class,
            "<purchaseOrder name='bar' amount='5.12' price='3.33'/>");

        assertNotNull("Purchase order should not be null!", purchaseOrder2);
        assertEquals("name", "bar", purchaseOrder2.getName());
        assertEquals("amount", 5.12, purchaseOrder2.getAmount(), 0);
        assertEquals("amount", 3.33, purchaseOrder2.getPrice(), 0);
    }

    @Test
    public void testStreamShouldBeClosed() throws Exception {
        String data = "<purchaseOrder name='foo' amount='123.45' price='2.22'/>";
        InputStream is = new ByteArrayInputStream(data.getBytes());

        PurchaseOrder purchaseOrder = converter.convertTo(PurchaseOrder.class, is);
        assertNotNull(purchaseOrder);
        assertEquals(-1, is.read());
    }

    @Test
    public void testStreamShouldBeClosedEvenForException() throws Exception {
        String data = "<errorOrder name='foo' amount='123.45' price='2.22'/>";
        InputStream is = new ByteArrayInputStream(data.getBytes());

        try {
            converter.convertTo(PurchaseOrder.class, is);
            fail("Should have thrown exception");
        } catch (TypeConversionException e) {
            // expected
        }
        assertEquals(-1, is.read());
    }
    
    @Test
    public void testNoConversionForStreamCache() throws Exception {
        PurchaseOrder order = new PurchaseOrder();
        try {
            converter.mandatoryConvertTo(StreamCache.class, order);
            fail("We should not use the JAXB FallbackTypeConverter for stream caching");
        } catch (NoTypeConversionAvailableException e) {
            //this is OK
        }
    }
}
