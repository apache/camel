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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mustache.MustacheConstants.MUSTACHE_ENDPOINT_URI_PREFIX;
import static org.apache.camel.component.mustache.MustacheConstants.MUSTACHE_RESOURCE_URI;
import static org.apache.camel.component.mustache.MustacheConstants.MUSTACHE_TEMPLATE;

/**
 * Render a Mustache template with the data of the message and use the text it writes as the new message body.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "mustache", title = "Mustache", syntax = "mustache:resourceUri",
             remote = false, producerOnly = true, category = { Category.TRANSFORMATION },
             headersClass = MustacheConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The template gets the body as body, as it is: text, or a Map, a List or a"
                                                + " POJO to read from; convert bytes or a stream to a String first. The"
                                                + " template is the resource, or the CamelMustacheTemplate or"
                                                + " CamelMustacheResourceUri header with allowTemplateFromHeader=true, and"
                                                + " never the body"),
           producerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String",
                                   description = "The text the template writes, also when it is JSON: unmarshal it to read its"
                                                 + " fields. Values in double braces are HTML-escaped, in triple braces as they"
                                                 + " are. The headers are kept"))
public class MustacheEndpoint extends ResourceEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(MustacheEndpoint.class);

    private static final String DEFAULT_START_DELIMITER = "{{";
    private static final String DEFAULT_END_DELIMITER = "}}";

    private MustacheFactory mustacheFactory;
    private Mustache mustache;

    @UriParam
    private boolean allowTemplateFromHeader;
    @UriParam(description = "The name of the character encoding (charset) that the producer uses to read the template"
                            + " resource of resourceUri, for example UTF-8. If not set, the default charset of the JVM is"
                            + " used. Not used for the template of the CamelMustacheTemplate header, which is already text,"
                            + " nor for partials, which the mustacheFactory reads.")
    private String encoding;
    @UriParam(defaultValue = "{{",
              description = "The characters that start a tag in the template, in place of {{. It can be set without"
                            + " endDelimiter, which then stays }}. Only applied when the mustacheFactory of the component is"
                            + " a com.github.mustachejava.DefaultMustacheFactory, which it is by default; with another factory"
                            + " the endpoint logs a WARN. Also applied to the template of the CamelMustacheTemplate header.")
    private String startDelimiter = DEFAULT_START_DELIMITER;
    @UriParam(defaultValue = "}}",
              description = "The characters that end a tag in the template, in place of }}. It can be set without"
                            + " startDelimiter, which then stays {{. Only applied when the mustacheFactory of the component is"
                            + " a com.github.mustachejava.DefaultMustacheFactory, which it is by default; with another factory"
                            + " the endpoint logs a WARN. Also applied to the template of the CamelMustacheTemplate header.")
    private String endDelimiter = DEFAULT_END_DELIMITER;

    public MustacheEndpoint() {
    }

    public MustacheEndpoint(String endpointUri, Component component, String resourceUri) {
        super(endpointUri, component, resourceUri);
    }

    @Override
    public boolean isRemote() {
        return false;
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
    protected void doInit() throws Exception {
        super.doInit();
        if (hasCustomDelimiters() && mustacheFactory != null && !(mustacheFactory instanceof DefaultMustacheFactory)) {
            LOG.warn("The startDelimiter {} and endDelimiter {} of {} are not used: the mustacheFactory {} is not a"
                     + " DefaultMustacheFactory, so the templates are compiled with its own delimiters",
                    getEffectiveStartDelimiter(), getEffectiveEndDelimiter(), this, mustacheFactory.getClass().getName());
        }
    }

    private String getEffectiveStartDelimiter() {
        return startDelimiter != null ? startDelimiter : DEFAULT_START_DELIMITER;
    }

    private String getEffectiveEndDelimiter() {
        return endDelimiter != null ? endDelimiter : DEFAULT_END_DELIMITER;
    }

    private boolean hasCustomDelimiters() {
        return !DEFAULT_START_DELIMITER.equals(getEffectiveStartDelimiter())
                || !DEFAULT_END_DELIMITER.equals(getEffectiveEndDelimiter());
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
            // one delimiter can be set alone: the other keeps its default. With the default delimiters the factory
            // compiles as it does without them
            if (hasCustomDelimiters() && mustacheFactory instanceof DefaultMustacheFactory defaultMustacheFactory) {
                newMustache = defaultMustacheFactory.compile(resourceReader, resourceUri, getEffectiveStartDelimiter(),
                        getEffectiveEndDelimiter());
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
        if (!isContentCache()) {
            // read and compile the template for each exchange, so a changed template is used
            return createMustache(getResourceAsReader(), getResourceUri());
        }
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

    public void setMustacheFactory(MustacheFactory mustacheFactory) {
        this.mustacheFactory = mustacheFactory;
    }

    public String getEncoding() {
        return encoding;
    }

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

    public void setStartDelimiter(String startDelimiter) {
        this.startDelimiter = startDelimiter;
    }

    public String getEndDelimiter() {
        return endDelimiter;
    }

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
