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
package org.apache.camel.component.properties;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.PropertiesLookupListener;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertiesComponentLookupListenerTest extends ContextTestSupport {

    private final MyListener myListener = new MyListener();

    @Test
    public void testListener() throws Exception {
        assertTrue(myListener.hasName("greeting"));
        assertEquals("Hello World", myListener.getValue("greeting"));
        assertEquals("InitialProperties", myListener.getSource("greeting"));

        assertTrue(myListener.hasName("cool.end"));
        assertEquals("mock:result", myListener.getValue("cool.end"));
        assertEquals("classpath:org/apache/camel/component/properties/myproperties.properties",
                myListener.getSource("cool.end"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setBody(constant("{{greeting}}"))
                        .to("{{cool.end}}");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        context.getPropertiesComponent().addInitialProperty("greeting", "Hello World");
        context.getPropertiesComponent().addPropertiesLookupListener(myListener);
        return context;
    }

    private static class MyListener implements PropertiesLookupListener {

        private Map<String, String[]> map = new HashMap<>();

        @Override
        public void onLookup(String name, String value, String defaultValue, String source) {
            map.put(name, new String[] { value, source });
        }

        public boolean hasName(String name) {
            return map.containsKey(name);
        }

        public String getValue(String name) {
            return map.get(name)[0];
        }

        public String getSource(String name) {
            return map.get(name)[1];
        }
    }

}
