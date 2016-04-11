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
package org.apache.camel.component.jolt;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test testing the Removr.
 */
public class JoltDefaultrTest extends CamelTestSupport {
    
    @Test
    public void testFirstSampleJolt() throws Exception {
        Exchange exchange = template.request("direct://start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> body = new HashMap<>();
                body.put("Hello", "World");
                exchange.getIn().setBody(body);
            }
        });
        
        assertEquals(3, exchange.getOut().getBody(Map.class).size());
        assertEquals("aa", exchange.getOut().getBody(Map.class).get("a"));
        assertEquals("bb", exchange.getOut().getBody(Map.class).get("b"));
        assertEquals("World", exchange.getOut().getBody(Map.class).get("Hello"));
        
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start")
                    .to("jolt:org/apache/camel/component/jolt/defaultr.json?transformDsl=Defaultr");
            }
        };
    }

}
