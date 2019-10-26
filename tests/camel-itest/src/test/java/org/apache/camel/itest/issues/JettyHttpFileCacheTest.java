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
package org.apache.camel.itest.issues;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

public class JettyHttpFileCacheTest extends CamelTestSupport {
    private static final String TEST_STRING = "This is a test string and it has enough" 
        + " aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ";
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        context.getStreamCachingStrategy().setSpoolThreshold(16);
        context.getStreamCachingStrategy().setSpoolDirectory("target/cachedir");
        deleteDirectory("target/cachedir");
        createDirectory("target/cachedir");
    }

    @Test
    public void testGetWithRelativePath() throws Exception {
        
        String response = template.requestBody("http://localhost:8201/clipboard/download/file", "   ", String.class);
        assertEquals("should get the right response", TEST_STRING, response);
        
        File file = new File("target/cachedir");
        String[] files = file.list();
        assertTrue("There should not have any temp file", files.length == 0);
        
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                

                from("jetty:http://localhost:8201/clipboard/download?chunked=true&matchOnUriPrefix=true")
                    .to("http://localhost:9101?bridgeEndpoint=true");
                
                from("jetty:http://localhost:9101?chunked=true&matchOnUriPrefix=true")
                    .process(new Processor() {

                        public void process(Exchange exchange) throws Exception {
                            exchange.getOut().setBody(TEST_STRING);
                        }
                        
                    });

               
            }
        };
    }

}
