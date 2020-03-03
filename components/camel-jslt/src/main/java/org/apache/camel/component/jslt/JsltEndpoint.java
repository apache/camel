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
package org.apache.camel.component.jslt;

import java.io.InputStream;
import java.util.Collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schibsted.spt.data.jslt.Expression;
import com.schibsted.spt.data.jslt.Function;
import com.schibsted.spt.data.jslt.Parser;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

/**
 * The jslt component allows you to process a JSON messages using an JSLT transformations.
 *
 *  @author JiriOndrusek
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "jslt", title = "JSLT", syntax = "jslt:resourceUri", producerOnly = true, label = "transformation")
public class JsltEndpoint extends ResourceEndpoint {

    private Expression transform;

    @UriParam(defaultValue = "false", label = "common")
    private boolean prettyPrint;


    public JsltEndpoint() {
    }

    public JsltEndpoint(String uri, JsltComponent component, String resourceUri) {
        super(uri, component, resourceUri);
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "jslt:" + getResourceUri();
    }

    private synchronized Expression getTransform(Message msg) throws Exception {
        if (transform == null) {
            if (log.isDebugEnabled()) {
                String path = getResourceUri();
                log.debug("Jslt content read from resource {} with resourceUri: {} for endpoint {}", getResourceUri(), path, getEndpointUri());
            }

            String jsltStringFromHeader = msg.getHeader(JsltConstants.HEADER_JSLT_STRING, String.class);
            Collection<Function> functions = ((JsltComponent)getComponent()).getFunctions();

            if (jsltStringFromHeader != null) {
                if (functions == null) {
                    this.transform = Parser.compileString(jsltStringFromHeader);
                } else {
                    this.transform = Parser.compileString(jsltStringFromHeader, functions);
                }
            } else {
                if (functions == null) {
                    this.transform = Parser.compileResource(getResourceUri());
                } else {
                    this.transform = Parser.compileResource(getResourceUri(), functions);
                }
            }
        }
        return transform;
    }


    public JsltEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(getResourceUri(), newResourceUri);
        log.debug("Getting endpoint with URI: {}", newUri);
        return getCamelContext().getEndpoint(newUri, JsltEndpoint.class);
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");

        String newResourceUri = exchange.getIn().getHeader(JsltConstants.HEADER_JSLT_RESOURCE_URI, String.class);
        if (newResourceUri != null) {
            exchange.getIn().removeHeader(JsltConstants.HEADER_JSLT_RESOURCE_URI);

            log.debug("{} set to {} creating new endpoint to handle exchange", JsltConstants.HEADER_JSLT_RESOURCE_URI, newResourceUri);
            JsltEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri);
            newEndpoint.onExchange(exchange);
            return;
        }

        JsonNode input;

        ObjectMapper objectMapper = new ObjectMapper();
        if (exchange.getIn().getBody() instanceof String) {
            input = objectMapper.readTree(exchange.getIn().getBody(String.class));
        } else if (exchange.getIn().getBody() instanceof InputStream) {
            input = objectMapper.readTree(exchange.getIn().getBody(InputStream.class));
        } else {
            log.debug("Body content is not String neither InputStream.");
            throw new ValidationException(exchange, "Allowed body types are String or InputStream.");
        }

        JsonNode output = getTransform(exchange.getMessage()).apply(input);

        Message out = exchange.getMessage();

        out.setBody(isPrettyPrint() ? output.toPrettyString() : output.toString());

        out.setHeaders(exchange.getIn().getHeaders());
    }

    /**
     * If true, JSON in output message is pretty printed.
     */
    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }
}
