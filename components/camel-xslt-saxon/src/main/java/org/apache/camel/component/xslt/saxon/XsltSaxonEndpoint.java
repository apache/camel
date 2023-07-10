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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import net.sf.saxon.Configuration;
import net.sf.saxon.TransformerFactoryImpl;
import org.apache.camel.CamelContext;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.xslt.XsltBuilder;
import org.apache.camel.component.xslt.XsltEndpoint;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transform XML payloads using an XSLT template using Saxon.
 */
@ManagedResource(description = "Managed XsltSaxonEndpoint")
@UriEndpoint(firstVersion = "3.0.0", scheme = "xslt-saxon", title = "XSLT Saxon", syntax = "xslt-saxon:resourceUri",
             producerOnly = true, category = { Category.CORE, Category.TRANSFORMATION })
public class XsltSaxonEndpoint extends XsltEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(XsltSaxonEndpoint.class);

    @UriParam(label = "advanced")
    private Configuration saxonConfiguration;
    @Metadata(label = "advanced")
    private Map<String, Object> saxonConfigurationProperties = new HashMap<>();
    @Metadata(label = "advanced")
    private Map<String, Object> saxonReaderProperties = new HashMap<>();
    @UriParam(label = "advanced", javaType = "java.lang.String")
    private List<Object> saxonExtensionFunctions;
    @UriParam(displayName = "Allow StAX", defaultValue = "true")
    private boolean allowStAX = true;
    @UriParam(label = "advanced", defaultValue = "true")
    private boolean secureProcessing = true;

    public XsltSaxonEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public List<Object> getSaxonExtensionFunctions() {
        return saxonExtensionFunctions;
    }

    /**
     * Allows you to use a custom net.sf.saxon.lib.ExtensionFunctionDefinition. You would need to add camel-saxon to the
     * classpath. The function is looked up in the registry, where you can comma to separate multiple values to lookup.
     */
    public void setSaxonExtensionFunctions(List<Object> extensionFunctions) {
        this.saxonExtensionFunctions = extensionFunctions;
    }

    /**
     * Allows you to use a custom net.sf.saxon.lib.ExtensionFunctionDefinition. You would need to add camel-saxon to the
     * classpath. The function is looked up in the registry, where you can comma to separate multiple values to lookup.
     */
    public void setSaxonExtensionFunctions(String extensionFunctions) {
        this.saxonExtensionFunctions = EndpointHelper.resolveReferenceListParameter(
                getCamelContext(),
                extensionFunctions,
                Object.class);
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

    public Map<String, Object> getSaxonReaderProperties() {
        return saxonReaderProperties;
    }

    /**
     * To set custom Saxon Reader properties
     */
    public void setSaxonReaderProperties(Map<String, Object> saxonReaderProperties) {
        this.saxonReaderProperties = saxonReaderProperties;
    }

    @ManagedAttribute(description = "Whether to allow using StAX as the javax.xml.transform.Source")
    public boolean isAllowStAX() {
        return allowStAX;
    }

    /**
     * Whether to allow using StAX as the javax.xml.transform.Source. You can enable this if the XSLT library supports
     * StAX such as the Saxon library (camel-saxon). The Xalan library (default in JVM) does not support StAXSource.
     */
    public void setAllowStAX(boolean allowStAX) {
        this.allowStAX = allowStAX;
    }

    public boolean isSecureProcessing() {
        return secureProcessing;
    }

    /**
     * Feature for XML secure processing (see javax.xml.XMLConstants). This is enabled by default. However, when using
     * Saxon Professional you may need to turn this off to allow Saxon to be able to use Java extension functions.
     */
    public void setSecureProcessing(boolean secureProcessing) {
        this.secureProcessing = secureProcessing;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // the processor is the xslt builder
        setXslt(createXsltBuilder());

        // must load resource first which sets a template and do a stylesheet compilation to catch errors early
        // load resource from classpath otherwise load in doStart()
        if (ResourceHelper.isClasspathUri(getResourceUri())) {
            loadResource(getResourceUri(), getXslt());
        }

        setProcessor(getXslt());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (!ResourceHelper.isClasspathUri(getResourceUri())) {
            loadResource(getResourceUri(), getXslt());
        }
    }

    @Override
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
                Class<TransformerFactory> factoryClass = resolver.resolveMandatoryClass(getTransformerFactoryClass(),
                        TransformerFactory.class, XsltSaxonComponent.class.getClassLoader());
                LOG.debug("Using TransformerFactoryClass {}", factoryClass);
                factory = injector.newInstance(factoryClass);
            }
        }

        if (factory instanceof TransformerFactoryImpl) {
            TransformerFactoryImpl tf = (TransformerFactoryImpl) factory;
            XsltSaxonHelper.registerSaxonConfiguration(tf, saxonConfiguration);
            XsltSaxonHelper.registerSaxonConfigurationProperties(tf, saxonConfigurationProperties);
            XsltSaxonHelper.registerSaxonExtensionFunctions(tf, saxonExtensionFunctions, secureProcessing);
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

        if (getXsltMessageLogger() != null) {
            xslt.setXsltMessageLogger(getXsltMessageLogger());
        }

        configureOutput(xslt, getOutput().name());

        // any additional transformer parameters then make a copy to avoid side effects
        if (getParameters() != null) {
            Map<String, Object> copy = new HashMap<>(getParameters());
            xslt.setParameters(copy);
        }

        return xslt;
    }

    /**
     * Loads the resource.
     *
     * @param  resourceUri          the resource to load
     * @throws TransformerException is thrown if error loading resource
     * @throws IOException          is thrown if error loading resource
     */
    @Override
    protected void loadResource(String resourceUri, XsltBuilder xslt) throws TransformerException, IOException {
        LOG.trace("{} loading schema resource: {}", this, resourceUri);
        Source source = xslt.getUriResolver().resolve(resourceUri, null);
        if (this.saxonReaderProperties != null) {
            //for Saxon we need to create XMLReader for the coming source
            //so that the features configuration can take effect
            source = createReaderForSource(source);
        }
        if (source == null) {
            throw new IOException("Cannot load schema resource " + resourceUri);
        } else {
            xslt.setTransformerSource(source);
        }
        // now loaded so clear flag
        setCacheCleared(false);
    }

    private Source createReaderForSource(Source source) {
        try {
            XMLReader xmlReader = XMLReaderFactory.createXMLReader();
            for (Map.Entry<String, Object> entry : this.saxonReaderProperties.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                try {
                    URI uri = new URI(key);
                    if (value != null
                            && (value.toString().equals("true") || (value.toString().equals("false")))) {
                        xmlReader.setFeature(uri.toString(), Boolean.valueOf(value.toString()));
                    } else if (value != null) {
                        xmlReader.setProperty(uri.toString(), value);
                    }
                } catch (URISyntaxException e) {
                    LOG.debug("{} isn't a valid URI, so ingore it", key);
                }
            }
            InputSource inputSource = SAXSource.sourceToInputSource(source);
            return new SAXSource(xmlReader, inputSource);
        } catch (SAXException e) {
            LOG.info("Can't created XMLReader for source ", e);
            return null;
        }

    }

}
