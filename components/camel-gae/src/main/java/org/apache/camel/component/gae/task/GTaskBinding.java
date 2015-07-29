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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.TaskOptions;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.gae.bind.InboundBinding;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.spi.HeaderFilterStrategy;

/**
 * Binds the {@link TaskOptions} of the task queueing service to a Camel
 * {@link Exchange} for outbound communication. For inbound communication a
 * {@link org.apache.camel.http.common.HttpMessage} is bound to {@link Exchange}.
 */
public class GTaskBinding implements 
    OutboundBinding <GTaskEndpoint, TaskOptions, Void>,
    InboundBinding  <GTaskEndpoint, HttpServletRequest, HttpServletResponse> { 

    /**
     * Camel header name corresponding to <code>X-AppEngine-QueueName</code>
     * header created by task queueing service.
     */
    public static final String GTASK_QUEUE_NAME = "CamelGtaskQueueName";

    /**
     * Camel header name corresponding to <code>X-AppEngine-TaskName</code>
     * header created by task queueing service.
     */
    public static final String GTASK_TASK_NAME = "CamelGtaskTaskName";
    
    /**
     * Camel header name corresponding to <code>X-AppEngine-TaskRetryCount</code>
     * header created by task queueing service.
     */
    public static final String GTASK_RETRY_COUNT = "CamelGtaskRetryCount";

    static final String GAE_QUEUE_NAME = "X-AppEngine-QueueName";
    static final String GAE_TASK_NAME = "X-AppEngine-TaskName";
    static final String GAE_RETRY_COUNT = "X-AppEngine-TaskRetryCount";
    
    // ----------------------------------------------------------------
    //  Outbound binding
    // ----------------------------------------------------------------
    
    /**
     * Reads data from <code>exchange</code> and writes it to a newly created
     * {@link TaskOptions} instance. The <code>request</code> parameter is
     * ignored.
     *
     * @return a newly created {@link TaskOptions} instance containing data from
     *         <code>exchange</code>.
     */
    public TaskOptions writeRequest(GTaskEndpoint endpoint, Exchange exchange, TaskOptions request) {
        TaskOptions answer = TaskOptions.Builder.withUrl(getWorkerRoot(endpoint) + endpoint.getPath());
        writeRequestHeaders(endpoint, exchange, answer);
        writeRequestBody(endpoint, exchange, answer);
        // TODO: consider TaskOptions method (POST, GET, ...)
        return answer;
    }
    
    /**
     * @throws UnsupportedOperationException
     */
    public Exchange readResponse(GTaskEndpoint endpoint, Exchange exchange, Void response) {
        throw new UnsupportedOperationException("gtask responses not supported");
    }

    // ----------------------------------------------------------------
    //  Inbound binding
    // ----------------------------------------------------------------
    
    /**
     * Replaces the task service-specific headers (<code>X-AppEngine-*</code>)
     * with Camel-specific headers.
     * 
     * @see GTaskBinding#GTASK_QUEUE_NAME
     * @see GTaskBinding#GTASK_TASK_NAME
     * @see GTaskBinding#GTASK_RETRY_COUNT
     * @see org.apache.camel.http.common.DefaultHttpBinding#readRequest(HttpServletRequest, org.apache.camel.http.common.HttpMessage)
     */
    public Exchange readRequest(GTaskEndpoint endpoint, Exchange exchange, HttpServletRequest request) {
        readRequestHeaders(endpoint, exchange, request);
        return exchange;
    }

    public HttpServletResponse writeResponse(GTaskEndpoint endpoint, Exchange exchange, HttpServletResponse response) {
        return response;
    }

    // ----------------------------------------------------------------
    //  Customization points
    // ----------------------------------------------------------------
    
    protected void writeRequestHeaders(GTaskEndpoint endpoint, Exchange exchange, TaskOptions request) {
        HeaderFilterStrategy strategy = endpoint.getHeaderFilterStrategy();
        for (String headerName : exchange.getIn().getHeaders().keySet()) {
            String headerValue = exchange.getIn().getHeader(headerName, String.class);
            if (strategy != null && !strategy.applyFilterToCamelHeaders(headerName, headerValue, exchange)) {
                request.header(headerName, headerValue);
            }
        }
    }

    protected void readRequestHeaders(GTaskEndpoint endpoint, Exchange exchange, HttpServletRequest request) {
        Message message = exchange.getIn();
        String key = GAE_QUEUE_NAME;
        Object val = message.getHeader(key);
        if (val != null) {
            message.getHeaders().put(GTASK_QUEUE_NAME, val);
            message.getHeaders().remove(key);
        }
        key = GAE_TASK_NAME;
        val = message.getHeader(key);
        if (val != null) {
            message.getHeaders().put(GTASK_TASK_NAME, val);
            message.getHeaders().remove(key);
        }
        key = GAE_RETRY_COUNT;
        val = message.getHeader(key);
        if (val != null) {
            message.getHeaders().put(GTASK_RETRY_COUNT, Integer.parseInt(val.toString()));
            message.getHeaders().remove(key);
        }
        // EXPERIMENTAL // TODO: resolve gzip encoding issues
        exchange.getIn().removeHeader("Accept-Encoding");
        exchange.getIn().removeHeader("Content-Encoding");
    }
    
    protected void writeRequestBody(GTaskEndpoint endpoint, Exchange exchange, TaskOptions request) {
        // TODO: allow message header or endpoint uri to configure character encoding and content type
        request.payload(exchange.getIn().getBody(byte[].class), "application/octet-stream");
    }
    
    protected String getWorkerRoot(GTaskEndpoint endpoint) {
        return "/" + endpoint.getWorkerRoot();
    }

}
