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
package org.apache.camel.component.xslt;

import java.util.Map;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.xml.ResultHandlerFactory;
import org.apache.camel.builder.xml.XsltBuilder;
import org.apache.camel.builder.xml.XsltUriResolver;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An <a href="http://camel.apache.org/xslt.html">XSLT Component</a>
 * for performing XSLT transforms of messages
 */
public class XsltComponent extends UriEndpointComponent {

    private static final String SAXON_TRANSFORMER_FACTORY_CLASS_NAME = "net.sf.saxon.TransformerFactoryImpl";
    private static final Logger LOG = LoggerFactory.getLogger(XsltComponent.class);
    private XmlConverter xmlConverter;
    private URIResolver uriResolver;
    private boolean contentCache = true;
    private boolean saxon;

    public XsltComponent() {
        super(XsltEndpoint.class);
    }

    public XmlConverter getXmlConverter() {
        return xmlConverter;
    }

    public void setXmlConverter(XmlConverter xmlConverter) {
        this.xmlConverter = xmlConverter;
    }

    public URIResolver getUriResolver() {
        return uriResolver;
    }

    public void setUriResolver(URIResolver uriResolver) {
        this.uriResolver = uriResolver;
    }

    public boolean isContentCache() {
        return contentCache;
    }

    public void setContentCache(boolean contentCache) {
        this.contentCache = contentCache;
    }

    public boolean isSaxon() {
        return saxon;
    }

    public void setSaxon(boolean saxon) {
        this.saxon = saxon;
    }

    protected Endpoint createEndpoint(String uri, final String remaining, Map<String, Object> parameters) throws Exception {
        String resourceUri = remaining;
        LOG.debug("{} using schema resource: {}", this, resourceUri);
        final XsltBuilder xslt = getCamelContext().getInjector().newInstance(XsltBuilder.class);

        // lets allow the converter to be configured
        XmlConverter converter = resolveAndRemoveReferenceParameter(parameters, "converter", XmlConverter.class);
        if (converter == null) {
            converter = getXmlConverter();
        }
        if (converter != null) {
            xslt.setConverter(converter);
        }

        String transformerFactoryClassName = getAndRemoveParameter(parameters, "transformerFactoryClass", String.class);
        Boolean saxon = getAndRemoveParameter(parameters, "saxon", Boolean.class, isSaxon());
        if (transformerFactoryClassName == null && saxon) {
            transformerFactoryClassName = SAXON_TRANSFORMER_FACTORY_CLASS_NAME;
        }

        TransformerFactory factory = null;
        if (transformerFactoryClassName != null) {
            // provide the class loader of this component to work in OSGi environments
            Class<?> factoryClass = getCamelContext().getClassResolver().resolveMandatoryClass(transformerFactoryClassName, XsltComponent.class.getClassLoader());
            LOG.debug("Using TransformerFactoryClass {}", factoryClass);
            factory = (TransformerFactory) getCamelContext().getInjector().newInstance(factoryClass);
        }

        if (parameters.get("transformerFactory") != null) {
            factory = resolveAndRemoveReferenceParameter(parameters, "transformerFactory", TransformerFactory.class);
        }

        if (factory != null) {
            LOG.debug("Using TransformerFactory {}", factory);
            xslt.getConverter().setTransformerFactory(factory);
        }

        ResultHandlerFactory resultHandlerFactory = resolveAndRemoveReferenceParameter(parameters, "resultHandlerFactory", ResultHandlerFactory.class);
        if (resultHandlerFactory != null) {
            xslt.setResultHandlerFactory(resultHandlerFactory);
        }

        Boolean failOnNullBody = getAndRemoveParameter(parameters, "failOnNullBody", Boolean.class);
        if (failOnNullBody != null) {
            xslt.setFailOnNullBody(failOnNullBody);
        }
        String output = getAndRemoveParameter(parameters, "output", String.class);
        configureOutput(xslt, output);
        
        Integer cs = getAndRemoveParameter(parameters, "transformerCacheSize", Integer.class, 0);
        xslt.transformerCacheSize(cs);

        ErrorListener errorListener = resolveAndRemoveReferenceParameter(parameters, "errorListener", ErrorListener.class);
        if (errorListener != null) {
            xslt.errorListener(errorListener);
        }

        // default to use the cache option from the component if the endpoint did not have the contentCache parameter
        boolean cache = getAndRemoveParameter(parameters, "contentCache", Boolean.class, contentCache);

        // if its a http uri, then append additional parameters as they are part of the uri
        if (ResourceHelper.isHttpUri(resourceUri)) {
            resourceUri = ResourceHelper.appendParameters(resourceUri, parameters);
        }

        // lookup custom resolver to use
        URIResolver resolver = resolveAndRemoveReferenceParameter(parameters, "uriResolver", URIResolver.class);
        if (resolver == null) {
            // not in endpoint then use component specific resolver
            resolver = getUriResolver();
        }
        if (resolver == null) {
            // fallback to use a Camel specific resolver
            resolver = new XsltUriResolver(getCamelContext().getClassResolver(), remaining);
        }
        // set resolver before input stream as resolver is used when loading the input stream
        xslt.setUriResolver(resolver);
        
        configureXslt(xslt, uri, remaining, parameters);

        return new XsltEndpoint(uri, this, xslt, resourceUri, cache);
    }

    protected void configureXslt(XsltBuilder xslt, String uri, String remaining, Map<String, Object> parameters) throws Exception {
        setProperties(xslt, parameters);
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

}
