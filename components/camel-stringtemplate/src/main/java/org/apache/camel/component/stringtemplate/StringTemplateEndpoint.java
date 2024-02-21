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
package org.apache.camel.component.stringtemplate;

import java.io.StringWriter;
import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.stringtemplate.v4.NoIndentWriter;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;

/**
 * Transform messages using StringTemplate engine.
 */
@UriEndpoint(firstVersion = "1.2.0", scheme = "string-template", title = "String Template",
             syntax = "string-template:resourceUri", producerOnly = true,
             remote = false, category = { Category.TRANSFORMATION, Category.SCRIPT },
             headersClass = StringTemplateConstants.class)
public class StringTemplateEndpoint extends ResourceEndpoint {

    @UriParam(defaultValue = "false")
    private boolean allowTemplateFromHeader;
    @UriParam(defaultValue = "<")
    private char delimiterStart = STGroup.defaultGroup.delimiterStartChar;
    @UriParam(defaultValue = ">")
    private char delimiterStop = STGroup.defaultGroup.delimiterStopChar;

    public StringTemplateEndpoint() {
    }

    public StringTemplateEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component, resourceUri);
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    public char getDelimiterStart() {
        return delimiterStart;
    }

    /**
     * The variable start delimiter
     */
    public void setDelimiterStart(char delimiterStart) {
        this.delimiterStart = delimiterStart;
    }

    public char getDelimiterStop() {
        return delimiterStop;
    }

    /**
     * The variable end delimiter
     */
    public void setDelimiterStop(char delimiterStop) {
        this.delimiterStop = delimiterStop;
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

    public StringTemplateEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(getResourceUri(), newResourceUri);
        log.debug("Getting endpoint with URI: {}", newUri);
        return getCamelContext().getEndpoint(newUri, StringTemplateEndpoint.class);
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String template = null;
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");

        StringWriter buffer = new StringWriter();

        Map<String, Object> variableMap = null;

        if (allowTemplateFromHeader) {
            String newResourceUri
                    = exchange.getIn().getHeader(StringTemplateConstants.STRINGTEMPLATE_RESOURCE_URI, String.class);
            if (newResourceUri != null) {
                exchange.getIn().removeHeader(StringTemplateConstants.STRINGTEMPLATE_RESOURCE_URI);

                log.debug("{} set to {} creating new endpoint to handle exchange",
                        StringTemplateConstants.STRINGTEMPLATE_RESOURCE_URI,
                        newResourceUri);
                StringTemplateEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri);
                newEndpoint.onExchange(exchange);
                return;
            }
            variableMap = exchange.getIn().getHeader(StringTemplateConstants.STRINGTEMPLATE_VARIABLE_MAP, Map.class);
            template = exchange.getIn().getHeader(StringTemplateConstants.STRINGTEMPLATE_TEMPLATE, String.class);
        }

        if (variableMap == null) {
            variableMap = ExchangeHelper.createVariableMap(exchange, isAllowContextMapAll());
        }

        if (template != null) {
            log.debug("StringTemplate content read from header {} for endpoint {}",
                    StringTemplateConstants.STRINGTEMPLATE_TEMPLATE,
                    getEndpointUri());
            // remove the header to avoid it being propagated in the routing
            exchange.getIn().removeHeader(StringTemplateConstants.STRINGTEMPLATE_TEMPLATE);
        } else {
            log.debug("StringTemplate content read from resource {} with resourceUri: {} for endpoint {}", getResourceUri(),
                    path,
                    getEndpointUri());
            template = exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, getResourceAsInputStream());
        }
        // getResourceAsInputStream also considers the content cache
        ST stTemplate = new ST(template, delimiterStart, delimiterStop);
        for (Map.Entry<String, Object> entry : variableMap.entrySet()) {
            stTemplate.add(entry.getKey(), entry.getValue());
        }
        log.debug("StringTemplate is writing using attributes: {}", variableMap);
        stTemplate.write(new NoIndentWriter(buffer));

        // now lets output the results to the exchange
        ExchangeHelper.setInOutBodyPatternAware(exchange, buffer.toString());
        exchange.getMessage().setHeader(StringTemplateConstants.STRINGTEMPLATE_RESOURCE_URI, getResourceUri());
    }
}
