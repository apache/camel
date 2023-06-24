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

import net.sf.saxon.Configuration;
import org.apache.camel.Endpoint;
import org.apache.camel.component.xslt.XsltComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.EndpointHelper;

/**
 * The XSLT Component is for performing XSLT transformations of messages using Saxon.
 */
@Component("xslt-saxon")
public class XsltSaxonComponent extends XsltComponent {

    @Metadata(label = "advanced")
    private Configuration saxonConfiguration;
    @Metadata(label = "advanced")
    private Map<String, Object> saxonConfigurationProperties = new HashMap<>();
    @Metadata(label = "advanced", javaType = "java.lang.String")
    private List<Object> saxonExtensionFunctions;
    @Metadata(label = "advanced", defaultValue = "true")
    private boolean secureProcessing = true;

    public List<Object> getSaxonExtensionFunctions() {
        return saxonExtensionFunctions;
    }

    /**
     * Allows you to use a custom net.sf.saxon.lib.ExtensionFunctionDefinition. You would need to add camel-saxon to the
     * classpath. The function is looked up in the registry, where you can use commas to separate multiple values to
     * lookup.
     */
    public void setSaxonExtensionFunctions(List<Object> extensionFunctions) {
        this.saxonExtensionFunctions = extensionFunctions;
    }

    /**
     * Allows you to use a custom net.sf.saxon.lib.ExtensionFunctionDefinition. You would need to add camel-saxon to the
     * classpath. The function is looked up in the registry, where you can use commas to separate multiple values to
     * lookup.
     */
    public void setSaxonExtensionFunctions(String extensionFunctions) {
        this.saxonExtensionFunctions = EndpointHelper.resolveReferenceListParameter(
                getCamelContext(),
                extensionFunctions,
                Object.class);
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

    @Override
    protected XsltSaxonEndpoint createXsltEndpoint(String uri) {
        return new XsltSaxonEndpoint(uri, this);
    }

    @Override
    protected void configureEndpoint(Endpoint endpoint, final String remaining, Map<String, Object> parameters)
            throws Exception {
        XsltSaxonEndpoint saxon = (XsltSaxonEndpoint) endpoint;
        saxon.setContentCache(isContentCache());
        saxon.setSaxonConfiguration(saxonConfiguration);
        saxon.setSaxonConfigurationProperties(saxonConfigurationProperties);
        saxon.setSaxonExtensionFunctions(saxonExtensionFunctions);
        saxon.setSecureProcessing(secureProcessing);

        super.configureEndpoint(endpoint, remaining, parameters);
    }
}
