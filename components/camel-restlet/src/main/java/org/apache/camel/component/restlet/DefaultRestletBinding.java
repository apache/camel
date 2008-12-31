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
import org.apache.camel.converter.jaxp.StringSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;

/**
 * Default Restlet binding implementation
 *
 * @version $Revision$
 */
public class DefaultRestletBinding implements RestletBinding {
    private static final Log LOG = LogFactory.getLog(DefaultRestletBinding.class);

    /**
     * populateExchangeFromRestletRequest
     */
    public void populateExchangeFromRestletRequest(Request request,
            Exchange exchange) throws Exception {

        for (Map.Entry<String, Object> entry : request.getAttributes().entrySet()) {
            if (!entry.getKey().startsWith("org.restlet.")) {
                exchange.getIn().setHeader(entry.getKey(), entry.getValue());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Populate exchange from Restlet request header: " 
                            + entry.getKey() + " value: " + entry.getValue());
                }
            }
        }
        
        Form headers = (Form) request.getAttributes().get("org.restlet.http.headers");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.getValuesMap().entrySet()) {
                if (!entry.getKey().startsWith("org.restlet.")
                        && !entry.getKey().equals("Host")
                        && !entry.getKey().equals("User-Agent")
                        && !entry.getKey().equals("Content-Length")
                        && !entry.getKey().equals("Content-Type")
                        && !entry.getKey().equals("Connection")
                        && !entry.getKey().equals("Accept")) {
                    exchange.getIn().setHeader(entry.getKey(), entry.getValue());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Populate exchange from Restlet request header: " 
                                + entry.getKey() + " value: " + entry.getValue());
                    }
                }
            }
        }
        Form form = new Form(request.getEntity());
        if (form != null) {
            for (Map.Entry<String, String> entry : form.getValuesMap()
                    .entrySet()) {
                exchange.getIn().setHeader(entry.getKey(), entry.getValue());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Populate exchange from Restlet request header: " 
                            + entry.getKey() + " value: " + entry.getValue());
                }
            }
        }
        
        Object body = form.getValuesMap().get("camel.body");
        exchange.getIn().setBody(body);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Populate exchange from Restlet request body: " + body);
        }
    }

    /**
     * populateRestletRequestFromExchange
     */
    public void populateRestletRequestFromExchange(Request request,
            Exchange exchange) {
        request.setReferrerRef("camel-restlet");
        String body = exchange.getIn().getBody(String.class);
        Form form = new Form();
        form.add("camel.body", body);
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Populate Restlet request from exchange body: " + body);
        }
        
        String login = (String) exchange.getIn().removeHeader(
                RestletConstants.LOGIN);
        String password = (String) exchange.getIn().removeHeader(
                RestletConstants.PASSWORD);
          
        if (login != null && password != null) {
            ChallengeResponse authentication = new ChallengeResponse(
                    ChallengeScheme.HTTP_BASIC, login, password);
            request.setChallengeResponse(authentication);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Basic HTTP Authentication has been applied");
            }
        }
        
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            form.add(entry.getKey(), entry.getValue().toString());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Populate Restlet request from exchange header: " 
                        + entry.getKey() + " value: " + entry.getValue());
            }
        }
        
        for (Map.Entry<String, Object> entry : exchange.getProperties().entrySet()) {
            form.add(entry.getKey(), entry.getValue().toString());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Populate Restlet request from exchange header: " 
                        + entry.getKey() + " value: " + entry.getValue());
            }
        }
        request.setEntity(form.getWebRepresentation());
    }

    /**
     * populateRestletResponseFromExchange
     */
    public void populateRestletResponseFromExchange(Exchange exchange,
            Response response) {
        Object body = exchange.getOut().getBody();
        MediaType mediaType = MediaType.TEXT_PLAIN;
        if (body instanceof String) {
            mediaType = MediaType.TEXT_PLAIN;
        } else if (body instanceof StringSource || body instanceof DOMSource) {
            mediaType = MediaType.TEXT_XML;
        }
        
        String text = exchange.getOut().getBody(String.class);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Populate Restlet response from exchange body: " + text);
        }
        response.setEntity(text, mediaType);
        exchange.getIn().setBody(body);
    }

    /**
     * populateExchangeFromRestletResponse
     */
    public void populateExchangeFromRestletResponse(Exchange exchange,
            Response response) throws IOException {
        String text = response.getEntity().getText();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Populate exchange from Restlet response: " + text);
        }
        
        if (exchange.getPattern().isOutCapable()) {
            exchange.getOut().setBody(text);
        } else {
            LOG.warn("Exchange is incapable of receiving response");
        }
    }
}
