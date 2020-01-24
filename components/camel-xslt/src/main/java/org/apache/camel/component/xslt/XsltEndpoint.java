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
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;

import org.xml.sax.EntityResolver;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ProcessorEndpoint;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforms the message using a XSLT template.
 */
@ManagedResource(description = "Managed XsltEndpoint")
@UriEndpoint(firstVersion = "1.3.0", scheme = "xslt", title = "XSLT", syntax = "xslt:resourceUri", producerOnly = true, label = "core,transformation")
public class XsltEndpoint extends ProcessorEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(XsltEndpoint.class);

    private volatile boolean cacheCleared;
    private volatile XsltBuilder xslt;
    private Map<String, Object> parameters;

    @UriPath @Metadata(required = true)
    private String resourceUri;
    @UriParam(defaultValue = "true")
    private boolean contentCache = true;
    @UriParam(label = "advanced")
    private String transformerFactoryClass;
    @UriParam(label = "advanced")
    private TransformerFactory transformerFactory;
    @UriParam(label = "advanced")
    private ResultHandlerFactory resultHandlerFactory;
    @UriParam(defaultValue = "true")
    private boolean failOnNullBody = true;
    @UriParam(defaultValue = "string")
    private XsltOutput output = XsltOutput.string;
    @UriParam(defaultValue = "0")
    private int transformerCacheSize;
    @UriParam(label = "advanced")
    private ErrorListener errorListener;
    @UriParam(label = "advanced")
    private URIResolver uriResolver;
    @UriParam
    private boolean deleteOutputFile;
    @UriParam(label = "advanced")
    private EntityResolver entityResolver;
    @UriParam(label = "advanced")
    private TransformerFactoryConfigurationStrategy transformerFactoryConfigurationStrategy;

    public XsltEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @ManagedOperation(description = "Clears the cached XSLT stylesheet, forcing to re-load the stylesheet on next request")
    public void clearCachedStylesheet() {
        this.cacheCleared = true;
    }

    @ManagedAttribute(description = "Whether the XSLT stylesheet is cached")
    public boolean isCacheStylesheet() {
        return contentCache;
    }

    public XsltEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(resourceUri, newResourceUri);
        LOG.trace("Getting endpoint with URI: {}", newUri);
        return getCamelContext().getEndpoint(newUri, XsltEndpoint.class);
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        if (!contentCache || cacheCleared) {
            loadResource(resourceUri, xslt);
        }
        super.onExchange(exchange);
    }

    public boolean isCacheCleared() {
        return cacheCleared;
    }

    public void setCacheCleared(boolean cacheCleared) {
        this.cacheCleared = cacheCleared;
    }

    public XsltBuilder getXslt() {
        return xslt;
    }

    public void setXslt(XsltBuilder xslt) {
        this.xslt = xslt;
    }

    @ManagedAttribute(description = "Path to the template")
    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * Path to the template.
     * <p/>
     * The following is supported by the default URIResolver.
     * You can prefix with: classpath, file, http, ref, or bean.
     * classpath, file and http loads the resource using these protocols (classpath is default).
     * ref will lookup the resource in the registry.
     * bean will call a method on a bean to be used as the resource.
     * For bean you can specify the method name after dot, eg bean:myBean.myMethod
     *
     * @param resourceUri  the resource path
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
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

    public TransformerFactory getTransformerFactory() {
        return transformerFactory;
    }

    /**
     * To use a custom XSLT transformer factory
     */
    public void setTransformerFactory(TransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
    }

    public ResultHandlerFactory getResultHandlerFactory() {
        return resultHandlerFactory;
    }

    /**
     * Allows you to use a custom org.apache.camel.builder.xml.ResultHandlerFactory which is capable of
     * using custom org.apache.camel.builder.xml.ResultHandler types.
     */
    public void setResultHandlerFactory(ResultHandlerFactory resultHandlerFactory) {
        this.resultHandlerFactory = resultHandlerFactory;
    }

    @ManagedAttribute(description = "Whether or not to throw an exception if the input body is null")
    public boolean isFailOnNullBody() {
        return failOnNullBody;
    }

    /**
     * Whether or not to throw an exception if the input body is null.
     */
    public void setFailOnNullBody(boolean failOnNullBody) {
        this.failOnNullBody = failOnNullBody;
    }

    @ManagedAttribute(description = "What kind of option to use.")
    public XsltOutput getOutput() {
        return output;
    }

    /**
     * Option to specify which output type to use.
     * Possible values are: string, bytes, DOM, file. The first three options are all in memory based, where as file is streamed directly to a java.io.File.
     * For file you must specify the filename in the IN header with the key Exchange.XSLT_FILE_NAME which is also CamelXsltFileName.
     * Also any paths leading to the filename must be created beforehand, otherwise an exception is thrown at runtime.
     */
    public void setOutput(XsltOutput output) {
        this.output = output;
    }

    public int getTransformerCacheSize() {
        return transformerCacheSize;
    }

    /**
     * The number of javax.xml.transform.Transformer object that are cached for reuse to avoid calls to Template.newTransformer().
     */
    public void setTransformerCacheSize(int transformerCacheSize) {
        this.transformerCacheSize = transformerCacheSize;
    }

    public ErrorListener getErrorListener() {
        return errorListener;
    }

    /**
     *  Allows to configure to use a custom javax.xml.transform.ErrorListener. Beware when doing this then the default error
     *  listener which captures any errors or fatal errors and store information on the Exchange as properties is not in use.
     *  So only use this option for special use-cases.
     */
    public void setErrorListener(ErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    @ManagedAttribute(description = "Cache for the resource content (the stylesheet file) when it is loaded.")
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

    public URIResolver getUriResolver() {
        return uriResolver;
    }

    /**
     * To use a custom javax.xml.transform.URIResolver
     */
    public void setUriResolver(URIResolver uriResolver) {
        this.uriResolver = uriResolver;
    }

    public boolean isDeleteOutputFile() {
        return deleteOutputFile;
    }

    /**
     * If you have output=file then this option dictates whether or not the output file should be deleted when the Exchange
     * is done processing. For example suppose the output file is a temporary file, then it can be a good idea to delete it after use.
     */
    public void setDeleteOutputFile(boolean deleteOutputFile) {
        this.deleteOutputFile = deleteOutputFile;
    }

    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    /**
     * To use a custom org.xml.sax.EntityResolver with javax.xml.transform.sax.SAXSource.
     */
    public void setEntityResolver(EntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Additional parameters to configure on the javax.xml.transform.Transformer.
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
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

    /**
     * Loads the resource.
     *
     * @param resourceUri  the resource to load
     * @throws TransformerException is thrown if error loading resource
     * @throws IOException is thrown if error loading resource
     */
    protected void loadResource(String resourceUri, XsltBuilder xslt) throws TransformerException, IOException {
        LOG.trace("{} loading schema resource: {}", this, resourceUri);
        Source source = xslt.getUriResolver().resolve(resourceUri, null);
        if (source == null) {
            throw new IOException("Cannot load schema resource " + resourceUri);
        } else {
            xslt.setTransformerSource(source);
        }
        // now loaded so clear flag
        cacheCleared = false;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // the processor is the xslt builder
        setXslt(createXsltBuilder());
        setProcessor(getXslt());
    }

    protected XsltBuilder createXsltBuilder() throws Exception {
        final CamelContext ctx = getCamelContext();
        final ClassResolver resolver = ctx.getClassResolver();
        final Injector injector = ctx.getInjector();

        LOG.debug("{} using schema resource: {}", this, resourceUri);

        final XsltBuilder xslt = injector.newInstance(XsltBuilder.class);

        TransformerFactory factory = transformerFactory;
        if (factory == null) {
            final String trFactoryClass = transformerFactoryClass != null
                    ? transformerFactoryClass
                    : ((XsltComponent) getComponent()).getTransformerFactoryClass();
            if (trFactoryClass != null) {
                // provide the class loader of this component to work in OSGi environments
                Class<TransformerFactory> factoryClass = resolver.resolveMandatoryClass(trFactoryClass, TransformerFactory.class, XsltComponent.class.getClassLoader());
                LOG.debug("Using TransformerFactoryClass {}", factoryClass);
                factory = injector.newInstance(factoryClass);

                final TransformerFactoryConfigurationStrategy tfConfigStrategy = transformerFactoryConfigurationStrategy != null
                        ? transformerFactoryConfigurationStrategy
                        : ((XsltComponent) getComponent()).getTransformerFactoryConfigurationStrategy();
                if (tfConfigStrategy != null) {
                    tfConfigStrategy.configure(factory, this);
                }
            }
        }

        if (factory != null) {
            LOG.debug("Using TransformerFactory {}", factory);
            xslt.setTransformerFactory(factory);
        }
        if (resultHandlerFactory != null) {
            xslt.setResultHandlerFactory(resultHandlerFactory);
        }
        if (errorListener != null) {
            xslt.errorListener(errorListener);
        }
        xslt.setFailOnNullBody(failOnNullBody);
        xslt.transformerCacheSize(transformerCacheSize);
        xslt.setUriResolver(uriResolver);
        xslt.setEntityResolver(entityResolver);
        xslt.setDeleteOutputFile(deleteOutputFile);

        configureOutput(xslt, output.name());

        // any additional transformer parameters then make a copy to avoid side-effects
        if (parameters != null) {
            Map<String, Object> copy = new HashMap<>(parameters);
            xslt.setParameters(copy);
        }

        // must load resource first which sets a template and do a stylesheet compilation to catch errors early
        loadResource(resourceUri, xslt);

        return xslt;
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

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(getXslt());
    }
}
