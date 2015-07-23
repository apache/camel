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
package org.apache.camel.component.netty;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class NettyCachedRequestTimeoutTest extends BaseNettyTest {

    @Test
    public void testRequestTimeoutKeyInProducerCache() throws Exception {        
        assertEquals(0, template.getCurrentCacheSize());
        String out = template.requestBody("netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=1000", "Hello Camel", String.class);
        assertEquals("Bye World", out);
        out = template.requestBody("netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=1000", "Hello Camel", String.class);
        assertEquals("Bye World", out);       
        assertEquals(1, template.getCurrentCacheSize());
        
        template.requestBody("netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=1001", "Hello Camel", String.class);
        assertEquals(2, template.getCurrentCacheSize());
        template.requestBody("netty:tcp://localhost:{{port}}?textline=true&sync=true&requestTimeout=1002", "Hello Camel", String.class);
        assertEquals(3, template.getCurrentCacheSize());        
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty:tcp://localhost:{{port}}?textline=true&sync=true")
                    .transform().constant("Bye World");

            }
        };
    }
}
