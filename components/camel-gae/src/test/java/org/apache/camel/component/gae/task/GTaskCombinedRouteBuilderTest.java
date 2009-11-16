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
package org.apache.camel.component.gae.task;

import java.io.InputStream;

import com.meterware.httpunit.HttpUnitOptions;
import com.meterware.servletunit.ServletRunner;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.gae.support.ServletTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/org/apache/camel/component/gae/task/context-combined.xml" })
public class GTaskCombinedRouteBuilderTest extends ServletTestSupport {

    @Autowired
    private CamelContext camelContext;
    
    @Autowired
    private ProducerTemplate producerTemplate; 
    
    @Autowired
    private MockQueue mockQueue;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        String webxml = "org/apache/camel/component/gae/task/web-combined.xml";
        InputStream is = new ClassPathResource(webxml).getInputStream();
        servletRunner = new ServletRunner(is, CTX_PATH);
        HttpUnitOptions.setExceptionsThrownOnErrorStatus(true);
        // Servlet needs to be initialized explicitly because 
        // route creation is not bound to servlet lifecycle.
        initServlet(); 
        is.close();
    }
    
    @Before
    public void setUp() {
        mockQueue.setServletUnitClient(newClient());
    }

    @After
    public void tearDown() {
        getMockEndpoint().reset();
    }
    
    @Test
    public void testDefault() throws Exception {
        getMockEndpoint().expectedBodiesReceived("test1");
        getMockEndpoint().expectedHeaderReceived("test", "test2");
        producerTemplate.sendBodyAndHeader("direct:input", "test1", "test", "test2");
        getMockEndpoint().assertIsSatisfied();
        Message received = getMockEndpoint().getExchanges().get(0).getIn();
        assertEquals("default", received.getHeader(GTaskBinding.GTASK_QUEUE_NAME));
        assertEquals(0, received.getHeader(GTaskBinding.GTASK_RETRY_COUNT));
    }
    
    private MockEndpoint getMockEndpoint() {
        return (MockEndpoint)camelContext.getEndpoint("mock:mock");
    }
    
}
