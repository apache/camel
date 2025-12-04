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

package org.apache.camel.component.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

public class BeanMapPutTest extends ContextTestSupport {

    private final Map<String, String> myMap = new HashMap<>();

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("myMap", myMap);
        return jndi;
    }

    @Test
    public void testMapPut() {
        assertEquals(0, myMap.size());

        template.sendBody("direct:start", "Hello World");

        assertEquals(1, myMap.size());
        assertEquals("true", myMap.get("isMaster"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").bean("myMap", "put('isMaster','true')");
            }
        };
    }
}
