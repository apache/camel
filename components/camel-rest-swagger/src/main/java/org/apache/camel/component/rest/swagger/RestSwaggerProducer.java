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
package org.apache.camel.component.rest.swagger;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.damnhandy.uri.template.UriTemplate;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.util.ServiceHelper;

/**
 * An awesome REST producer backed by Swagger specifications delegating to
 * {@link RestProducerFactory} to create the REST client producer.
 */
public final class RestSwaggerProducer extends DefaultProducer {

    private final UriTemplate baseTemplate;

    private final String method;

    private final UriTemplate pathTemplate;

    private final AtomicReference<Producer> producerRef = new AtomicReference<>();

    private final RestProducerFactory restProducerFactory;

    public RestSwaggerProducer(final RestSwaggerEndpoint endpoint, final RestProducerFactory restProducerFactory,
        final String restEndpoint, final String method, final String path) {
        super(endpoint);
        this.restProducerFactory = restProducerFactory;
        this.method = method;

        baseTemplate = UriTemplate.fromTemplate(restEndpoint);

        pathTemplate = UriTemplate.fromTemplate(path);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final Endpoint endpoint = getEndpoint();
        final CamelContext camelContext = endpoint.getCamelContext();

        final Message in = exchange.getIn();
        final Map<String, Object> headers = in.getHeaders();

        final String expandedUri = baseTemplate.expand(headers);
        final URI uri = URI.create(expandedUri);

        final String host = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null).toString();

        final String basePath = uri.getPath();
        final String path = pathTemplate.expand(headers);

        Producer answer = producerRef.get();
        if (answer == null) {
            answer = restProducerFactory.createProducer(camelContext, host, method, basePath, path, null, null, null,
                null);

            if (producerRef.compareAndSet(null, answer)) {
                ServiceHelper.startService(answer);
            }
        }

        answer.process(exchange);
    }

    @Override
    protected void doStop() throws Exception {
        final Producer producer = producerRef.get();
        if (producer != null) {
            ServiceHelper.stopService(producer);
        }
    }
}
