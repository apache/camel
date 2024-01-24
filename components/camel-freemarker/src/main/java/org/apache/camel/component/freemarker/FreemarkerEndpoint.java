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
package org.apache.camel.component.freemarker;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Transform messages using FreeMarker templates.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "freemarker", title = "Freemarker", syntax = "freemarker:resourceUri",
             remote = false, producerOnly = true, category = { Category.TRANSFORMATION },
             headersClass = FreemarkerConstants.class)
public class FreemarkerEndpoint extends ResourceEndpoint {

    @UriParam(defaultValue = "false")
    private boolean allowTemplateFromHeader;
    @UriParam
    private String encoding;
    @UriParam
    private int templateUpdateDelay;
    @UriParam
    private Configuration configuration;

    public FreemarkerEndpoint() {
    }

    public FreemarkerEndpoint(String uri, Component component, String resourceUri) {
        super(uri, component, resourceUri);
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "freemarker:" + getResourceUri();
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

    /**
     * Sets the encoding to be used for loading the template file.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getEncoding() {
        return encoding;
    }

    public int getTemplateUpdateDelay() {
        return templateUpdateDelay;
    }

    /**
     * Number of seconds the loaded template resource will remain in the cache.
     */
    public void setTemplateUpdateDelay(int templateUpdateDelay) {
        this.templateUpdateDelay = templateUpdateDelay;
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
        return getCamelContext().getEndpoint(newUri, FreemarkerEndpoint.class);
    }

    @Override
    public void clearContentCache() {
        configuration.clearTemplateCache();
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(configuration, "configuration");
        ObjectHelper.notNull(path, "resourceUri");

        if (allowTemplateFromHeader) {
            String newResourceUri = exchange.getIn().getHeader(FreemarkerConstants.FREEMARKER_RESOURCE_URI, String.class);
            if (newResourceUri != null) {
                exchange.getIn().removeHeader(FreemarkerConstants.FREEMARKER_RESOURCE_URI);

                log.debug("{} set to {} creating new endpoint to handle exchange", FreemarkerConstants.FREEMARKER_RESOURCE_URI,
                        newResourceUri);
                FreemarkerEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri);
                newEndpoint.onExchange(exchange);
                return;
            }
        }

        Reader reader = null;
        String content = null;
        if (allowTemplateFromHeader) {
            content = exchange.getIn().getHeader(FreemarkerConstants.FREEMARKER_TEMPLATE, String.class);
        }
        if (content != null) {
            // use content from header
            reader = new StringReader(content);
            // remove the header to avoid it being propagated in the routing
            exchange.getIn().removeHeader(FreemarkerConstants.FREEMARKER_TEMPLATE);
        }
        Object dataModel = null;
        if (allowTemplateFromHeader) {
            dataModel = exchange.getIn().getHeader(FreemarkerConstants.FREEMARKER_DATA_MODEL, Object.class);
        }
        if (dataModel == null) {
            dataModel = ExchangeHelper.createVariableMap(exchange, isAllowContextMapAll());
        }
        // let freemarker parse and generate the result in buffer
        Template template;

        if (reader != null) {
            log.debug("Freemarker is evaluating template read from header {} using context: {}",
                    FreemarkerConstants.FREEMARKER_TEMPLATE, dataModel);
            template = new Template("temp", reader, new Configuration(Configuration.VERSION_2_3_32));
        } else {
            log.debug("Freemarker is evaluating {} using context: {}", path, dataModel);
            if (getEncoding() != null) {
                template = configuration.getTemplate(path, getEncoding());
            } else {
                template = configuration.getTemplate(path);
            }
        }
        StringWriter buffer = new StringWriter();
        template.process(dataModel, buffer);
        buffer.flush();

        // now lets store the result
        ExchangeHelper.setInOutBodyPatternAware(exchange, buffer.toString());
    }
}
