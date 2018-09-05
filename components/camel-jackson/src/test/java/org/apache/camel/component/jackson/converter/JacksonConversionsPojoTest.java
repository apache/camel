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
package org.apache.camel.component.jackson.converter;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JacksonConversionsPojoTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // enable jackson type converter by setting this property on CamelContext
        context.getProperties().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        context.getProperties().put(JacksonConstants.TYPE_CONVERTER_TO_POJO, "true");
        return context;
    }

    @Test
    public void shouldConvertPojoToString() {
        Order order = new Order();
        order.setAmount(1);
        order.setCustomerName("Acme");
        order.setPartName("Camel");

        String json = (String) template.requestBody("direct:test", order);
        assertEquals("{\"id\":0,\"partName\":\"Camel\",\"amount\":1,\"customerName\":\"Acme\"}", json);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:test").convertBodyTo(String.class);
            }
        };
    }

}
