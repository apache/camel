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

import java.io.InputStream;

import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalURLFetchServiceTestConfig;
import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.HttpUnitOptions;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletRunner;
import com.meterware.servletunit.ServletUnitClient;
import org.apache.camel.component.gae.support.ServletTestSupport;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import static org.apache.camel.component.gae.TestConfig.getBaseUri;
import static org.junit.Assert.assertEquals;

public class GHttpCombinedRouteBuilderTest extends ServletTestSupport {

    private static Server testServer = GHttpTestUtils.createTestServer();

    private final LocalURLFetchServiceTestConfig config = new LocalURLFetchServiceTestConfig();
    private final LocalServiceTestHelper helper = new LocalServiceTestHelper(config);

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // Start servlet container for running the CamelHttpTransportServlet
        String webxml = "org/apache/camel/component/gae/http/web-combined.xml";
        InputStream is = new ClassPathResource(webxml).getInputStream();
        servletRunner = new ServletRunner(is, CTX_PATH);
        HttpUnitOptions.setExceptionsThrownOnErrorStatus(true);
        is.close();

        // Start servlet container for running the GHttpTestServlet
        testServer.start();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception  {
        testServer.stop();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        helper.setUp();
    }

    @After
    public void tearDown() throws Exception {
        helper.tearDown();
        super.tearDown();
    }

    @Test
    public void testGet() throws Exception {
        WebRequest req = new GetMethodWebRequest(createUrl("/test1?test=input1"));
        ServletUnitClient client = newClient();
        WebResponse response = client.getResponse(req);
        assertEquals(200, response.getResponseCode());
        assertEquals("input1", response.getHeaderField("test"));
    }
    
    @Test
    public void testPost() throws Exception {
        WebRequest req = new PostMethodWebRequest(createUrl("/test1"), createInput("input2"), getContentType()); 
        ServletUnitClient client = newClient();
        WebResponse response = client.getResponse(req);
        assertEquals("input2", response.getText());
    }
    
    @Test
    public void testCustomUrl() throws Exception {
        WebRequest req = new GetMethodWebRequest(createUrl("/test2"));
        ServletUnitClient client = newClient();
        WebResponse response = client.getResponse(req);
        assertEquals(getBaseUri() + "/blah", response.getHeaderField("testUrl"));
    }
    
}
