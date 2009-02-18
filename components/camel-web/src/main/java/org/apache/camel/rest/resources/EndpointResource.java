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
package org.apache.camel.rest.resources;

import com.sun.jersey.api.representation.Form;
import com.sun.jersey.api.view.ImplicitProduces;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.rest.model.EndpointLink;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @version $Revision$
 */
@ImplicitProduces(Constants.HTML_MIME_TYPES)
public class EndpointResource {
    private final CamelContext camelContext;
    private final Endpoint endpoint;
    private final ProducerTemplate template;

    @Context
    HttpHeaders headers;


    public EndpointResource(CamelContext camelContext, ProducerTemplate template, Endpoint endpoint) {
        this.camelContext = camelContext;
        this.template = template;
        this.endpoint = endpoint;
    }

    public String getHref() {
        return new EndpointLink(endpoint).getHref();
    }

    public String getUri() {
        return endpoint.getEndpointUri();
    }

    public ProducerTemplate getTemplate() {
        return template;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public CamelContext getCamelContext() {
        return camelContext;
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
    
    @POST
    @Consumes({MediaType.TEXT_PLAIN, MediaType.TEXT_HTML, MediaType.TEXT_XML, MediaType.APPLICATION_XML})
    public Response postMessage(final String body) throws URISyntaxException {
        sendMessage(body);
        return Response.ok().build();
    }

    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response processForm(Form formData) throws URISyntaxException {
        System.out.println("Received form! " + formData);
        String body = formData.getFirst("text", String.class);
        sendMessage(body);
        return Response.seeOther(new URI(getHref())).build();
    }

    protected void sendMessage(final String body) {
        System.out.println("Sending to " + endpoint + " body: " + body);

        template.send(endpoint, new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                in.setBody(body);

                // lets pass in all the HTTP headers
                if (headers != null) {
                    MultivaluedMap<String, String> requestHeaders = headers.getRequestHeaders();

                    System.out.println("Headers are: " + requestHeaders);
                    Set<Map.Entry<String, List<String>>> entries = requestHeaders.entrySet();
                    for (Map.Entry<String, List<String>> entry : entries) {
                        String key = entry.getKey();
                        List<String> values = entry.getValue();
                        int size = values.size();
                        if (size == 1) {
                            in.setHeader(key, values.get(0));
                        } else if (size > 0) {
                            in.setHeader(key, values);
                        }
                    }
                }
                else {
                    System.out.println("No request headers!");
                }
            }
        });
    }


}
