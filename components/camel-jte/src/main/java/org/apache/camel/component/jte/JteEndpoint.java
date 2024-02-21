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
package org.apache.camel.component.jte;

import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Transform messages using a Java based template engine (JTE).
 */
@UriEndpoint(firstVersion = "4.4.0", scheme = "jte", title = "JTE", syntax = "jte:resourceUri",
             remote = false, producerOnly = true, category = { Category.TRANSFORMATION }, headersClass = JteConstants.class)
public class JteEndpoint extends ResourceEndpoint {

    @UriParam(defaultValue = "false")
    private boolean allowTemplateFromHeader;

    public JteEndpoint() {
    }

    public JteEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component, resourceUri);
    }

    @Override
    public JteComponent getComponent() {
        return (JteComponent) super.getComponent();
    }

    public boolean isAllowTemplateFromHeader() {
        return allowTemplateFromHeader;
    }

    /**
     * Whether to allow to use resource template from header or not (default false).
     *
     * Enabling this allows to specify dynamic templates via message header. However this can be seen as a potential
     * security vulnerability if the header is coming from a malicious user, so use this with care.
     */
    public void setAllowTemplateFromHeader(boolean allowTemplateFromHeader) {
        this.allowTemplateFromHeader = allowTemplateFromHeader;
    }

    public JteEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(getResourceUri(), newResourceUri);
        log.debug("Getting endpoint with URI: {}", newUri);
        return getCamelContext().getEndpoint(newUri, JteEndpoint.class);
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");

        if (allowTemplateFromHeader) {
            String newResourceUri = exchange.getIn().getHeader(JteConstants.JTE_RESOURCE_URI, String.class);
            if (newResourceUri != null) {
                exchange.getIn().removeHeader(JteConstants.JTE_RESOURCE_URI);
                log.debug("{} set to {} creating new endpoint to handle exchange", JteConstants.JTE_RESOURCE_URI,
                        newResourceUri);
                JteEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri);
                newEndpoint.onExchange(exchange);
                return;
            }
        }

        String name = getResourceUri();
        String content;
        if (allowTemplateFromHeader) {
            content = exchange.getIn().getHeader(JteConstants.JTE_TEMPLATE, String.class);
            if (content != null) {
                // remove the header to avoid it being propagated in the routing
                exchange.getIn().removeHeader(JteConstants.JTE_TEMPLATE);
                // add template in code resolver so we can find it
                JteCodeResolver codeResolver = getComponent().getCodeResolver();
                if (codeResolver != null) {
                    name = exchange.getExchangeId();
                    codeResolver.addTemplateFromHeader(name, content);
                }
            }
        }
        Object dataModel = null;
        if (allowTemplateFromHeader) {
            dataModel = exchange.getIn().getHeader(JteConstants.JTE_DATA_MODEL, Object.class);
        }
        if (dataModel == null) {
            Model model = new Model(getCamelContext());
            model.body = exchange.getMessage().getBody();
            model.headers = exchange.getMessage().getHeaders();
            if (isAllowContextMapAll()) {
                model.exchange = exchange;
            }
            dataModel = model;
        }

        // let JTE parse and generate the result in buffer
        TemplateEngine template = getComponent().getTemplateEngine();

        TemplateOutput buffer = new StringOutput();
        template.render(name, dataModel, buffer);

        // now lets store the result
        String s = buffer.toString();
        // trim leading and ending empty lines
        s = s.trim();
        ExchangeHelper.setInOutBodyPatternAware(exchange, s);
    }

}
