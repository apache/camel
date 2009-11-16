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

import com.google.appengine.api.labs.taskqueue.TaskOptionsAccessor;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMessage;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.apache.camel.component.gae.http.GHttpTestUtils.getCamelContext;
import static org.apache.camel.component.gae.task.GTaskTestUtils.createEndpoint;
import static org.apache.camel.component.gae.task.GTaskTestUtils.createTaskOptionsAccessor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GTaskBindingTest {

    private static GTaskBinding binding;

    private DefaultExchange exchange;

    private GTaskEndpoint endpoint;
    
    private TaskOptionsAccessor accessor;
    
    @BeforeClass
    public static void setUpClass() {
        binding = new GTaskBinding();
    }
    
    @Before
    public void setUp() throws Exception {
        exchange = new DefaultExchange(getCamelContext());
        accessor = createTaskOptionsAccessor();
        endpoint = createEndpoint("test");
    }

    @Test
    public void testWriteRequestHeaders() throws Exception {
        exchange.getIn().setHeader("test", "abc");
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "x=y");
        binding.writeRequestHeaders(endpoint, exchange, accessor.getTaskOptions());
        assertEquals(1, accessor.getHeaders().size());
        assertEquals("abc", accessor.getHeaders().get("test"));
    }
    
    @Test
    public void testWriteRequestBody() {
        exchange.getIn().setBody("test");
        binding.writeRequestBody(endpoint, exchange, accessor.getTaskOptions());
        assertEquals("test", exchange.getContext().getTypeConverter().convertTo(String.class, accessor.getPayload()));
    }
    
    @Test
    public void testWriteRequest() throws Exception {
        GTaskEndpoint custom = createEndpoint("test?workerRoot=lazy");
        exchange.getIn().setBody("anything");
        accessor = new TaskOptionsAccessor(binding.writeRequest(endpoint, exchange, null));
        assertEquals("/worker/test", accessor.getPath());
        accessor = new TaskOptionsAccessor(binding.writeRequest(custom, exchange, null));
        assertEquals("/lazy/test", accessor.getPath());
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
    
}
