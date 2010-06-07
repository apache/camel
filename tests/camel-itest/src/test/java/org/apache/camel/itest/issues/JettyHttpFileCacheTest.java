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
package org.apache.camel.itest.issues;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Before;
import org.junit.Test;

public class JettyHttpFileCacheTest extends CamelTestSupport {
    private static final String TEST_STRING = "This is a test string and it has enough" 
        + " aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ";
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        
        context.getProperties().put(CachedOutputStream.TEMP_DIR, "./target/cachedir");
        context.getProperties().put(CachedOutputStream.THRESHOLD, "16");
        deleteDirectory("./target/cachedir");
        createDirectory("./target/cachedir");
    }

    @Test
    public void testGetWithRelativePath() throws Exception {
        // use HttpClient for testing so we wont use Camel to send the request as well, which
        // may trigger using stream caching as well and store stream to file

        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet("http://localhost:8201/clipboard/download/file");

        HttpResponse response = client.execute(get);
        assertNotNull(response);
        assertEquals(200, response.getStatusLine().getStatusCode());

        String reply = context.getTypeConverter().convertTo(String.class, response.getEntity().getContent());
        assertEquals("should get the right response", TEST_STRING, reply);

        File file = new File("./target/cachedir");
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
