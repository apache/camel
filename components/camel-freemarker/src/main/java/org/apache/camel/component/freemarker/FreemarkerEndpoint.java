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
package org.apache.camel.component.freemarker;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Map.Entry;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.component.ResourceBasedEndpoint;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Freemarker endpoint
 */
public class FreemarkerEndpoint extends ResourceBasedEndpoint {

    private String encoding;
    private Configuration configuration;

    public FreemarkerEndpoint() {
    }

    public FreemarkerEndpoint(String uri, Component component, String resourceUri) {
        super(uri, component, resourceUri, null);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "freemarker:" + getResourceUri();
    }

    /**
     * Sets the encoding to be used for loading the template file.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getEncoding() {
        return encoding;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the Freemarker configuration to use
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public FreemarkerEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(getResourceUri(), newResourceUri);
        log.debug("Getting endpoint with URI: {}", newUri);
        return (FreemarkerEndpoint) getCamelContext().getEndpoint(newUri);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(configuration, "configuration");
        ObjectHelper.notNull(path, "resourceUri");

        String newResourceUri = exchange.getIn().getHeader(FreemarkerConstants.FREEMARKER_RESOURCE_URI, String.class);
        if (newResourceUri != null) {
            exchange.getIn().removeHeader(FreemarkerConstants.FREEMARKER_RESOURCE_URI);

            log.debug("{} set to {} creating new endpoint to handle exchange", FreemarkerConstants.FREEMARKER_RESOURCE_URI, newResourceUri);
            FreemarkerEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri);
            newEndpoint.onExchange(exchange);
            return;
        }

        Reader reader = null;
        String content = exchange.getIn().getHeader(FreemarkerConstants.FREEMARKER_TEMPLATE, String.class);
        if (content != null) {
            // use content from header
            reader = new StringReader(content);
            // remove the header to avoid it being propagated in the routing
            exchange.getIn().removeHeader(FreemarkerConstants.FREEMARKER_TEMPLATE);
        }

        Map variableMap = ExchangeHelper.createVariableMap(exchange);
        // let freemarker parse and generate the result in buffer
        Template template;

        if (reader != null) {
            log.debug("Freemarker is evaluating template read from header {} using context: {}", FreemarkerConstants.FREEMARKER_TEMPLATE, variableMap);
            template = new Template("temp", reader, new Configuration());
        } else {
            log.debug("Freemarker is evaluating {} using context: {}", path, variableMap);
            if (getEncoding() != null) {
                template = configuration.getTemplate(path, getEncoding());
            } else {
                template = configuration.getTemplate(path);
            }
        }
        StringWriter buffer = new StringWriter();
        template.process(variableMap, buffer);
        buffer.flush();

        // now lets output the results to the exchange
        Message out = exchange.getOut();
        out.setBody(buffer.toString());
        Map<String, Object> headers = (Map<String, Object>) variableMap.get("headers");
        for (Entry<String, Object> entry : headers.entrySet()) {
            out.setHeader(entry.getKey(), entry.getValue());
        }
    }
}