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
import org.apache.camel.component.servlet.ServletComponent;
import org.apache.camel.component.servlet.ServletEndpoint;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Represents a <a href="http://camel.apache.org/gtask.html">Google App Engine Task Queueing endpoint</a>.
 */
@UriEndpoint(scheme = "gtask", extendsScheme = "servlet", title = "Google Task",
        syntax = "gtask:queueName", producerOnly = true, label = "cloud")
public class GTaskEndpoint extends ServletEndpoint implements OutboundBindingSupport<GTaskEndpoint, TaskOptions, Void> {

    private OutboundBinding<GTaskEndpoint, TaskOptions, Void> outboundBinding;
    private InboundBinding<GTaskEndpoint, HttpServletRequest, HttpServletResponse> inboundBinding;
    private Queue queue;

    @UriPath @Metadata(required = "true")
    private String queueName;
    @UriParam(label = "producer", defaultValue = "worker")
    private String workerRoot;
    @UriParam(label = "consumer")
    private String inboundBindingRef;
    @UriParam(label = "producer")
    private String outboundBindingRef;

    public GTaskEndpoint(String endpointUri, ServletComponent component, URI httpUri) throws URISyntaxException {
        super(endpointUri, component, httpUri);
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
     * {@link InboundBinding#readRequest(org.apache.camel.Endpoint, Exchange, Object)} .
     * 
     * @return proxied {@link HttpBinding}.
     */
    @Override
    public HttpBinding getBinding() {
        return (HttpBinding)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {HttpBinding.class}, 
                new HttpBindingInvocationHandler<GTaskEndpoint, HttpServletRequest, HttpServletResponse>(
                        this, super.getBinding(), getInboundBinding()));
    }

    public String getWorkerRoot() {
        return workerRoot;
    }

    /**
     * The servlet mapping for callback handlers. By default, this component requires a callback servlet mapping of /worker/*.
     * If another servlet mapping is used e.g. /myworker/* it must be set as option on the producer side: to("gtask:myqueue?workerRoot=myworker").
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

    public String getQueueName() {
        return queueName;
    }

    /**
     * Name of queue
     */
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getInboundBindingRef() {
        return inboundBindingRef;
    }

    /**
     * Reference to an InboundBinding<GTaskEndpoint, HttpServletRequest, HttpServletResponse> in the Registry for
     * customizing the binding of an Exchange to the Servlet API.
     * The referenced binding is used as post-processor to org.apache.camel.component.http.HttpBinding.
     */
    public void setInboundBindingRef(String inboundBindingRef) {
        this.inboundBindingRef = inboundBindingRef;
    }

    public String getOutboundBindingRef() {
        return outboundBindingRef;
    }

    /**
     * Reference to an OutboundBinding<GTaskEndpoint, TaskOptions, void> in the Registry for customizing the binding of an Exchange to the task queueing service.
     */
    public void setOutboundBindingRef(String outboundBindingRef) {
        this.outboundBindingRef = outboundBindingRef;
    }

    public Producer createProducer() throws Exception {
        return new GTaskProducer(this);
    }

}
