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
package org.apache.camel.component.cxf;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.cxf.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://camel.apache.org/cxf.html">CXF Component</a>
 */
public class CxfComponent extends HeaderFilterStrategyComponent implements SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(CxfComponent.class);

    @Metadata(label = "advanced")
    private Boolean allowStreaming;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public CxfComponent() {
        super(CxfEndpoint.class);
    }

    public CxfComponent(CamelContext context) {
        super(context, CxfEndpoint.class);
    }

    /**
     * This option controls whether the CXF component, when running in PAYLOAD mode, will DOM parse the incoming messages
     * into DOM Elements or keep the payload as a javax.xml.transform.Source object that would allow streaming in some cases.
     */
    public void setAllowStreaming(Boolean allowStreaming) {
        this.allowStreaming = allowStreaming;
    }

    public Boolean isAllowStreaming() {
        return allowStreaming;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    /**
     * Create a {@link CxfEndpoint} which, can be a Spring bean endpoint having
     * URI format cxf:bean:<i>beanId</i> or transport address endpoint having URI format
     * cxf://<i>transportAddress</i>.
     */
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        CxfEndpoint result;

        Object value = parameters.remove("setDefaultBus");
        if (value != null) {
            LOG.warn("The option setDefaultBus is @deprecated, use name defaultBus instead");
            if (!parameters.containsKey("defaultBus")) {
                parameters.put("defaultBus", value);
            }
        }

        if (allowStreaming != null && !parameters.containsKey("allowStreaming")) {
            parameters.put("allowStreaming", Boolean.toString(allowStreaming));
        }

        if (remaining.startsWith(CxfConstants.SPRING_CONTEXT_ENDPOINT)) {
            // Get the bean from the Spring context
            String beanId = remaining.substring(CxfConstants.SPRING_CONTEXT_ENDPOINT.length());
            if (beanId.startsWith("//")) {
                beanId = beanId.substring(2);
            }

            result = createCxfSpringEndpoint(beanId);
            // need to check the CamelContext value
            if (getCamelContext().equals(result.getCamelContext())) {
                result.setCamelContext(getCamelContext());
            }
            result.setBeanId(beanId);

        } else {
            // endpoint URI does not specify a bean
            result = createCxfEndpoint(remaining);
        }

        if (result.getCamelContext() == null) {
            result.setCamelContext(getCamelContext());
        }
        setEndpointHeaderFilterStrategy(result);
        setProperties(result, parameters);

        // extract the properties.xxx and set them as properties
        Map<String, Object> properties = IntrospectionSupport.extractProperties(parameters, "properties.");
        if (properties != null) {
            result.setProperties(properties);
        }
        if (result.getProperties() != null) {
            // set the properties of MTOM
            result.setMtomEnabled(Boolean.valueOf((String) result.getProperties().get(Message.MTOM_ENABLED)));
        }

        // use global ssl config if set
        if (result.getSslContextParameters() == null) {
            result.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return result;
    }

    protected CxfEndpoint createCxfSpringEndpoint(String beanId) throws Exception {
        return CamelContextHelper.mandatoryLookup(getCamelContext(), beanId, CxfEndpoint.class);
    }

    protected CxfEndpoint createCxfEndpoint(String remaining) {
        return new CxfEndpoint(remaining, this);
    }

    @Override
    protected void afterConfiguration(String uri, String remaining, Endpoint endpoint, Map<String, Object> parameters) throws Exception {
        CxfEndpoint cxfEndpoint = (CxfEndpoint) endpoint;
        cxfEndpoint.updateEndpointUri(uri);
    }

}
