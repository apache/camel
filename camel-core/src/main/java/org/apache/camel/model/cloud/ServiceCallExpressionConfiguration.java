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

package org.apache.camel.model.cloud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.cloud.ServiceExpressionFactory;
import org.apache.camel.impl.cloud.DefaultServiceCallExpression;
import org.apache.camel.impl.cloud.ServiceCallConstants;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

@Metadata(label = "routing,cloud")
@XmlRootElement(name = "serviceExpression")
@XmlAccessorType(XmlAccessType.FIELD)
public class ServiceCallExpressionConfiguration extends IdentifiedType implements ServiceExpressionFactory {
    @XmlTransient
    private final ServiceCallDefinition parent;
    @XmlTransient
    private final String factoryKey;
    @XmlElement(name = "properties") @Metadata(label = "advanced")
    private List<PropertyDefinition> properties;
    @XmlAttribute @Metadata(defaultValue = ServiceCallConstants.SERVICE_HOST)
    private String hostHeader = ServiceCallConstants.SERVICE_HOST;
    @XmlAttribute @Metadata(defaultValue = ServiceCallConstants.SERVICE_PORT)
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

    public List<PropertyDefinition> getProperties() {
        return properties;
    }

    /**
     * Set client properties to use.
     * <p/>
     * These properties are specific to what service call implementation are in
     * use. For example if using ribbon, then the client properties are define
     * in com.netflix.client.config.CommonClientConfigKey.
     */
    public void setProperties(List<PropertyDefinition> properties) {
        this.properties = properties;
    }

    /**
     * Adds a custom property to use.
     * <p/>
     * These properties are specific to what service call implementation are in
     * use. For example if using ribbon, then the client properties are define
     * in com.netflix.client.config.CommonClientConfigKey.
     */
    public ServiceCallExpressionConfiguration property(String key, String value) {
        if (properties == null) {
            properties = new ArrayList<>();
        }
        PropertyDefinition prop = new PropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);
        properties.add(prop);
        return this;
    }

    protected Map<String, String> getPropertiesAsMap(CamelContext camelContext) throws Exception {
        Map<String, String> answer;

        if (properties == null || properties.isEmpty()) {
            answer = Collections.emptyMap();
        } else {
            answer = new HashMap<>();
            for (PropertyDefinition prop : properties) {
                // support property placeholders
                String key = CamelContextHelper.parseText(camelContext, prop.getKey());
                String value = CamelContextHelper.parseText(camelContext, prop.getValue());
                answer.put(key, value);
            }
        }

        return answer;
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
        if (expressionType != null && answer == null) {
            return expressionType.createExpression(camelContext);
        }

        if (factoryKey != null) {
            // First try to find the factory from the registry.
            ServiceExpressionFactory factory = CamelContextHelper.lookup(camelContext, factoryKey, ServiceExpressionFactory.class);
            if (factory != null) {
                // If a factory is found in the registry do not re-configure it as
                // it should be pre-configured.
                answer = factory.newInstance(camelContext);
            } else {

                Class<?> type;
                try {
                    // Then use Service factory.
                    type = camelContext.getFactoryFinder(ServiceCallDefinitionConstants.RESOURCE_PATH).findClass(factoryKey);
                } catch (Exception e) {
                    throw new NoFactoryAvailableException(ServiceCallDefinitionConstants.RESOURCE_PATH + factoryKey, e);
                }

                if (type != null) {
                    if (ServiceExpressionFactory.class.isAssignableFrom(type)) {
                        factory = (ServiceExpressionFactory) camelContext.getInjector().newInstance(type);
                    } else {
                        throw new IllegalArgumentException(
                            "Resolving Expression: " + factoryKey + " detected type conflict: Not a ExpressionFactory implementation. Found: " + type.getName());
                    }
                }

                try {
                    Map<String, Object> parameters = new HashMap<>();
                    IntrospectionSupport.getProperties(this, parameters, null, false);

                    parameters.replaceAll(
                        (k, v) -> {
                            if (v instanceof String) {
                                try {
                                    v = camelContext.resolvePropertyPlaceholders((String) v);
                                } catch (Exception e) {
                                    throw new IllegalArgumentException(
                                        String.format("Exception while resolving %s (%s)", k, v.toString()),
                                        e
                                    );
                                }
                            }

                            return v;
                        }
                    );

                    // Convert properties to Map<String, String>
                    parameters.put("properties", getPropertiesAsMap(camelContext));

                    postProcessFactoryParameters(camelContext, parameters);

                    IntrospectionSupport.setProperties(factory, parameters);

                    answer = factory.newInstance(camelContext);
                } catch (Exception e) {
                    throw new IllegalArgumentException(e);
                }
            }
        } else {
            answer = new DefaultServiceCallExpression(
                ObjectHelper.notNull(hostHeader, "hostHeader"),
                ObjectHelper.notNull(portHeader, "portHeader")
            );
        }

        return answer;
    }

    // *************************************************************************
    // Utilities
    // *************************************************************************

    protected void postProcessFactoryParameters(CamelContext camelContext, Map<String, Object> parameters) throws Exception  {
    }
}
