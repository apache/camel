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
package org.apache.camel.component.jackson.converter;

import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonConversionsPojoTest extends CamelTestSupport {
    @Test
    public void shouldConvertPojoToString() {
        context.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        context.getGlobalOptions().put(JacksonConstants.TYPE_CONVERTER_TO_POJO, "true");

        Order order = new Order();
        order.setAmount(1);
        order.setCustomerName("Acme");
        order.setPartName("Camel");

        String json = (String) template.requestBody("direct:test", order);
        assertEquals("{\"id\":0,\"partName\":\"Camel\",\"amount\":1,\"customerName\":\"Acme\"}", json);
    }

    @Test
    public void shouldConvertJAXBPojoToString() {
        context.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        context.getGlobalOptions().put(JacksonConstants.TYPE_CONVERTER_TO_POJO, "true");
        context.getGlobalOptions().put(JacksonConstants.TYPE_CONVERTER_MODULE_CLASS_NAMES,
                JakartaXmlBindAnnotationModule.class.getName());

        Order order = new Order();
        order.setAmount(1);
        order.setCustomerName("Acme");
        order.setPartName("Camel");

        String json = (String) template.requestBody("direct:test", order);
        assertEquals("{\"id\":0,\"partName\":\"Camel\",\"amount\":1,\"customer_name\":\"Acme\"}", json);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:test").convertBodyTo(String.class);
            }
        };
    }

}
