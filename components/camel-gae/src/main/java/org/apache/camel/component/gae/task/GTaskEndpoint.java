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

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskOptions;

import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.gae.bind.HttpBindingInvocationHandler;
import org.apache.camel.component.gae.bind.InboundBinding;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.component.gae.bind.OutboundBindingSupport;
import org.apache.camel.component.http.HttpBinding;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.component.servlet.ServletComponent;
import org.apache.camel.component.servlet.ServletEndpoint;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;

/**
 * Represents a <a href="http://camel.apache.org/gtask.html">Google App Engine Task Queueing endpoint</a>.
 */
public class GTaskEndpoint extends ServletEndpoint implements OutboundBindingSupport<GTaskEndpoint, TaskOptions, Void> {

    private OutboundBinding<GTaskEndpoint, TaskOptions, Void> outboundBinding;
    private InboundBinding<GTaskEndpoint, HttpServletRequest, HttpServletResponse> inboundBinding;

    private String workerRoot;
    
    private Queue queue;
    
    public GTaskEndpoint(String endpointUri, ServletComponent component,
            URI httpUri, HttpClientParams params,
            HttpConnectionManager httpConnectionManager,
            HttpClientConfigurer clientConfigurer) throws URISyntaxException {
        super(endpointUri, component, httpUri, params, httpConnectionManager, clientConfigurer);
    }

    public OutboundBinding<GTaskEndpoint, TaskOptions, Void> getOutboundBinding() {
        return outboundBinding;
    }

    public void setOutboundBinding(OutboundBinding<GTaskEndpoint, TaskOptions, Void> outboundBinding) {
        this.outboundBinding = outboundBinding;
    }
    
    public InboundBinding<GTaskEndpoint, HttpServletRequest, HttpServletResponse> getInboundBinding() {
        return inboundBinding;
    }

    public void setInboundBinding(
            InboundBinding<GTaskEndpoint, HttpServletRequest, HttpServletResponse> inboundBinding) {
        this.inboundBinding = inboundBinding;
    }

    /**
     * Proxies the {@link HttpBinding} returned by {@link super#getBinding()}
     * with a dynamic proxy. The proxy's invocation handler further delegates to
     * {@link InboundBinding#readRequest(org.apache.camel.Endpoint, Exchange, Object)}
     * .
     * 
     * @return proxied {@link HttpBinding}.
     */
    @Override
    public HttpBinding getBinding() {
        return (HttpBinding)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {HttpBinding.class}, 
                new HttpBindingInvocationHandler<GTaskEndpoint, HttpServletRequest, HttpServletResponse>(
                        this, super.getBinding(), getInboundBinding()));
    }

    /**
     * @see #setWorkerRoot(String)
     */
    public String getWorkerRoot() {
        return workerRoot;
    }

    /**
     * Sets the web hook path root. 
     *
     * @param workerRoot
     *            the assumed web hook path root. The default is
     *            <code>worker</code>. The servlet handling the callback from
     *            the task queueing service should have a <code>/worker/*</code>
     *            servlet mapping in this case. If another servlet mapping is
     *            used it must be set here accordingly.
     */
    public void setWorkerRoot(String workerRoot) {
        this.workerRoot = workerRoot;
    }

    public Queue getQueue() {
        return queue;
    }
    
    public void setQueue(Queue queue) {
        this.queue = queue;
    }
    
    public Producer createProducer() throws Exception {
        return new GTaskProducer(this);
    }

}
