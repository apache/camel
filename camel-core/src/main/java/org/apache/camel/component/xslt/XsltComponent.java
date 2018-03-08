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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.transform.URIResolver;

import org.apache.camel.Endpoint;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <a href="http://camel.apache.org/xslt.html">XSLT Component</a> is for performing XSLT transformations of messages
 */
public class XsltComponent extends UriEndpointComponent {

    private static final Logger LOG = LoggerFactory.getLogger(XsltComponent.class);

    @Metadata(label = "advanced")
    private XmlConverter xmlConverter;
    @Metadata(label = "advanced")
    private URIResolver uriResolver;
    @Metadata(label = "advanced")
    private XsltUriResolverFactory uriResolverFactory;
    @Metadata(label = "advanced")
    private Object saxonConfiguration;
    @Metadata(label = "advanced")
    private Map<String, Object> saxonConfigurationProperties = new HashMap<>();
    @Metadata(label = "advanced", javaType = "java.lang.String")
    private List<Object> saxonExtensionFunctions;
    @Metadata(defaultValue = "true")
    private boolean contentCache = true;
    private boolean saxon;

    public XsltComponent() {
        super(XsltEndpoint.class);
    }

    public XmlConverter getXmlConverter() {
        return xmlConverter;
    }

    /**
     * To use a custom implementation of {@link org.apache.camel.converter.jaxp.XmlConverter}
     */
    public void setXmlConverter(XmlConverter xmlConverter) {
        this.xmlConverter = xmlConverter;
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

    public boolean isSaxon() {
        return saxon;
    }

    /**
     * Whether to use Saxon as the transformerFactoryClass.
     * If enabled then the class net.sf.saxon.TransformerFactoryImpl. You would need to add Saxon to the classpath.
     */
    public void setSaxon(boolean saxon) {
        this.saxon = saxon;
    }

    public List<Object> getSaxonExtensionFunctions() {
        return saxonExtensionFunctions;
    }

    /**
     * Allows you to use a custom net.sf.saxon.lib.ExtensionFunctionDefinition.
     * You would need to add camel-saxon to the classpath.
     * The function is looked up in the registry, where you can comma to separate multiple values to lookup.
     */
    public void setSaxonExtensionFunctions(List<Object> extensionFunctions) {
        this.saxonExtensionFunctions = extensionFunctions;
    }

    /**
     * Allows you to use a custom net.sf.saxon.lib.ExtensionFunctionDefinition.
     * You would need to add camel-saxon to the classpath.
     * The function is looked up in the registry, where you can comma to separate multiple values to lookup.
     */
    public void setSaxonExtensionFunctions(String extensionFunctions) {
        this.saxonExtensionFunctions = EndpointHelper.resolveReferenceListParameter(
            getCamelContext(),
            extensionFunctions,
            Object.class
        );
    }

    public Object getSaxonConfiguration() {
        return saxonConfiguration;
    }

    /**
     * To use a custom Saxon configuration
     */
    public void setSaxonConfiguration(Object saxonConfiguration) {
        this.saxonConfiguration = saxonConfiguration;
    }

    public Map<String, Object> getSaxonConfigurationProperties() {
        return saxonConfigurationProperties;
    }

    /**
     * To set custom Saxon configuration properties
     */
    public void setSaxonConfigurationProperties(Map<String, Object> configurationProperties) {
        this.saxonConfigurationProperties = configurationProperties;
    }

    @Override
    protected Endpoint createEndpoint(String uri, final String remaining, Map<String, Object> parameters) throws Exception {
        XsltEndpoint endpoint = new XsltEndpoint(uri, this);
        endpoint.setConverter(getXmlConverter());
        endpoint.setContentCache(isContentCache());
        endpoint.setSaxon(isSaxon());
        endpoint.setSaxonConfiguration(saxonConfiguration);
        endpoint.setSaxonConfigurationProperties(saxonConfigurationProperties);
        endpoint.setSaxonExtensionFunctions(saxonExtensionFunctions);

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
        endpoint.setUriResolver(resolver);

        setProperties(endpoint, parameters);

        String resourceUri = remaining;
        if (ResourceHelper.isHttpUri(resourceUri)) {
            // if its a http uri, then append additional parameters as they are part of the uri
            resourceUri = ResourceHelper.appendParameters(resourceUri, parameters);
        }
        LOG.debug("{} using schema resource: {}", this, resourceUri);
        endpoint.setResourceUri(resourceUri);

        if (!parameters.isEmpty()) {
            // additional parameters need to be stored on endpoint as they can be used to configure xslt builder additionally
            endpoint.setParameters(parameters);
        }

        return endpoint;
    }

}
