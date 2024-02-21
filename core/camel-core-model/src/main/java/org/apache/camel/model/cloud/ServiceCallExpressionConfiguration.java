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
package org.apache.camel.model.cloud;

import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.cloud.ServiceCallConstants;
import org.apache.camel.cloud.ServiceExpressionFactory;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PropertyBindingSupport;

@Metadata(label = "routing,cloud")
@XmlRootElement(name = "serviceExpression")
@XmlAccessorType(XmlAccessType.FIELD)
@Configurer(extended = true)
@Deprecated
public class ServiceCallExpressionConfiguration extends ServiceCallConfiguration implements ServiceExpressionFactory {
    @XmlTransient
    private final ServiceCallDefinition parent;
    @XmlTransient
    private final String factoryKey;
    @XmlAttribute
    @Metadata(defaultValue = ServiceCallConstants.SERVICE_HOST)
    private String hostHeader = ServiceCallConstants.SERVICE_HOST;
    @XmlAttribute
    @Metadata(defaultValue = ServiceCallConstants.SERVICE_PORT)
    private String portHeader = ServiceCallConstants.SERVICE_PORT;
    @XmlElementRef(required = false)
    private ExpressionDefinition expressionType;
    @XmlTransient
    private Expression expression;

    public ServiceCallExpressionConfiguration() {
        this(null, null);
    }

    public ServiceCallExpressionConfiguration(ServiceCallDefinition parent, String factoryKey) {
        this.parent = parent;
        this.factoryKey = factoryKey;
    }

    public ServiceCallDefinition end() {
        return this.parent;
    }

    public ProcessorDefinition<?> endParent() {
        return this.parent.end();
    }

    // *************************************************************************
    //
    // *************************************************************************

    @Override
    public ServiceCallServiceChooserConfiguration property(String key, String value) {
        return (ServiceCallServiceChooserConfiguration) super.property(key, value);
    }

    public String getHostHeader() {
        return hostHeader;
    }

    /**
     * The header that holds the service host information, default ServiceCallConstants.SERVICE_HOST
     */
    public void setHostHeader(String hostHeader) {
        this.hostHeader = hostHeader;
    }

    public String getPortHeader() {
        return portHeader;
    }

    /**
     * The header that holds the service port information, default ServiceCallConstants.SERVICE_PORT
     */
    public void setPortHeader(String portHeader) {
        this.portHeader = portHeader;
    }

    public ExpressionDefinition getExpressionType() {
        return expressionType;
    }

    public void setExpressionType(ExpressionDefinition expressionType) {
        this.expressionType = expressionType;
    }

    public Expression getExpression() {
        return expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    /**
     * The header that holds the service host information, default ServiceCallConstants.SERVICE_HOST
     */
    public ServiceCallExpressionConfiguration hostHeader(String hostHeader) {
        setHostHeader(hostHeader);
        return this;
    }

    /**
     * The header that holds the service port information, default ServiceCallConstants.SERVICE_PORT
     */
    public ServiceCallExpressionConfiguration portHeader(String portHeader) {
        setPortHeader(portHeader);
        return this;
    }

    public ServiceCallExpressionConfiguration expressionType(ExpressionDefinition expressionType) {
        setExpressionType(expressionType);
        return this;
    }

    public ServiceCallExpressionConfiguration expression(Expression expression) {
        setExpression(expression);
        return this;
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public Expression newInstance(CamelContext camelContext) throws Exception {
        Expression answer = getExpression();
        if (answer != null) {
            return answer;
        }

        ExpressionDefinition expressionType = getExpressionType();
        if (expressionType != null) {
            return expressionType.createExpression(camelContext);
        }

        if (factoryKey != null) {
            // First try to find the factory from the registry.
            ServiceExpressionFactory factory
                    = CamelContextHelper.lookup(camelContext, factoryKey, ServiceExpressionFactory.class);
            if (factory != null) {
                // If a factory is found in the registry do not re-configure it
                // as
                // it should be pre-configured.
                answer = factory.newInstance(camelContext);
            } else {

                Class<?> type;
                try {
                    // Then use Service factory.
                    type = camelContext.getCamelContextExtension()
                            .getFactoryFinder(ServiceCallDefinitionConstants.RESOURCE_PATH).findClass(factoryKey).orElse(null);
                } catch (Exception e) {
                    throw new NoFactoryAvailableException(ServiceCallDefinitionConstants.RESOURCE_PATH + factoryKey, e);
                }

                if (type != null) {
                    if (ServiceExpressionFactory.class.isAssignableFrom(type)) {
                        factory = (ServiceExpressionFactory) camelContext.getInjector().newInstance(type, false);
                    } else {
                        throw new IllegalArgumentException(
                                "Resolving Expression: " + factoryKey
                                                           + " detected type conflict: Not a ExpressionFactory implementation. Found: "
                                                           + type.getName());
                    }
                }

                try {
                    Map<String, Object> parameters = getConfiguredOptions(camelContext, this);

                    parameters.replaceAll((k, v) -> {
                        if (v instanceof String) {
                            try {
                                v = camelContext.resolvePropertyPlaceholders((String) v);
                            } catch (Exception e) {
                                throw new IllegalArgumentException(
                                        String.format("Exception while resolving %s (%s)", k, v), e);
                            }
                        }

                        return v;
                    });

                    // Convert properties to Map<String, String>
                    Map<String, String> map = getPropertiesAsMap(camelContext);
                    if (map != null && !map.isEmpty()) {
                        parameters.put("properties", map);
                    }

                    postProcessFactoryParameters(camelContext, parameters);

                    PropertyBindingSupport.build().bind(camelContext, factory, parameters);

                    answer = factory.newInstance(camelContext);
                } catch (Exception e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }

        return answer;
    }

}
