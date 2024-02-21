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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.CamelContext;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.PropertyDefinition;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.ExtendedPropertyConfigurerGetter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PluginHelper;

@XmlType(name = "serviceCallConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
@Configurer
@Deprecated
public abstract class ServiceCallConfiguration extends IdentifiedType {
    @XmlElement(name = "properties")
    @Metadata(label = "advanced")
    protected List<PropertyDefinition> properties;

    // *************************************************************************
    //
    // *************************************************************************

    public List<PropertyDefinition> getProperties() {
        return properties;
    }

    /**
     * Set client properties to use.
     * <p/>
     * These properties are specific to what service call implementation are in use. For example if using a different
     * one, then the client properties are defined according to the specific service in use.
     */
    public void setProperties(List<PropertyDefinition> properties) {
        this.properties = properties;
    }

    /**
     * Adds a custom property to use.
     * <p/>
     * These properties are specific to what service call implementation are in use. For example if using a different
     * one, then the client properties are defined according to the specific service in use.
     */
    public ServiceCallConfiguration property(String key, String value) {
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

    protected Map<String, Object> getConfiguredOptions(CamelContext context, Object target) {
        Map<String, Object> answer = new HashMap<>();

        PropertyConfigurer configurer = PluginHelper.getConfigurerResolver(context)
                .resolvePropertyConfigurer(target.getClass().getName(), context);
        // use reflection free configurer (if possible)
        if (configurer instanceof ExtendedPropertyConfigurerGetter) {
            ExtendedPropertyConfigurerGetter getter = (ExtendedPropertyConfigurerGetter) configurer;
            Set<String> all = getter.getAllOptions(target).keySet();
            for (String name : all) {
                Object value = getter.getOptionValue(target, name, true);
                if (value != null) {
                    // lower case the first letter which is what the properties map expects
                    String key = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                    answer.put(key, value);
                }
            }
        } else {
            PluginHelper.getBeanIntrospection(context).getProperties(target, answer,
                    null, false);
        }

        return answer;
    }

    // *************************************************************************
    // Utilities
    // *************************************************************************

    protected void postProcessFactoryParameters(CamelContext camelContext, Map<String, Object> parameters) throws Exception {
    }
}
