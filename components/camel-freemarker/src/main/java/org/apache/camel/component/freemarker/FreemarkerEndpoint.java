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

import java.io.StringWriter;
import java.util.Map;

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

    @Override
    @SuppressWarnings("unchecked")
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(configuration, "configuration");
        ObjectHelper.notNull(path, "resourceUri");

        Map variableMap = ExchangeHelper.createVariableMap(exchange);

        if (log.isDebugEnabled()) {
            log.debug("Freemarker is evaluating " + path + " using context: " + variableMap);
        }

        // let freemarker parse and generate the result in buffer
        Template template;
        if (encoding != null) {
            template = configuration.getTemplate(path, encoding);
        } else {
            template = configuration.getTemplate(path);
        }
        StringWriter buffer = new StringWriter();
        template.process(variableMap, buffer);
        buffer.flush();

        // now lets output the results to the exchange
        Message out = exchange.getOut(true);
        out.setBody(buffer.toString());
        out.setHeader(FreemarkerConstants.FREEMARKER_RESOURCE, getResource());
        out.setHeader(FreemarkerConstants.FREEMARKER_RESOURCE_URI, path);
        Map<String, Object> headers = (Map<String, Object>) variableMap.get("headers");
        for (String key : headers.keySet()) {
            out.setHeader(key, headers.get(key));
        }
    }

}

