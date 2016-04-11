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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.component.jackson.TestPojo;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JacksonConversionsTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // enable jackson type converter by setting this property on CamelContext
        context.getProperties().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        return context;
    }

    @Test
    public void shouldConvertMapToPojo() {
        String name = "someName";
        Map<String, String> pojoAsMap = new HashMap<String, String>();
        pojoAsMap.put("name", name);

        TestPojo testPojo = (TestPojo) template.requestBody("direct:test", pojoAsMap);

        assertEquals(name, testPojo.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:test").convertBodyTo(TestPojo.class);
            }
        };
    }

}
