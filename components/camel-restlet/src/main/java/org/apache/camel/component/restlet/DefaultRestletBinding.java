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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import javax.xml.transform.dom.DOMSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.converter.jaxp.StringSource;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default Restlet binding implementation
 *
 * @version 
 */
public class DefaultRestletBinding implements RestletBinding, HeaderFilterStrategyAware {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRestletBinding.class);
    private HeaderFilterStrategy headerFilterStrategy;

    public void populateExchangeFromRestletRequest(Request request, Exchange exchange) throws Exception {
        Message inMessage = exchange.getIn();

        // extract headers from restlet
        for (Map.Entry<String, Object> entry : request.getAttributes().entrySet()) {
            if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange)) {
                inMessage.setHeader(entry.getKey(), entry.getValue());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Populate exchange from Restlet request header: "
                            + entry.getKey() + " value: " + entry.getValue());
                }
            }
        }

        // copy query string to header
        String query = request.getResourceRef().getQuery();
        if (query != null) {
            inMessage.setHeader(Exchange.HTTP_QUERY, query);
        }

        // copy URI to header
        inMessage.setHeader(Exchange.HTTP_URI, request.getResourceRef().getIdentifier(true));

        // copy HTTP method to header
        inMessage.setHeader(Exchange.HTTP_METHOD, request.getMethod().toString());

        if (!request.isEntityAvailable()) {
            return;
        }

        // only deal with the form if the content type is "application/x-www-form-urlencoded"
        if (request.getEntity().getMediaType() != null && request.getEntity().getMediaType().equals(MediaType.APPLICATION_WWW_FORM)) {
            Form form = new Form(request.getEntity());
            for (Map.Entry<String, String> entry : form.getValuesMap().entrySet()) {
                if (entry.getValue() == null) {
                    inMessage.setBody(entry.getKey());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Populate exchange from Restlet request body: " + entry.getValue());
                    }
                } else {
                    if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange)) {
                        inMessage.setHeader(entry.getKey(), entry.getValue());
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Populate exchange from Restlet request user header: "
                                    + entry.getKey() + " value: " + entry.getValue());
                        }
                    }
                }
            }
        } else {
            inMessage.setBody(request.getEntity().getStream());
        }

    }

    public void populateRestletRequestFromExchange(Request request, Exchange exchange) {
        request.setReferrerRef("camel-restlet");
        String body = exchange.getIn().getBody(String.class);
        Form form = new Form();
        // add the body as the key in the form with null value
        form.add(body, null);

        MediaType mediaType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, MediaType.class);
        if (mediaType == null) {
            mediaType = MediaType.APPLICATION_WWW_FORM;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Populate Restlet request from exchange body: " + body + " using media type " + mediaType);
        }

        // login and password are filtered by header filter strategy
        String login = exchange.getIn().getHeader(RestletConstants.RESTLET_LOGIN, String.class);
        String password = exchange.getIn().getHeader(RestletConstants.RESTLET_PASSWORD, String.class);

        if (login != null && password != null) {
            ChallengeResponse authentication = new ChallengeResponse(ChallengeScheme.HTTP_BASIC, login, password);
            request.setChallengeResponse(authentication);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Basic HTTP Authentication has been applied");
            }
        }

        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            if (!headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), exchange)) {
                // Use forms only for GET and POST/x-www-form-urlencoded
                if (request.getMethod() == Method.GET || (request.getMethod() == Method.POST && mediaType == MediaType.APPLICATION_WWW_FORM)) {
                    if (entry.getKey().startsWith("org.restlet.")) {
                        // put the org.restlet headers in attributes
                        request.getAttributes().put(entry.getKey(), entry.getValue());
                    } else {
                        // put the user stuff in the form
                        form.add(entry.getKey(), entry.getValue().toString());
                    }
                } else {
                    // For non-form post put all the headers in attributes
                    request.getAttributes().put(entry.getKey(), entry.getValue());
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Populate Restlet request from exchange header: "
                            + entry.getKey() + " value: " + entry.getValue());
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Using Content Type: " + mediaType + " for POST data: " + body);
        }

        // Only URL Encode for GET and form POST
        if (request.getMethod() == Method.GET || (request.getMethod() == Method.POST && mediaType == MediaType.APPLICATION_WWW_FORM)) {
            request.setEntity(form.getWebRepresentation());
        } else {
            request.setEntity(body, mediaType);
        }
    }

    public void populateRestletResponseFromExchange(Exchange exchange, Response response) {
        Message out;
        if (exchange.isFailed()) {
            // 500 for internal server error which can be overridden by response code in header
            response.setStatus(Status.valueOf(500));
            if (exchange.hasOut() && exchange.getOut().isFault()) {
                out = exchange.getOut();
            } else {
                // print exception as message and stacktrace
                Exception t = exchange.getException();
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                response.setEntity(sw.toString(), MediaType.TEXT_PLAIN);
                return;
            }
        } else {
            out = exchange.getOut();
        }

        // get content type
        MediaType mediaType = out.getHeader(Exchange.CONTENT_TYPE, MediaType.class);
        if (mediaType == null) {
            Object body = out.getBody();
            mediaType = MediaType.TEXT_PLAIN;
            if (body instanceof String) {
                mediaType = MediaType.TEXT_PLAIN;
            } else if (body instanceof StringSource || body instanceof DOMSource) {
                mediaType = MediaType.TEXT_XML;
            }
        }

        // get response code
        Integer responseCode = out.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        if (responseCode != null) {
            response.setStatus(Status.valueOf(responseCode));
        }

        for (Map.Entry<String, Object> entry : out.getHeaders().entrySet()) {
            if (!headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), entry.getValue(), exchange)) {
                response.getAttributes().put(entry.getKey(), entry.getValue());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Populate Restlet response from exchange header: "
                            + entry.getKey() + " value: " + entry.getValue());
                }
            }
        }

        String text = out.getBody(String.class);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Populate Restlet response from exchange body: " + text);
        }
        response.setEntity(text, mediaType);

        if (exchange.getProperty(Exchange.CHARSET_NAME) != null) {
            CharacterSet cs = CharacterSet.valueOf(exchange.getProperty(Exchange.CHARSET_NAME, String.class));
            response.getEntity().setCharacterSet(cs);
        }
    }

    public void populateExchangeFromRestletResponse(Exchange exchange, Response response) throws Exception {
        for (Map.Entry<String, Object> entry : response.getAttributes().entrySet()) {
            if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange)) {
                exchange.getOut().setHeader(entry.getKey(), entry.getValue());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Populate exchange from Restlet response header: "
                            + entry.getKey() + " value: " + entry.getValue());
                }
            }
        }

        // set response code
        int responseCode = response.getStatus().getCode();
        exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);

        if (response.getEntity() != null) {
            // get content type
            MediaType mediaType = response.getEntity().getMediaType();
            if (mediaType != null) {
                exchange.getOut().setHeader(Exchange.CONTENT_TYPE, mediaType.toString());
            }

            // get content text
            String text = response.getEntity().getText();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Populate exchange from Restlet response: " + text);
            }
            exchange.getOut().setBody(text);
        }
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;
    }
}
