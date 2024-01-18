/*
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
package org.apache.camel.component.dynamicrouter.control;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.annotations.SendDynamic;
import org.apache.camel.support.component.SendDynamicAwareSupport;
import org.apache.camel.util.URISupport;

import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.COMPONENT_SCHEME_CONTROL;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_PROPERTY;

/**
 * A {@link SendDynamicAwareSupport} implementation to process control channel messages for the Dynamic Router.
 */
@SendDynamic(COMPONENT_SCHEME_CONTROL)
public class DynamicRouterControlChannelSendDynamicAware extends SendDynamicAwareSupport {

    /**
     * This function returns a Processor that sets the headers of an exchange message. The headers are set based on the
     * properties of the {@link DynamicAwareEntry} provided. The set of headers includes those that match with the URI
     * parameters specified by the {@link DynamicRouterControlConstants#URI_PARAMS_TO_HEADER_NAMES} map.
     */
    static Function<DynamicAwareEntry, Processor> queryParamsHeadersProcessor = entry -> ex -> {
        Map<String, Object> entryProperties = entry.getProperties();
        Message message = ex.getMessage();
        DynamicRouterControlConstants.URI_PARAMS_TO_HEADER_NAMES.forEach((paramName, headerName) -> {
            if (entryProperties.containsKey(paramName)) {
                message.setHeader(headerName, entryProperties.get(paramName));
            }
        });
    };

    /**
     * The dynamic router control channel does not have any lenient properties.
     *
     * @return false (constant)
     */
    @Override
    public boolean isLenientProperties() {
        return false;
    }

    /**
     * Prepares for using optimized dynamic to by parsing the uri and returning an entry of details that are used for
     * creating the pre- and post-processors, and the static uri.
     *
     * @param  exchange    the exchange
     * @param  uri         the resolved uri which is intended to be used
     * @param  originalUri the original uri of the endpoint before any dynamic evaluation
     * @return             prepared information about the dynamic endpoint to use
     * @throws Exception   is thrown if error parsing the uri
     */
    @Override
    public DynamicAwareEntry prepare(Exchange exchange, String uri, String originalUri) throws Exception {
        Map<String, Object> properties = endpointProperties(exchange, uri);
        URI normalizedUri = URISupport.normalizeUriAsURI(uri);
        String controlAction = URISupport.extractRemainderPath(normalizedUri, false);
        properties.put(CONTROL_ACTION_PROPERTY, controlAction);
        return new DynamicAwareEntry(uri, originalUri, properties, null);
    }

    /**
     * Resolves the static part of the uri that are used for creating a single {@link org.apache.camel.Endpoint} and
     * {@link Producer} that will be reused for processing the optimised toD.
     *
     * @param  exchange the exchange
     * @param  entry    prepared information about the dynamic endpoint to use
     * @return          the static uri, or <tt>null</tt> to not let toD use this optimization.
     */
    @Override
    public String resolveStaticUri(Exchange exchange, DynamicAwareEntry entry) {
        String optimizedUri = null;
        String uri = entry.getUri();
        if (DynamicRouterControlConstants.SHOULD_OPTIMIZE.test(uri)) {
            optimizedUri = URISupport.stripQuery(uri);
        }
        return optimizedUri;
    }

    /**
     * Creates the pre {@link Processor} that will prepare the {@link Exchange} with dynamic details from the given
     * recipient.
     *
     * @param  exchange the exchange
     * @param  entry    prepared information about the dynamic endpoint to use
     * @return          the processor, or <tt>null</tt> to not let toD use this optimization.
     */
    @Override
    public Processor createPreProcessor(Exchange exchange, DynamicAwareEntry entry) {
        Processor preProcessor = null;
        if (DynamicRouterControlConstants.SHOULD_OPTIMIZE.test(entry.getUri())) {
            preProcessor = queryParamsHeadersProcessor.apply(entry);
        }
        return preProcessor;
    }

    /**
     * Creates an optional post {@link Processor} that will be executed afterward when the message has been sent
     * dynamic.
     *
     * @param  exchange the exchange
     * @param  entry    prepared information about the dynamic endpoint to use
     * @return          the post processor, or <tt>null</tt> if no post processor is needed.
     */
    @Override
    public Processor createPostProcessor(Exchange exchange, DynamicAwareEntry entry) {
        Processor postProcessor = null;
        if (DynamicRouterControlConstants.SHOULD_OPTIMIZE.test(entry.getUri())) {
            postProcessor = ex -> {
                Message message = exchange.getMessage();
                DynamicRouterControlConstants.URI_PARAMS_TO_HEADER_NAMES.values().forEach(message::removeHeader);
            };
        }
        return postProcessor;
    }
}
