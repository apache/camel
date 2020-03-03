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
package org.apache.camel.component.xquery;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ModuleURIResolver;
import net.sf.saxon.query.StaticQueryContext;
import org.apache.camel.Component;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ProcessorEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforms the message using a XQuery template using Saxon.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = "xquery", title = "XQuery", syntax = "xquery:resourceUri", label = "transformation")
public class XQueryEndpoint extends ProcessorEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(XQueryEndpoint.class);

    private volatile XQueryBuilder xquery;

    @UriPath @Metadata(required = true)
    private String resourceUri;
    @UriParam(label = "advanced")
    private Configuration configuration;
    @UriParam(label = "advanced")
    private Map<String, Object> configurationProperties = new HashMap<>();
    @UriParam(label = "advanced")
    private StaticQueryContext staticQueryContext;
    @UriParam(label = "advanced")
    private Map<String, Object> parameters = new HashMap<>();
    @UriParam
    private Map<String, String> namespacePrefixes = new HashMap<>();
    @UriParam(defaultValue = "DOM")
    private ResultFormat resultsFormat = ResultFormat.DOM;
    @UriParam(label = "advanced")
    private Properties properties = new Properties();
    @UriParam
    private Class<?> resultType;
    @UriParam(defaultValue = "true")
    private boolean stripsAllWhiteSpace = true;
    @UriParam(label = "advanced")
    private ModuleURIResolver moduleURIResolver;
    @UriParam
    private boolean allowStAX;
    @UriParam
    private String headerName;

    public XQueryEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public String getResourceUri() {
        return resourceUri;
    }

    /**
     * The name of the template to load from classpath or file system
     */
    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * To use a custom Saxon configuration
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public Map<String, Object> getConfigurationProperties() {
        return configurationProperties;
    }

    /**
     * To set custom Saxon configuration properties
     */
    public void setConfigurationProperties(Map<String, Object> configurationProperties) {
        this.configurationProperties = configurationProperties;
    }

    public StaticQueryContext getStaticQueryContext() {
        return staticQueryContext;
    }

    /**
     * To use a custom Saxon StaticQueryContext
     */
    public void setStaticQueryContext(StaticQueryContext staticQueryContext) {
        this.staticQueryContext = staticQueryContext;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Additional parameters
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, String> getNamespacePrefixes() {
        return namespacePrefixes;
    }

    /**
     * Allows to control which namespace prefixes to use for a set of namespace mappings
     */
    public void setNamespacePrefixes(Map<String, String> namespacePrefixes) {
        this.namespacePrefixes = namespacePrefixes;
    }

    public ResultFormat getResultsFormat() {
        return resultsFormat;
    }

    /**
     * What output result to use
     */
    public void setResultsFormat(ResultFormat resultsFormat) {
        this.resultsFormat = resultsFormat;
    }

    public Properties getProperties() {
        return properties;
    }

    /**
     * Properties to configure the serialization parameters
     */
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    /**
     * What output result to use defined as a class
     */
    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public boolean isStripsAllWhiteSpace() {
        return stripsAllWhiteSpace;
    }

    /**
     * Whether to strip all whitespaces
     */
    public void setStripsAllWhiteSpace(boolean stripsAllWhiteSpace) {
        this.stripsAllWhiteSpace = stripsAllWhiteSpace;
    }

    public ModuleURIResolver getModuleURIResolver() {
        return moduleURIResolver;
    }

    /**
     * To use the custom {@link ModuleURIResolver}
     */
    public void setModuleURIResolver(ModuleURIResolver moduleURIResolver) {
        this.moduleURIResolver = moduleURIResolver;
    }

    public boolean isAllowStAX() {
        return allowStAX;
    }

    /**
     * Whether to allow using StAX mode
     */
    public void setAllowStAX(boolean allowStAX) {
        this.allowStAX = allowStAX;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * To use a Camel Message header as the input source instead of Message body.
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.debug("{} using schema resource: {}", this, resourceUri);
        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), resourceUri);

        this.xquery = XQueryBuilder.xquery(is);
        this.xquery.setConfiguration(getConfiguration());
        this.xquery.setConfigurationProperties(getConfigurationProperties());
        this.xquery.setStaticQueryContext(getStaticQueryContext());
        this.xquery.setParameters(getParameters());
        this.xquery.setNamespaces(namespacePrefixes);
        this.xquery.setResultsFormat(getResultsFormat());
        this.xquery.setProperties(getProperties());
        this.xquery.setResultType(getResultType());
        this.xquery.setStripsAllWhiteSpace(isStripsAllWhiteSpace());
        this.xquery.setAllowStAX(isAllowStAX());
        this.xquery.setHeaderName(getHeaderName());
        this.xquery.setModuleURIResolver(getModuleURIResolver());

        setProcessor(xquery);

        ServiceHelper.startService(xquery);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopService(xquery);
    }
}
