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

import java.util.Map;

import javax.xml.transform.URIResolver;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/xslt.html">XSLT Component</a> is for performing XSLT transformations of messages
 */
@Component("xslt")
public class XsltComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(XsltComponent.class);

    @Metadata(label = "advanced")
    private URIResolver uriResolver;
    @Metadata(label = "advanced")
    private XsltUriResolverFactory uriResolverFactory;
    @Metadata(defaultValue = "true")
    private boolean contentCache = true;
    @Metadata(label = "advanced")
    private TransformerFactoryConfigurationStrategy transformerFactoryConfigurationStrategy;
    @Metadata(label = "advanced")
    private String transformerFactoryClass;

    public XsltComponent() {
    }

    public XsltUriResolverFactory getUriResolverFactory() {
        return uriResolverFactory;
    }

    /**
     * To use a custom UriResolver which depends on a dynamic endpoint resource URI. Should not be used together with the option 'uriResolver'.
     */
    public void setUriResolverFactory(XsltUriResolverFactory uriResolverFactory) {
        this.uriResolverFactory = uriResolverFactory;
    }

    public URIResolver getUriResolver() {
        return uriResolver;
    }

    /**
     * To use a custom UriResolver. Should not be used together with the option 'uriResolverFactory'.
     */
    public void setUriResolver(URIResolver uriResolver) {
        this.uriResolver = uriResolver;
    }

    public boolean isContentCache() {
        return contentCache;
    }

    /**
     * Cache for the resource content (the stylesheet file) when it is loaded.
     * If set to false Camel will reload the stylesheet file on each message processing. This is good for development.
     * A cached stylesheet can be forced to reload at runtime via JMX using the clearCachedStylesheet operation.
     */
    public void setContentCache(boolean contentCache) {
        this.contentCache = contentCache;
    }

    public TransformerFactoryConfigurationStrategy getTransformerFactoryConfigurationStrategy() {
        return transformerFactoryConfigurationStrategy;
    }

    /**
     * A configuration strategy to apply on freshly created instances of TransformerFactory.
     */
    public void setTransformerFactoryConfigurationStrategy(
            TransformerFactoryConfigurationStrategy transformerFactoryConfigurationStrategy) {
        this.transformerFactoryConfigurationStrategy = transformerFactoryConfigurationStrategy;
    }

    public String getTransformerFactoryClass() {
        return transformerFactoryClass;
    }

    /**
     * To use a custom XSLT transformer factory, specified as a FQN class name
     */
    public void setTransformerFactoryClass(String transformerFactoryClass) {
        this.transformerFactoryClass = transformerFactoryClass;
    }

    @Override
    protected Endpoint createEndpoint(String uri, final String remaining, Map<String, Object> parameters) throws Exception {
        XsltEndpoint endpoint = createXsltEndpoint(uri);
        configureEndpoint(endpoint, remaining, parameters);

        return endpoint;
    }

    protected XsltEndpoint createXsltEndpoint(String uri) {
        return new XsltEndpoint(uri, this);
    }

    protected void configureEndpoint(Endpoint endpoint, final String remaining, Map<String, Object> parameters) throws Exception {
        XsltEndpoint xslt = (XsltEndpoint) endpoint;
        xslt.setContentCache(isContentCache());

        // lookup custom resolver to use
        URIResolver resolver = resolveAndRemoveReferenceParameter(parameters, "uriResolver", URIResolver.class);
        if (resolver == null) {
            // not in endpoint then use component specific resolver
            resolver = getUriResolver();
        }
        if (resolver == null) {
            // lookup custom resolver factory to use
            XsltUriResolverFactory resolverFactory = resolveAndRemoveReferenceParameter(parameters, "uriResolverFactory", XsltUriResolverFactory.class);
            if (resolverFactory == null) {
                // not in endpoint then use component specific resolver factory
                resolverFactory = getUriResolverFactory();
            }
            if (resolverFactory == null) {
                // fallback to use the Default URI resolver factory
                resolverFactory = new DefaultXsltUriResolverFactory();
            }

            resolver = resolverFactory.createUriResolver(getCamelContext(), remaining);
        }
        xslt.setUriResolver(resolver);

        setProperties(endpoint, parameters);

        String resourceUri = remaining;
        if (ResourceHelper.isHttpUri(resourceUri)) {
            // if its a http uri, then append additional parameters as they are part of the uri
            resourceUri = ResourceHelper.appendParameters(resourceUri, parameters);
        }
        LOG.debug("{} using schema resource: {}", this, resourceUri);
        xslt.setResourceUri(resourceUri);

        if (!parameters.isEmpty()) {
            // additional parameters need to be stored on endpoint as they can be used to configure xslt builder additionally
            xslt.setParameters(parameters);
        }
    }
}
