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
package org.apache.camel.component.cxf.jaxrs;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.HeaderFilterStrategyComponent;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.util.CastUtils;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://camel.apache.org/cxfrs.html">CXF RS Component</a>
 */
@Component("cxfrs")
public class CxfRsComponent extends HeaderFilterStrategyComponent implements SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(CxfRsComponent.class);

    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public CxfRsComponent() {
    }

    public CxfRsComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // lookup
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        CxfRsEndpoint answer;

        Object value = parameters.remove("setDefaultBus");
        if (value != null) {
            LOG.warn("The option setDefaultBus is @deprecated, use name defaultBus instead");
            if (!parameters.containsKey("defaultBus")) {
                parameters.put("defaultBus", value);
            }
        }

        if (remaining.startsWith(CxfConstants.SPRING_CONTEXT_ENDPOINT)) {
            // Get the bean from the Spring context
            String beanId = remaining.substring(CxfConstants.SPRING_CONTEXT_ENDPOINT.length());
            if (beanId.startsWith("//")) {
                beanId = beanId.substring(2);
            }

            AbstractJAXRSFactoryBean bean = CamelContextHelper.mandatoryLookup(getCamelContext(), beanId,
                    AbstractJAXRSFactoryBean.class);

            CxfRsEndpointFactoryBean factory = null;
            if (bean.getClass().getName().contains("blueprint")) {
                // use blueprint
                Class<CxfRsEndpointFactoryBean> clazz = getCamelContext().getClassResolver().resolveMandatoryClass(
                        "org.apache.camel.component.cxf.jaxrs.blueprint.CxfRsBlueprintEndpointFactoryBean",
                        CxfRsEndpointFactoryBean.class);
                factory = getCamelContext().getInjector().newInstance(clazz);
            } else {
                try {
                    //try spring first
                    Class<CxfRsEndpointFactoryBean> clazz = getCamelContext().getClassResolver()
                            .resolveMandatoryClass("org.apache.camel.component.cxf.spring.jaxrs.SpringCxfRsEndpointFactoryBean",
                                    CxfRsEndpointFactoryBean.class);
                    factory = getCamelContext().getInjector().newInstance(clazz);
                } catch (Exception ex) {
                    factory = new DefaultCxfRsEndpointFactoryBean();
                }
            }
            answer = factory.createEndpoint(this, remaining, bean);

            // Apply Spring bean properties (including # notation referenced bean).  Note that the
            // Spring bean properties values can be overridden by property defined in URI query.
            // The super class (DefaultComponent) will invoke "setProperties" after this method
            // with to apply properties defined by URI query.
            if (bean.getProperties() != null) {
                Map<String, Object> copy = new HashMap<>(bean.getProperties());
                setProperties(answer, copy);
            }
            // setup the skipFaultLogging

            answer.setBeanId(beanId);

        } else {
            // endpoint URI does not specify a bean
            answer = new CxfRsEndpoint(remaining, this);
        }

        String resourceClass = getAndRemoveParameter(parameters, "resourceClass", String.class);
        if (resourceClass != null) {
            Class<?> clazz = getCamelContext().getClassResolver().resolveMandatoryClass(resourceClass);
            answer.addResourceClass(clazz);
        }

        String resourceClasses = getAndRemoveParameter(parameters, "resourceClasses", String.class);
        Iterator<?> it = ObjectHelper.createIterator(resourceClasses);
        while (it.hasNext()) {
            String name = (String) it.next();
            Class<?> clazz = getCamelContext().getClassResolver().resolveMandatoryClass(name);
            answer.addResourceClass(clazz);
        }

        setProperties(answer, parameters);
        Map<String, String> params = CastUtils.cast(parameters);
        answer.setParameters(params);
        setEndpointHeaderFilterStrategy(answer);

        // use global ssl config if set
        if (answer.getSslContextParameters() == null) {
            answer.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return answer;
    }

    @Override
    protected void afterConfiguration(String uri, String remaining, Endpoint endpoint, Map<String, Object> parameters)
            throws Exception {
        CxfRsEndpoint cxfRsEndpoint = (CxfRsEndpoint) endpoint;
        cxfRsEndpoint.updateEndpointUri(uri);
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
}
