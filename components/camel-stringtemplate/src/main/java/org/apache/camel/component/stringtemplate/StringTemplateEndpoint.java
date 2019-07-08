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

import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.stringtemplate.v4.NoIndentWriter;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;

/**
 * Transforms the message using a String template.
 */
@UriEndpoint(firstVersion = "1.2.0", scheme = "string-template", title = "String Template", syntax = "string-template:resourceUri", producerOnly = true, label = "transformation")
public class StringTemplateEndpoint extends ResourceEndpoint {

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

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        StringWriter buffer = new StringWriter();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> variableMap = exchange.getIn().getHeader(StringTemplateConstants.STRINGTEMPLATE_VARIABLE_MAP, Map.class);
        if (variableMap == null) {
            variableMap = ExchangeHelper.createVariableMap(exchange);
        }

        // getResourceAsInputStream also considers the content cache
        String text = exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, getResourceAsInputStream());
        ST template = new ST(text, delimiterStart, delimiterStop);
        for (Map.Entry<String, Object> entry : variableMap.entrySet()) {
            template.add(entry.getKey(), entry.getValue());
        }
        log.debug("StringTemplate is writing using attributes: {}", variableMap);
        template.write(new NoIndentWriter(buffer));

        // now lets output the results to the exchange
        Message out = exchange.getOut();
        out.setBody(buffer.toString());
        out.setHeaders(exchange.getIn().getHeaders());
        out.setHeader(StringTemplateConstants.STRINGTEMPLATE_RESOURCE_URI, getResourceUri());
    }
}
