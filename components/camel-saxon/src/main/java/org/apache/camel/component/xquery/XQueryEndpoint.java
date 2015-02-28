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
package org.apache.camel.component.xquery;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ModuleURIResolver;
import net.sf.saxon.query.StaticQueryContext;
import org.apache.camel.Component;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ResourceHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "xquery", syntax = "xquery:resourceUri", label = "transformation")
public class XQueryEndpoint extends ProcessorEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(XQueryEndpoint.class);

    private volatile XQueryBuilder xquery;

    @UriPath @Metadata(required = "true")
    private String resourceUri;
    @UriParam
    private Configuration configuration;
    @UriParam
    private StaticQueryContext staticQueryContext;
    @UriParam
    private Map<String, Object> parameters = new HashMap<String, Object>();
    @UriParam
    private Map<String, String> namespacePrefixes = new HashMap<String, String>();
    @UriParam(defaultValue = "DOM")
    private ResultFormat resultsFormat = ResultFormat.DOM;
    @UriParam
    private Properties properties = new Properties();
    @UriParam
    private Class<?> resultType;
    @UriParam(defaultValue = "true")
    private boolean stripsAllWhiteSpace = true;
    @UriParam
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

    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public StaticQueryContext getStaticQueryContext() {
        return staticQueryContext;
    }

    public void setStaticQueryContext(StaticQueryContext staticQueryContext) {
        this.staticQueryContext = staticQueryContext;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, String> getNamespacePrefixes() {
        return namespacePrefixes;
    }

    public void setNamespacePrefixes(Map<String, String> namespacePrefixes) {
        this.namespacePrefixes = namespacePrefixes;
    }

    public ResultFormat getResultsFormat() {
        return resultsFormat;
    }

    public void setResultsFormat(ResultFormat resultsFormat) {
        this.resultsFormat = resultsFormat;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public boolean isStripsAllWhiteSpace() {
        return stripsAllWhiteSpace;
    }

    public void setStripsAllWhiteSpace(boolean stripsAllWhiteSpace) {
        this.stripsAllWhiteSpace = stripsAllWhiteSpace;
    }

    public ModuleURIResolver getModuleURIResolver() {
        return moduleURIResolver;
    }

    public void setModuleURIResolver(ModuleURIResolver moduleURIResolver) {
        this.moduleURIResolver = moduleURIResolver;
    }

    public boolean isAllowStAX() {
        return allowStAX;
    }

    public void setAllowStAX(boolean allowStAX) {
        this.allowStAX = allowStAX;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.debug("{} using schema resource: {}", this, resourceUri);
        URL url = ResourceHelper.resolveMandatoryResourceAsUrl(getCamelContext().getClassResolver(), resourceUri);
        this.xquery = XQueryBuilder.xquery(url);

        xquery.setConfiguration(getConfiguration());
        xquery.setStaticQueryContext(getStaticQueryContext());
        xquery.setParameters(getParameters());
        xquery.setNamespaces(namespacePrefixes);
        xquery.setResultsFormat(getResultsFormat());
        xquery.setProperties(getProperties());
        xquery.setResultType(getResultType());
        xquery.setStripsAllWhiteSpace(isStripsAllWhiteSpace());
        xquery.setAllowStAX(isAllowStAX());
        xquery.setHeaderName(getHeaderName());

        setProcessor(xquery);

        ServiceHelper.startService(xquery);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopService(xquery);
    }
}
