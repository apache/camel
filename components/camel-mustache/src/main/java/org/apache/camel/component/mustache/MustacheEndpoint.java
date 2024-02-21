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
package org.apache.camel.component.mustache;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;

import static org.apache.camel.component.mustache.MustacheConstants.MUSTACHE_ENDPOINT_URI_PREFIX;
import static org.apache.camel.component.mustache.MustacheConstants.MUSTACHE_RESOURCE_URI;
import static org.apache.camel.component.mustache.MustacheConstants.MUSTACHE_TEMPLATE;

/**
 * Transform messages using a Mustache template.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "mustache", title = "Mustache", syntax = "mustache:resourceUri",
             remote = false, producerOnly = true, category = { Category.TRANSFORMATION },
             headersClass = MustacheConstants.class)
public class MustacheEndpoint extends ResourceEndpoint {

    private MustacheFactory mustacheFactory;
    private Mustache mustache;

    @UriParam(defaultValue = "false")
    private boolean allowTemplateFromHeader;
    @UriParam
    private String encoding;
    @UriParam(defaultValue = "{{")
    private String startDelimiter;
    @UriParam(defaultValue = "}}")
    private String endDelimiter;

    public MustacheEndpoint() {
    }

    public MustacheEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component, resourceUri);
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return MUSTACHE_ENDPOINT_URI_PREFIX + getResourceUri();
    }

    @Override
    public void clearContentCache() {
        this.mustache = null;
        super.clearContentCache();
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String newResourceUri = null;
        if (allowTemplateFromHeader) {
            newResourceUri = exchange.getIn().getHeader(MUSTACHE_RESOURCE_URI, String.class);
        }
        if (newResourceUri != null) {
            exchange.getIn().removeHeader(MustacheConstants.MUSTACHE_RESOURCE_URI);
            MustacheEndpoint newEndpoint
                    = getCamelContext().getEndpoint(MUSTACHE_ENDPOINT_URI_PREFIX + newResourceUri, MustacheEndpoint.class);
            newEndpoint.onExchange(exchange);
            return;
        }

        // Get Mustache
        String newTemplate = null;
        if (allowTemplateFromHeader) {
            newTemplate = exchange.getIn().getHeader(MUSTACHE_TEMPLATE, String.class);
        }
        Mustache newMustache;
        if (newTemplate == null) {
            newMustache = getOrCreateMustache();
        } else {
            newMustache = createMustache(new StringReader(newTemplate), "mustache:temp#" + newTemplate.hashCode());
            exchange.getIn().removeHeader(MUSTACHE_TEMPLATE);
        }

        // Execute Mustache
        Map<String, Object> variableMap = ExchangeHelper.createVariableMap(exchange, isAllowContextMapAll());
        StringWriter writer = new StringWriter();
        newMustache.execute(writer, variableMap);
        writer.flush();

        // Fill out message
        ExchangeHelper.setInOutBodyPatternAware(exchange, writer.toString());
    }

    /**
     * Read and compile a Mustache template
     *
     * @param  resourceReader Reader used to get template
     * @param  resourceUri    Template Id
     * @return                Template
     */
    private Mustache createMustache(Reader resourceReader, String resourceUri) throws IOException {
        ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader apcl = getCamelContext().getApplicationContextClassLoader();
            if (apcl != null) {
                Thread.currentThread().setContextClassLoader(apcl);
            }
            Mustache newMustache;
            if (startDelimiter != null && endDelimiter != null && mustacheFactory instanceof DefaultMustacheFactory) {
                DefaultMustacheFactory defaultMustacheFactory = (DefaultMustacheFactory) mustacheFactory;
                newMustache = defaultMustacheFactory.compile(resourceReader, resourceUri, startDelimiter, endDelimiter);
            } else {
                newMustache = mustacheFactory.compile(resourceReader, resourceUri);
            }
            return newMustache;
        } finally {
            resourceReader.close();
            Thread.currentThread().setContextClassLoader(oldcl);
        }
    }

    private Mustache getOrCreateMustache() throws IOException {
        if (mustache == null) {
            mustache = createMustache(getResourceAsReader(), getResourceUri());
        }
        return mustache;
    }

    @Override
    public String getResourceUri() {
        // do not have leading slash as mustache cannot find the resource, as that entails classpath root
        String uri = super.getResourceUri();
        if (uri != null && (uri.startsWith("/") || uri.startsWith("\\"))) {
            return uri.substring(1);
        } else {
            return uri;
        }
    }

    public MustacheFactory getMustacheFactory() {
        return mustacheFactory;
    }

    /**
     * To use a custom {@link MustacheFactory}
     */
    public void setMustacheFactory(MustacheFactory mustacheFactory) {
        this.mustacheFactory = mustacheFactory;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Character encoding of the resource content.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    private Reader getResourceAsReader() throws IOException {
        return encoding == null
                ? new InputStreamReader(getResourceAsInputStream())
                : new InputStreamReader(getResourceAsInputStream(), encoding);
    }

    public String getStartDelimiter() {
        return startDelimiter;
    }

    /**
     * Characters used to mark template code beginning.
     */
    public void setStartDelimiter(String startDelimiter) {
        this.startDelimiter = startDelimiter;
    }

    public String getEndDelimiter() {
        return endDelimiter;
    }

    /**
     * Characters used to mark template code end.
     */
    public void setEndDelimiter(String endDelimiter) {
        this.endDelimiter = endDelimiter;
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

}
