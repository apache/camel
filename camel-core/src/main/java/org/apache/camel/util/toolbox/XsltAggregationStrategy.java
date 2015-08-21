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
package org.apache.camel.util.toolbox;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.xml.XsltBuilder;
import org.apache.camel.builder.xml.XsltUriResolver;
import org.apache.camel.component.xslt.XsltOutput;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import java.io.IOException;

/**
 * The XSLT Aggregation Strategy enables you to use XSL stylesheets to aggregate messages.
 *
 * The already aggregated payload (oldExchange) will be the input XML, the message to aggregate (newExchange)
 * will be passed in a exchange property. The exchange property name defaults to <i>xmlToAggregate</i> but can be
 * changed through the a header called <i>CamelPropertyName</i>.
 *
 * Some code bits have been copied from the {@link org.apache.camel.component.xslt.XsltEndpoint}.
 *
 */
public class XsltAggregationStrategy implements AggregationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(XsltAggregationStrategy.class);

    private static final String PROPERTY_NAME_HEADER = "CamelPropertyName";
    private static final String DEFAULT_PROPERTY_NAME = "xmlToAggregate";

    private volatile XsltBuilder xslt;
    private volatile URIResolver uriResolver;

    private String xslFile;
    private String transformerFactoryClass;
    private XsltOutput output = XsltOutput.string;

    /**
     * Constructor.
     *
     * @param xslFile to use in the aggregation.
     */
    public XsltAggregationStrategy(String xslFile) {
        this.xslFile = xslFile;
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {

        // Guard against empty new exchanges
        if (newExchange == null) {
            return oldExchange;
        }

        // first aggregation
        if (oldExchange == null) {
            return newExchange;
        }

        try {

            if (xslt == null) {
                initializeDefaultXsltBuilder(oldExchange.getContext());
            }

            String propertyName = newExchange.getIn().getHeader(PROPERTY_NAME_HEADER, String.class);
            propertyName = propertyName == null ? DEFAULT_PROPERTY_NAME : propertyName;

            oldExchange.setProperty(propertyName, newExchange.getIn().getBody(Document.class));
            xslt.process(oldExchange);

            return oldExchange;

        } catch (Exception e) {
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

    protected void initializeDefaultXsltBuilder(CamelContext context) throws Exception {

        this.xslt = context.getInjector().newInstance(XsltBuilder.class);

        if (transformerFactoryClass != null) {
            Class<?> factoryClass = context.getClassResolver().resolveMandatoryClass(transformerFactoryClass, XsltAggregationStrategy.class.getClassLoader());
            TransformerFactory factory = (TransformerFactory) context.getInjector().newInstance(factoryClass);
            xslt.getConverter().setTransformerFactory(factory);
        }

        if (uriResolver == null) {
            uriResolver = new XsltUriResolver(context.getClassResolver(), xslFile);
        }
        xslt.setUriResolver(uriResolver);

        xslt.setFailOnNullBody(true);
        xslt.transformerCacheSize(0);
        xslt.setAllowStAX(true);
        xslt.setCamelContext(context);

        configureOutput(xslt, output.name());
        loadResource(xslFile);
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
}