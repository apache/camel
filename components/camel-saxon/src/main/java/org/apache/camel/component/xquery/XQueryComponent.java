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

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ModuleURIResolver;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ResourceHelper;

/**
 * An <a href="http://camel.apache.org/xquery.html">XQuery Component</a>
 * for performing transforming messages
 */
public class XQueryComponent extends UriEndpointComponent {

    @Metadata(label = "advanced")
    private ModuleURIResolver moduleURIResolver = new XQueryModuleURIResolver(this);
    @Metadata(label = "advanced")
    private Configuration configuration;
    @Metadata(label = "advanced")
    private Map<String, Object> configurationProperties = new HashMap<>();

    public XQueryComponent() {
        super(XQueryEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        XQueryEndpoint answer = new XQueryEndpoint(uri, this);
        answer.setConfiguration(configuration);
        answer.setConfigurationProperties(getConfigurationProperties());
        setProperties(answer, parameters);

        answer.setResourceUri(remaining);
        answer.setModuleURIResolver(getModuleURIResolver());

        return answer;
    }

    public URL resolveModuleResource(String uri) throws Exception {
        return ResourceHelper.resolveMandatoryResourceAsUrl(getCamelContext().getClassResolver(), uri);
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
}
