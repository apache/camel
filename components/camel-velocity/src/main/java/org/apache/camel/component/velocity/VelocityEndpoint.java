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
package org.apache.camel.component.velocity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.RuntimeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforms the message using a Velocity template.
 */
@UriEndpoint(firstVersion = "1.2.0", scheme = "velocity", title = "Velocity", syntax = "velocity:resourceUri", producerOnly = true, label = "transformation")
public class VelocityEndpoint extends ResourceEndpoint {

    private VelocityEngine velocityEngine;

    @UriParam(defaultValue = "true")
    private boolean loaderCache = true;
    @UriParam
    private String encoding;
    @UriParam
    private String propertiesFile;

    public VelocityEndpoint() {
    }

    public VelocityEndpoint(String uri, VelocityComponent component, String resourceUri) {
        super(uri, component, resourceUri);
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "velocity:" + getResourceUri();
    }

    private synchronized VelocityEngine getVelocityEngine() throws Exception {
        if (velocityEngine == null) {
            velocityEngine = new VelocityEngine();

            // set the class resolver as a property so we can access it from CamelVelocityClasspathResourceLoader
            velocityEngine.addProperty("CamelClassResolver", getCamelContext().getClassResolver());

            // set default properties
            Properties properties = new Properties();
            properties.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_CACHE, isLoaderCache() ? "true" : "false");
            properties.setProperty(RuntimeConstants.RESOURCE_LOADER, "file, class");
            properties.setProperty("class.resource.loader.description", "Camel Velocity Classpath Resource Loader");
            properties.setProperty("class.resource.loader.class", CamelVelocityClasspathResourceLoader.class.getName());
            final Logger velocityLogger = LoggerFactory.getLogger("org.apache.camel.maven.Velocity");
            properties.setProperty(RuntimeConstants.RUNTIME_LOG_NAME, velocityLogger.getName());
            
          

            // load the velocity properties from property file which may overrides the default ones
            if (ObjectHelper.isNotEmpty(getPropertiesFile())) {
                InputStream reader = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), getPropertiesFile());
                try {
                    properties.load(reader);
                    log.info("Loaded the velocity configuration file {}", getPropertiesFile());
                } finally {
                    IOHelper.close(reader, getPropertiesFile(), log);
                }
            }

            log.debug("Initializing VelocityEngine with properties {}", properties);
            // help the velocityEngine to load the CamelVelocityClasspathResourceLoader
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            try {
                ClassLoader delegate = new CamelVelocityDelegateClassLoader(old);
                Thread.currentThread().setContextClassLoader(delegate);
                velocityEngine.init(properties);
            } finally {
                Thread.currentThread().setContextClassLoader(old);
            }
        }
        return velocityEngine;
    }

    public void setVelocityEngine(VelocityEngine velocityEngine) {
        this.velocityEngine = velocityEngine;
    }

    public boolean isLoaderCache() {
        return loaderCache;
    }

    /**
     * Enables / disables the velocity resource loader cache which is enabled by default
     */
    public void setLoaderCache(boolean loaderCache) {
        this.loaderCache = loaderCache;
    }

    /**
     * Character encoding of the resource content.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * The URI of the properties file which is used for VelocityEngine initialization.
     */
    public void setPropertiesFile(String file) {
        propertiesFile = file;
    }

    public String getPropertiesFile() {
        return propertiesFile;
    }

    public VelocityEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(getResourceUri(), newResourceUri);
        log.debug("Getting endpoint with URI: {}", newUri);
        return getCamelContext().getEndpoint(newUri, VelocityEndpoint.class);
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");

        String newResourceUri = exchange.getIn().getHeader(VelocityConstants.VELOCITY_RESOURCE_URI, String.class);
        if (newResourceUri != null) {
            exchange.getIn().removeHeader(VelocityConstants.VELOCITY_RESOURCE_URI);

            log.debug("{} set to {} creating new endpoint to handle exchange", VelocityConstants.VELOCITY_RESOURCE_URI, newResourceUri);
            VelocityEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri);
            newEndpoint.onExchange(exchange);
            return;
        }

        Reader reader;
        String content = exchange.getIn().getHeader(VelocityConstants.VELOCITY_TEMPLATE, String.class);
        if (content != null) {
            // use content from header
            reader = new StringReader(content);
            if (log.isDebugEnabled()) {
                log.debug("Velocity content read from header {} for endpoint {}", VelocityConstants.VELOCITY_TEMPLATE, getEndpointUri());
            }
            // remove the header to avoid it being propagated in the routing
            exchange.getIn().removeHeader(VelocityConstants.VELOCITY_TEMPLATE);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Velocity content read from resource {} with resourceUri: {} for endpoint {}", getResourceUri(), path, getEndpointUri());
            }
            reader = getEncoding() != null ? new InputStreamReader(getResourceAsInputStream(), getEncoding()) : new InputStreamReader(getResourceAsInputStream());
        }

        // getResourceAsInputStream also considers the content cache
        StringWriter buffer = new StringWriter();
        String logTag = getClass().getName();
        Context velocityContext = exchange.getIn().getHeader(VelocityConstants.VELOCITY_CONTEXT, Context.class);
        if (velocityContext == null) {
            Map<String, Object> variableMap = ExchangeHelper.createVariableMap(exchange);

            @SuppressWarnings("unchecked")
            Map<String, Object> supplementalMap = exchange.getIn().getHeader(VelocityConstants.VELOCITY_SUPPLEMENTAL_CONTEXT, Map.class);
            if (supplementalMap != null) {
                variableMap.putAll(supplementalMap);
            }

            velocityContext = new VelocityContext(variableMap);
        }

        // let velocity parse and generate the result in buffer
        VelocityEngine engine = getVelocityEngine();
        log.debug("Velocity is evaluating using velocity context: {}", velocityContext);
        engine.evaluate(velocityContext, buffer, logTag, reader);

        // now lets output the results to the exchange
        Message out = exchange.getOut();
        out.setBody(buffer.toString());
        out.setHeaders(exchange.getIn().getHeaders());
    }
}
