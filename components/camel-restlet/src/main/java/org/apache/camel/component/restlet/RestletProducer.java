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
package org.apache.camel.component.restlet;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.restlet.Client;
import org.restlet.Context;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel producer that acts as a client to Restlet server.
 *
 * @version 
 */
public class RestletProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(RestletProducer.class);
    private static final Pattern PATTERN = Pattern.compile("\\{([\\w\\.]*)\\}");
    private Client client;
    private boolean throwException;

    public RestletProducer(RestletEndpoint endpoint) throws Exception {
        super(endpoint);
        this.throwException = endpoint.isThrowExceptionOnFailure();
        client = new Client(endpoint.getProtocol());
        client.setContext(new Context());
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        client.start();
    }

    @Override
    public void doStop() throws Exception {
        client.stop();
        super.doStop();
    }

    public void process(Exchange exchange) throws Exception {
        RestletEndpoint endpoint = (RestletEndpoint) getEndpoint();

        String resourceUri = buildUri(endpoint, exchange);
        Request request = new Request(endpoint.getRestletMethod(), resourceUri);

        RestletBinding binding = endpoint.getRestletBinding();
        binding.populateRestletRequestFromExchange(request, exchange);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Client sends a request (method: " + request.getMethod() + ", uri: " + resourceUri + ")");
        }

        Response response = client.handle(request);
        if (throwException) {
            if (response != null) {
                Integer respCode = response.getStatus().getCode();
                if (respCode > 207) {
                    throw populateRestletProducerException(exchange, response, respCode);
                }
            }
        }
        binding.populateExchangeFromRestletResponse(exchange, response);
    }

    private static String buildUri(RestletEndpoint endpoint, Exchange exchange) throws CamelExchangeException {
        String uri = endpoint.getProtocol() + "://" + endpoint.getHost() + ":" + endpoint.getPort() + endpoint.getUriPattern();

        // substitute { } placeholders in uri and use mandatory headers
        if (LOG.isTraceEnabled()) {
            LOG.trace("Substituting { } placeholders in uri: " + uri);
        }
        Matcher matcher = PATTERN.matcher(uri);
        while (matcher.find()) {
            String key = matcher.group(1);
            String header = exchange.getIn().getHeader(key, String.class);
            // header should be mandatory
            if (header == null) {
                throw new CamelExchangeException("Header with key: " + key + " not found in Exchange", exchange);
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Replacing: " + matcher.group(0) + " with header value: " + header);
            }

            uri = matcher.replaceFirst(header);
            // we replaced uri so reset and go again
            matcher.reset(uri);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Using uri: " + uri);
        }

        return uri;
    }

    protected RestletOperationException populateRestletProducerException(Exchange exchange, Response response, int responseCode) {
        RestletOperationException exception;
        String uri = exchange.getFromEndpoint().getEndpointUri();
        String statusText = response.getStatus().getDescription();
        Map<String, String> headers = parseResponseHeaders(response, exchange);
        String copy = response.toString();
        if (responseCode >= 300 && responseCode < 400) {
            String redirectLocation;
            if (response.getStatus().isRedirection()) {
                redirectLocation = response.getLocationRef().getHostIdentifier();
                exception = new RestletOperationException(uri, responseCode, statusText, redirectLocation, headers, copy);
            } else {
                //no redirect location
                exception = new RestletOperationException(uri, responseCode, statusText, null, headers, copy);
            }
        } else {
            //internal server error(error code 500)
            exception = new RestletOperationException(uri, responseCode, statusText, null, headers, copy);
        }

        return exception;
    }

    protected Map<String, String> parseResponseHeaders(Object response, Exchange camelExchange) {

        Map<String, String> answer = new HashMap<String, String>();
        if (response instanceof Response) {

            for (Map.Entry<String, Object> entry : ((Response) response).getAttributes().entrySet()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Parse external header " + entry.getKey() + "=" + entry.getValue());
                }
                answer.put(entry.getKey(), entry.getValue().toString());
            }
        }

        return answer;
    }
}
