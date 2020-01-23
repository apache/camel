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
package org.apache.camel.component.xslt.saxon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.TransformerFactory;

import net.sf.saxon.Configuration;
import net.sf.saxon.TransformerFactoryImpl;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.xslt.XsltEndpoint;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.EndpointHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforms the message using a XSLT template using Saxon.
 */
@ManagedResource(description = "Managed XsltSaxonEndpoint")
@UriEndpoint(firstVersion = "3.0.0", scheme = "xslt-saxon", title = "XSLT Saxon", syntax = "xslt-saxon:resourceUri", producerOnly = true, label = "core,transformation")
public class XsltSaxonEndpoint extends XsltEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(XsltSaxonEndpoint.class);

    @UriParam(label = "advanced")
    private Configuration saxonConfiguration;
    @Metadata(label = "advanced")
    private Map<String, Object> saxonConfigurationProperties = new HashMap<>();
    @UriParam(label = "advanced", javaType = "java.lang.String")
    private List<Object> saxonExtensionFunctions;
    @UriParam(displayName = "Allow StAX", defaultValue = "true")
    private boolean allowStAX = true;

    public XsltSaxonEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
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

    public Configuration getSaxonConfiguration() {
        return saxonConfiguration;
    }

    /**
     * To use a custom Saxon configuration
     */
    public void setSaxonConfiguration(Configuration saxonConfiguration) {
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

    @ManagedAttribute(description = "Whether to allow using StAX as the javax.xml.transform.Source")
    public boolean isAllowStAX() {
        return allowStAX;
    }

    /**
     * Whether to allow using StAX as the javax.xml.transform.Source.
     * You can enable this if the XSLT library supports StAX such as the Saxon library (camel-saxon).
     * The Xalan library (default in JVM) does not support StAXSource.
     */
    public void setAllowStAX(boolean allowStAX) {
        this.allowStAX = allowStAX;
    }

    @Override
    protected void doStart() throws Exception {
        // the processor is the xslt builder
        setXslt(createXsltBuilder());
        setProcessor(getXslt());
    }

    protected XsltSaxonBuilder createXsltBuilder() throws Exception {
        final CamelContext ctx = getCamelContext();
        final ClassResolver resolver = ctx.getClassResolver();
        final Injector injector = ctx.getInjector();

        LOG.debug("{} using schema resource: {}", this, getResourceUri());

        final XsltSaxonBuilder xslt = injector.newInstance(XsltSaxonBuilder.class);

        TransformerFactory factory = getTransformerFactory();
        if (factory == null) {
            if (getTransformerFactoryClass() == null) {
                // create new saxon factory
                factory = new TransformerFactoryImpl();
            } else {
                // provide the class loader of this component to work in OSGi environments
                Class<TransformerFactory> factoryClass = resolver.resolveMandatoryClass(getTransformerFactoryClass(), TransformerFactory.class, XsltSaxonComponent.class.getClassLoader());
                LOG.debug("Using TransformerFactoryClass {}", factoryClass);
                factory = injector.newInstance(factoryClass);
            }
        }

        if (factory instanceof TransformerFactoryImpl) {
            TransformerFactoryImpl tf = (TransformerFactoryImpl) factory;
            XsltSaxonHelper.registerSaxonConfiguration(tf, saxonConfiguration);
            XsltSaxonHelper.registerSaxonConfigurationProperties(tf, saxonConfigurationProperties);
            XsltSaxonHelper.registerSaxonExtensionFunctions(tf, saxonExtensionFunctions);
        }

        if (factory != null) {
            LOG.debug("Using TransformerFactory {}", factory);
            xslt.setTransformerFactory(factory);
        }
        if (getResultHandlerFactory() != null) {
            xslt.setResultHandlerFactory(getResultHandlerFactory());
        }
        if (getErrorListener() != null) {
            xslt.errorListener(getErrorListener());
        }
        xslt.setFailOnNullBody(isFailOnNullBody());
        xslt.transformerCacheSize(getTransformerCacheSize());
        xslt.setUriResolver(getUriResolver());
        xslt.setEntityResolver(getEntityResolver());
        xslt.setAllowStAX(allowStAX);
        xslt.setDeleteOutputFile(isDeleteOutputFile());

        configureOutput(xslt, getOutput().name());

        // any additional transformer parameters then make a copy to avoid side-effects
        if (getParameters() != null) {
            Map<String, Object> copy = new HashMap<>(getParameters());
            xslt.setParameters(copy);
        }

        // must load resource first which sets a template and do a stylesheet compilation to catch errors early
        loadResource(getResourceUri(), xslt);

        return xslt;
    }

}
