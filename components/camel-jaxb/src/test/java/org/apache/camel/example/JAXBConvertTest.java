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

import javax.xml.bind.UnmarshalException;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version $Revision$
 */
public class JAXBConvertTest extends TestCase {
    protected CamelContext context = new DefaultCamelContext();
    protected TypeConverter converter = context.getTypeConverter();

    public void testConverter() throws Exception {
        PurchaseOrder purchaseOrder = converter.convertTo(PurchaseOrder.class, 
            "<purchaseOrder name='foo' amount='123.45' price='2.22'/>");

        assertNotNull("Purchase order should not be null!", purchaseOrder);
        assertEquals("name", "foo", purchaseOrder.getName());
        assertEquals("amount", 123.45, purchaseOrder.getAmount());
    }

    public void testStreamShouldBeClosed() throws Exception {
        String data = "<purchaseOrder name='foo' amount='123.45' price='2.22'/>";
        InputStream is = new ByteArrayInputStream(data.getBytes());

        PurchaseOrder purchaseOrder = converter.convertTo(PurchaseOrder.class, is);
        assertNotNull(purchaseOrder);
        assertEquals(-1, is.read());
    }

    public void testStreamShouldBeClosedEvenForException() throws Exception {
        String data = "<errorOrder name='foo' amount='123.45' price='2.22'/>";
        InputStream is = new ByteArrayInputStream(data.getBytes());

        try {
            converter.convertTo(PurchaseOrder.class, is);
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof UnmarshalException);
        }
        assertEquals(-1, is.read());
    }

}
