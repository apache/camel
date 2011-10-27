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
package org.apache.camel.web.resources;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import com.sun.jersey.api.representation.Form;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.web.model.EndpointLink;

/**
 * A Camel <a href="http://camel.apache.org/endpoint.html">Endpoint</a>
 */
public class EndpointResource extends CamelChildResourceSupport {
    private final String key;
    private final Endpoint endpoint;

    public EndpointResource(CamelContextResource contextResource, String key, Endpoint endpoint) {
        super(contextResource);
        this.key = key;
        this.endpoint = endpoint;
    }

    public String getHref() {
        return new EndpointLink(key, endpoint).getHref();
    }

    public String getKey() {
        return key;
    }

    public String getUri() {
        return endpoint.getEndpointUri();
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public BrowsableEndpoint getBrowsableEndpoint() {
        if (endpoint instanceof BrowsableEndpoint) {
            return (BrowsableEndpoint) endpoint;
        }
        return null;
    }

    /**
     * Returns a single Camel <a href="http://camel.apache.org/exchange.html">message exchange</a> on this endpoint if the endpoint supports
     * <a href="http://camel.apache.org/browsableendpoint.html">being browsed</a>
     */
    @Path("messages/{id}")
    public ExchangeResource getExchange(@PathParam("id") String exchangeId) {
        if (endpoint instanceof BrowsableEndpoint) {
            BrowsableEndpoint browsableEndpoint = (BrowsableEndpoint) endpoint;
            Exchange exchange = ExchangeHelper.getExchangeById(browsableEndpoint.getExchanges(), exchangeId);
            if (exchange != null) {
                return new ExchangeResource(this, exchange);
            }
        }
        // should return 404
        return null;
    }

    /**
     * Posts a <a href="http://camel.apache.org/message.html">message</a> to this Camel endpoint with the payload
     * being text, XML or JSON
     */
    @POST
    @Consumes({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML, MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    public Response postMessage(@Context HttpHeaders headers, final String body) throws URISyntaxException {
        Map<String, Object> messageHeaders = multivalueMapToMap(headers.getRequestHeaders());
        sendMessage(messageHeaders, body);
        return Response.ok().build();
    }

    /**
     * Posts a <a href="http://camel.apache.org/message.html">message</a> to this Camel endpoint taking the
     * form data and extracting the <code>body</code> field as the body of the message.
     */
    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response postMessageForm(@Context HttpHeaders headers, Form formData) throws URISyntaxException {
        String body = getBodyData(formData);

        Map<String, Object> messageHeaders = multivalueMapToMap(headers.getRequestHeaders());
        messageHeaders.putAll(getHeadersData(formData));

        sendMessage(messageHeaders, body);
        return Response.seeOther(new URI(getHref())).build();
    }

    private Map<String, Object> getHeadersData(Form formData) {
        Map<String, Object> headers = new HashMap<String, Object>();
        Map<String, Object> formMap = multivalueMapToMap(formData);
        for (Map.Entry<String, Object> entry : formMap.entrySet()) {
            if (entry.getKey().startsWith("header_")) {
                headers.put((String) entry.getValue(), getHeaderValueForKey(entry.getKey(), formMap));
            }
        }
        return headers;
    }

    private Object getHeaderValueForKey(String key, Map<String, Object> headers) {
        return headers.get("value_" + key);
    }

    private String getBodyData(Form formData) {
        return formData.getFirst("body", String.class);
    }

    private Map<String, Object> multivalueMapToMap(MultivaluedMap<String, String> multivaluedMap) {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        if (multivaluedMap != null) {
            for (Map.Entry<String, List<String>> entry : multivaluedMap.entrySet()) {
                List<String> values = entry.getValue();
                int size = values.size();
                if (size == 1) {
                    resultMap.put(entry.getKey(), entry.getValue().get(0));
                } else if (size > 0) {
                    resultMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return resultMap;
    }

    protected void sendMessage(final Map<String, Object> headers, final String body) {
        getTemplate().send(endpoint, new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeaders(headers);
            }
        });
    }

}
