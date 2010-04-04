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
package org.apache.camel.component.gae.http;

import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalURLFetchServiceTestConfig;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/org/apache/camel/component/gae/http/context-outbound.xml"})
public class GHttpOutboundRouteBuilderTest {

    private static Server testServer = GHttpTestUtils.createTestServer();

    private final LocalURLFetchServiceTestConfig config = new LocalURLFetchServiceTestConfig();
    private final LocalServiceTestHelper helper = new LocalServiceTestHelper(config);

    @Autowired
    private ProducerTemplate producerTemplate;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        testServer.start();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception  {
        testServer.stop();
    }

    @Before
    public void setUp() throws Exception {
        helper.setUp();
    }

    @After
    public void tearDown() throws Exception {
        helper.tearDown();
    }

    @Test
    public void testPost() {
        Exchange result = producerTemplate.request("direct:input1", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("testBody1");
                exchange.getIn().setHeader("test", "testHeader1");
            }
        });
        assertEquals("testBody1", result.getOut().getBody(String.class));
        assertEquals("testHeader1", result.getOut().getHeader("test"));
        assertEquals("a=b", result.getOut().getHeader("testQuery"));
        assertEquals("POST", result.getOut().getHeader("testMethod"));
        assertEquals(200, result.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }
    
    @Test
    public void testGet() {
        Exchange result = producerTemplate.request("direct:input1", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("test", "testHeader1");
            }
        });
        assertEquals("testHeader1", result.getOut().getHeader("test"));
        assertEquals("a=b", result.getOut().getHeader("testQuery"));
        assertEquals("GET", result.getOut().getHeader("testMethod"));
        assertEquals(200, result.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }
    
}
