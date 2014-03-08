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

import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;
import org.apache.camel.EndpointInject;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.gae.support.ServletTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.apache.camel.component.gae.task.GTaskTestUtils.newLocalServiceTestHelper;
import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/org/apache/camel/component/gae/task/context-combined.xml" })
@Ignore
public class GTaskCombinedRouteBuilderTest extends ServletTestSupport {

    private static Server server = GTaskTestUtils.createTestServer();

    private final LocalTaskQueueTestConfig config = new LocalTaskQueueTestConfig();
    private final LocalServiceTestHelper helper = newLocalServiceTestHelper(config.setDisableAutoTaskExecution(false));

    @Autowired
    private ProducerTemplate producerTemplate;

    @EndpointInject(uri = "mock:mock")
    private MockEndpoint mock;

    @BeforeClass
    public static void setUpClass() throws Exception {
        server.start();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        server.stop();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        helper.setUp();
    }

    @After
    public void tearDown() throws Exception {
        mock.reset();
        helper.tearDown();
        super.tearDown();
    }

    @Test
    public void testDefault() throws Exception {
        mock.expectedBodiesReceived("test1");
        mock.expectedHeaderReceived("test", "test2");
        producerTemplate.sendBodyAndHeader("direct:input", "test1", "test", "test2");
        mock.assertIsSatisfied();
        Message received = mock.getExchanges().get(0).getIn();
        assertEquals("default", received.getHeader(GTaskBinding.GTASK_QUEUE_NAME));
        assertEquals(0, received.getHeader(GTaskBinding.GTASK_RETRY_COUNT));
    }
    
}
