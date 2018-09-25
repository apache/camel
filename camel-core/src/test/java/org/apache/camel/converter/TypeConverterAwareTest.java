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
package org.apache.camel.converter;

import org.apache.camel.ContextTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class TypeConverterAwareTest extends ContextTestSupport {

    @Override
    protected boolean isLoadTypeConverters() {
        return true;
    }

    @Test
    public void testPurchaseOrderConverter() throws Exception {
        byte[] data = "##START##AKC4433   179       3##END##".getBytes();
        PurchaseOrder order = context.getTypeConverter().convertTo(PurchaseOrder.class, data);
        assertNotNull(order);

        assertEquals("AKC4433", order.getName());
        assertEquals("179.00", order.getPrice().toString());
        assertEquals(3, order.getAmount());
    }

}
