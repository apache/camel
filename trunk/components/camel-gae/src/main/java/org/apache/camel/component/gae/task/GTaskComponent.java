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

import java.net.URI;
import java.util.Map;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;

import org.apache.camel.Endpoint;
import org.apache.camel.component.gae.bind.InboundBinding;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.servlet.ServletComponent;
import org.apache.camel.component.servlet.ServletEndpoint;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;

/**
 * The <a href="http://camel.apache.org/gtask.html">Google App Engine Task
 * Queueing Component</a> supports asynchronous message processing. Outbound
 * communication uses the task queueing service of the Google App Engine.
 * Inbound communication is realized in terms of the <a
 * href="http://camel.apache.org/servlet.html">Servlet Component</a> component
 * for installing a web hook.
 */
public class GTaskComponent extends ServletComponent {

    @Override
    @SuppressWarnings("unchecked")
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String workerRoot = getAndRemoveParameter(
                parameters, "workerRoot", String.class, "worker");
        OutboundBinding outboundBinding = resolveAndRemoveReferenceParameter(
                parameters, "outboundBindingRef", OutboundBinding.class, new GTaskBinding());
        InboundBinding inboundBinding = resolveAndRemoveReferenceParameter(
                parameters, "inboundBindingRef", InboundBinding.class, new GTaskBinding());
        GTaskEndpointInfo info = new GTaskEndpointInfo(uri, remaining);
        GTaskEndpoint endpoint = (GTaskEndpoint)super.createEndpoint(
            info.getCanonicalUri(),
            info.getCanonicalUriPath(),
            parameters);
        endpoint.setServletName(getServletName());
        endpoint.setWorkerRoot(workerRoot);
        endpoint.setOutboundBinding(outboundBinding);
        endpoint.setInboundBinding(inboundBinding);
        endpoint.setQueue(QueueFactory.getQueue(remaining));
        return endpoint;
    }

    @Override
    protected ServletEndpoint createServletEndpoint(String endpointUri,
            ServletComponent component, URI httpUri, HttpClientParams params,
            HttpConnectionManager httpConnectionManager,
            HttpClientConfigurer clientConfigurer) throws Exception {
        return new GTaskEndpoint(endpointUri, component, httpUri, params,
                httpConnectionManager, clientConfigurer);
    }

    @Override
    public void connect(HttpConsumer consumer) throws Exception {
        super.connect(consumer);
    }
}
