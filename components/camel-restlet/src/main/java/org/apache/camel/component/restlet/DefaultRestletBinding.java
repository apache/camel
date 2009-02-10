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

import java.io.IOException;
import java.util.Map;

import javax.xml.transform.dom.DOMSource;

import org.apache.camel.Exchange;
import org.apache.camel.HeaderFilterStrategyAware;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.converter.jaxp.StringSource;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Parameter;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;

/**
 * Default Restlet binding implementation
 *
 * @version $Revision$
 */
public class DefaultRestletBinding implements RestletBinding, HeaderFilterStrategyAware {
    private static final Log LOG = LogFactory.getLog(DefaultRestletBinding.class);
    private HeaderFilterStrategy headerFilterStrategy;

    /**
     * Populate Camel message from Restlet request
     * 
     * @param request message to be copied from
     * @param exchange to be populated
     * @throws Exception 
     */
    public void populateExchangeFromRestletRequest(Request request,
            Exchange exchange) throws Exception {

        Message inMessage = exchange.getIn();
        // extract headers from restlet 
        for (Map.Entry<String, Object> entry : request.getAttributes().entrySet()) {
            if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), 
                    entry.getValue())) {
                
                inMessage.setHeader(entry.getKey(), entry.getValue());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Populate exchange from Restlet request header: " 
                            + entry.getKey() + " value: " + entry.getValue());
                }

            }
        }
        
        // copy query string to header
        String query = request.getResourceRef().getQuery();
        if (null != query) {
            inMessage.setHeader(RestletConstants.QUERY_STRING, query);
        }

        if (!request.isEntityAvailable()) {
            return;
        }
        
        Form form = new Form(request.getEntity());
        if (form != null) {
            for (Map.Entry<String, String> entry : form.getValuesMap().entrySet()) {
                // extract body added to the form as the key which has null value
                if (entry.getValue() == null) {
                    inMessage.setBody(entry.getKey());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Populate exchange from Restlet request body: " + entry.getValue());
                    }
                } else {
                    if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), 
                            entry.getValue())) {

                        inMessage.setHeader(entry.getKey(), entry.getValue());
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Populate exchange from Restlet request user header: " 
                                    + entry.getKey() + " value: " + entry.getValue());
                        }
                    }
                }
            }
        }        
    }   

    /**
     * Populate Restlet Request from Camel message
     * 
     * @param request to be populated
     * @param exchange message to be copied from
     */
    public void populateRestletRequestFromExchange(Request request,
            Exchange exchange) {
        request.setReferrerRef("camel-restlet");
        String body = exchange.getIn().getBody(String.class);
        Form form = new Form();
        // add the body as the key in the form with null value
        form.add(body, null);
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Populate Restlet request from exchange body: " + body);
        }
        
        // login and password are filtered by header filter strategy
        String login = (String) exchange.getIn().getHeader(RestletConstants.LOGIN);
        String password = (String) exchange.getIn().getHeader(RestletConstants.PASSWORD);
          
        if (login != null && password != null) {
            ChallengeResponse authentication = new ChallengeResponse(
                    ChallengeScheme.HTTP_BASIC, login, password);
            request.setChallengeResponse(authentication);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Basic HTTP Authentication has been applied");
            }
        }
        
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            if (!headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), 
                    entry.getValue())) {
                if (entry.getKey().startsWith("org.restlet.")) {
                    // put the org.restlet headers in attributes
                    request.getAttributes().put(entry.getKey(), entry.getValue());
                } else {
                    // put the user stuff in the form
                    form.add(entry.getKey(), entry.getValue().toString());   
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Populate Restlet request from exchange header: " 
                            + entry.getKey() + " value: " + entry.getValue());
                }
            }
        }
        
        request.setEntity(form.getWebRepresentation());
    }

    /**
     * Populate Restlet request from Camel message
     *  
     * @param exchange message to be copied from 
     * @param response to be populated
     */
    public void populateRestletResponseFromExchange(Exchange exchange,
            Response response) {
        
        // get content type
        Message out = exchange.getOut();
        MediaType mediaType = out.getHeader(RestletConstants.MEDIA_TYPE, MediaType.class);
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
        Integer responseCode = out.getHeader(RestletConstants.RESPONSE_CODE, Integer.class);
        if (responseCode != null) {
            response.setStatus(Status.valueOf(responseCode));
        }

        for (Map.Entry<String, Object> entry : out.getHeaders().entrySet()) {
            if (!headerFilterStrategy.applyFilterToCamelHeaders(entry.getKey(), 
                    entry.getValue())) {
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
    }

    /**
     * Populate Camel message from Restlet response
     * 
     * @param exchange to be populated
     * @param response message to be copied from
     * @throws IOException 
     */
    public void populateExchangeFromRestletResponse(Exchange exchange,
            Response response) throws IOException {
        
        for (Map.Entry<String, Object> entry : response.getAttributes().entrySet()) {
            if (!headerFilterStrategy.applyFilterToExternalHeaders(entry.getKey(), 
                    entry.getValue())) {
                exchange.getOut().setHeader(entry.getKey(), entry.getValue());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Populate exchange from Restlet response header: " 
                            + entry.getKey() + " value: " + entry.getValue());
                }
            }
        }

        String text = response.getEntity().getText();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Populate exchange from Restlet response: " + text);
        }
        
        if (exchange.getPattern().isOutCapable()) {
            exchange.getOut().setBody(text);
        } else {
            throw new RuntimeCamelException("Exchange is incapable of receiving response: " 
                    + exchange);
        }
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        headerFilterStrategy = strategy;
    }
}
