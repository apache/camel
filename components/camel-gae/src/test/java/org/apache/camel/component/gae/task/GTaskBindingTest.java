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

import java.util.List;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.dev.LocalTaskQueue;
import com.google.appengine.api.taskqueue.dev.QueueStateInfo.HeaderWrapper;
import com.google.appengine.api.taskqueue.dev.QueueStateInfo.TaskStateInfo;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.appengine.tools.development.testing.LocalTaskQueueTestConfig;
import org.apache.camel.Exchange;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.impl.DefaultExchange;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withDefaults;
import static org.apache.camel.component.gae.http.GHttpTestUtils.getCamelContext;
import static org.apache.camel.component.gae.task.GTaskTestUtils.createEndpoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class GTaskBindingTest {

    private static GTaskBinding binding;

    private DefaultExchange exchange;
    private GTaskEndpoint endpoint;
    
    private LocalTaskQueueTestConfig config = new LocalTaskQueueTestConfig();
    private LocalServiceTestHelper helper = new LocalServiceTestHelper(config);
    private Queue queue;


    @BeforeClass
    public static void setUpClass() {
        binding = new GTaskBinding();
    }
    
    @Before
    public void setUp() throws Exception {
        helper.setUp();
        queue = QueueFactory.getDefaultQueue();
        exchange = new DefaultExchange(getCamelContext());
        endpoint = createEndpoint("test");
    }

    @After
    public void tearDown() {
        helper.tearDown();
    }

    @Test
    public void testWriteRequestHeaders() throws Exception {
        exchange.getIn().setHeader("test", "abc");
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "x=y");
        TaskOptions options = withDefaults();
        binding.writeRequestHeaders(endpoint, exchange, options);
        queue.add(options);
        TaskStateInfo info = getTaskStateInfos().get(0);
        assertEquals("abc", getHeader(info, "test"));
        assertNull(getHeader(info, Exchange.HTTP_QUERY));
    }
    
    @Test
    public void testWriteRequestBody() {
        exchange.getIn().setBody("test");
        TaskOptions options = withDefaults();
        binding.writeRequestBody(endpoint, exchange, options);
        queue.add(options);
        TaskStateInfo info = getTaskStateInfos().get(0);
        assertEquals("test", info.getBody());
        assertNull("application/octet-stream", getHeader(info , Exchange.CONTENT_TYPE));
    }
    
    @Test
    public void testWriteRequestWithDefaultWorkerRoot() throws Exception {
        exchange.getIn().setBody("anything");
        TaskOptions options = binding.writeRequest(endpoint, exchange, null);
        queue.add(options);
        TaskStateInfo info = getTaskStateInfos().get(0);
        assertEquals("/worker/test", info.getUrl());
    }
    
    @Test
    public void testWriteRequestWithCustomWorkerRoot() throws Exception {
        GTaskEndpoint custom = createEndpoint("test?workerRoot=lazy");
        exchange.getIn().setBody("anything");
        TaskOptions options = binding.writeRequest(custom, exchange, null);
        queue.add(options);
        TaskStateInfo info = getTaskStateInfos().get(0);
        assertEquals("/lazy/test", info.getUrl());
    }

    @Test
    public void testReadRequest() {
        exchange.setFromEndpoint(endpoint);
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpMessage message = new HttpMessage(exchange, request, null);
        request.addHeader(GTaskBinding.GAE_QUEUE_NAME, "a");
        request.addHeader(GTaskBinding.GAE_TASK_NAME, "b");
        request.addHeader(GTaskBinding.GAE_RETRY_COUNT, "1");
        // test invocation of inbound binding via dynamic proxy 
        endpoint.getBinding().readRequest(request, message);
        assertEquals("a", message.getHeader(GTaskBinding.GTASK_QUEUE_NAME));
        assertEquals("b", message.getHeader(GTaskBinding.GTASK_TASK_NAME));
        assertEquals(1, message.getHeader(GTaskBinding.GTASK_RETRY_COUNT));
        assertFalse(message.getHeaders().containsKey(GTaskBinding.GAE_QUEUE_NAME));
        assertFalse(message.getHeaders().containsKey(GTaskBinding.GAE_TASK_NAME));
        assertFalse(message.getHeaders().containsKey(GTaskBinding.GAE_RETRY_COUNT));
    }
    
    private List<TaskStateInfo> getTaskStateInfos() {
        LocalTaskQueue queue = LocalTaskQueueTestConfig.getLocalTaskQueue();
        return queue.getQueueStateInfo().get("default").getTaskInfo();
    }

    private String getHeader(TaskStateInfo info, String name) {
        for (HeaderWrapper header : info.getHeaders()) {
            if (name.equals(header.getKey())) {
                return header.getValue();
            }
        }
        return null;
    }
    
}
