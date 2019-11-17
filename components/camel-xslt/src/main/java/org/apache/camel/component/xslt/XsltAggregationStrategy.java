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
package org.apache.camel.component.xslt;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;

import org.w3c.dom.Document;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The XSLT Aggregation Strategy enables you to use XSL stylesheets to aggregate messages.
 * <p>
 * Since XSLT does not directly support providing multiple XML payloads as an input, this aggregator injects
 * the new incoming XML document (<tt>newExchange</tt>) into the <tt>oldExchange</tt> as an exchange property of
 * type {@link Document}. The old exchange therefore remains accessible as the root context.
 * This exchange property can then be accessed from your XSLT by declaring an {@code <xsl:param />} at the top
 * of your stylesheet:
 *
 * <code>
 *     <xsl:param name="new-exchange" />
 *
 *     <xsl:template match="/">
 *         <abc>
 *             <xsl:copy-of select="/ElementFromOldExchange" />
 *             <xsl:copy-of select="$new-exchange/ElementFromNewExchange" />
 *         </abc>
 *     </xsl:template>
 * </code>
 *
 * The exchange property name defaults to <tt>new-exchange</tt> but can be
 * changed through {@link #setPropertyName(String)}.
 * <p>
 * Some code bits have been copied from the {@link org.apache.camel.component.xslt.XsltEndpoint}.
 */
public class XsltAggregationStrategy extends ServiceSupport implements AggregationStrategy, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(XsltAggregationStrategy.class);
    private static final String DEFAULT_PROPERTY_NAME = "new-exchange";

    private volatile XsltBuilder xslt;
    private volatile URIResolver uriResolver;
    private CamelContext camelContext;

    private String propertyName;
    private final String xslFile;
    private TransformerFactory transformerFactory;
    private String transformerFactoryClass;
    private XsltOutput output = XsltOutput.string;

    /**
     * Constructor.
     *
     * @param xslFileLocation location of the XSL transformation
     */
    public XsltAggregationStrategy(String xslFileLocation) {
        this.xslFile = xslFileLocation;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        // guard against unlikely NPE
        if (newExchange == null) {
            return oldExchange;
        }

        // in the first call to this aggregation, do not call the XSLT but instead store the
        // incoming exchange
        if (oldExchange == null) {
            return newExchange;
        }

        if (!isRunAllowed()) {
            throw new RejectedExecutionException();
        }

        try {
            oldExchange.setProperty(propertyName, newExchange.getIn().getBody(Document.class));
            xslt.process(oldExchange);
            return oldExchange;
        } catch (Throwable e) {
            oldExchange.setException(e);
        }

        return oldExchange;
    }

    public void setOutput(XsltOutput output) {
        this.output = output;
    }

    public void setXslt(XsltBuilder xslt) {
        this.xslt = xslt;
    }

    public void setUriResolver(URIResolver uriResolver) {
        this.uriResolver = uriResolver;
    }

    public void setTransformerFactoryClass(String transformerFactoryClass) {
        this.transformerFactoryClass = transformerFactoryClass;
    }

    public void setTransformerFactory(TransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    protected void configureOutput(XsltBuilder xslt, String output) throws Exception {
        if (ObjectHelper.isEmpty(output)) {
            return;
        }

        if ("string".equalsIgnoreCase(output)) {
            xslt.outputString();
        } else if ("bytes".equalsIgnoreCase(output)) {
            xslt.outputBytes();
        } else if ("DOM".equalsIgnoreCase(output)) {
            xslt.outputDOM();
        } else if ("file".equalsIgnoreCase(output)) {
            xslt.outputFile();
        } else {
            throw new IllegalArgumentException("Unknown output type: " + output);
        }
    }

    /**
     * Loads the resource.
     *
     * @param resourceUri the resource to load
     * @throws TransformerException is thrown if error loading resource
     * @throws IOException          is thrown if error loading resource
     */
    protected void loadResource(String resourceUri) throws TransformerException, IOException {
        LOG.trace("{} loading schema resource: {}", this, resourceUri);
        Source source = xslt.getUriResolver().resolve(resourceUri, null);
        if (source == null) {
            throw new IOException("Cannot load schema resource " + resourceUri);
        } else {
            xslt.setTransformerSource(source);
        }
    }

    // --- fluent builders ---
    public static XsltAggregationStrategy create(String xslFile) {
        return new XsltAggregationStrategy(xslFile);
    }

    public XsltAggregationStrategy withPropertyName(String propertyName) {
        setPropertyName(propertyName);
        return this;
    }

    public XsltAggregationStrategy withOutput(XsltOutput output) {
        setOutput(output);
        return this;
    }

    public XsltAggregationStrategy withUriResolver(URIResolver resolver) {
        setUriResolver(resolver);
        return this;
    }

    public XsltAggregationStrategy withTransformerFactoryClass(String clazz) {
        setTransformerFactoryClass(clazz);
        return this;
    }

    protected XsltBuilder createXsltBuilder() {
        return camelContext.getInjector().newInstance(XsltBuilder.class);
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        // set the default property name if not set
        this.propertyName = ObjectHelper.isNotEmpty(propertyName) ? propertyName : DEFAULT_PROPERTY_NAME;

        // initialize the XsltBuilder
        this.xslt = createXsltBuilder();

        if (transformerFactory == null && transformerFactoryClass != null) {
            Class<?> factoryClass = camelContext.getClassResolver().resolveMandatoryClass(transformerFactoryClass,
                XsltAggregationStrategy.class.getClassLoader());
            TransformerFactory factory = (TransformerFactory) camelContext.getInjector().newInstance(factoryClass);
            xslt.setTransformerFactory(factory);
        } else if (transformerFactory != null) {
            xslt.setTransformerFactory(transformerFactory);
        }

        if (uriResolver == null) {
            uriResolver = new XsltUriResolver(camelContext, xslFile);
        }

        xslt.setUriResolver(uriResolver);
        xslt.setFailOnNullBody(true);
        xslt.transformerCacheSize(0);

        configureOutput(xslt, output.name());
        loadResource(xslFile);
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
