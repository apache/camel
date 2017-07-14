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
package org.apache.camel.impl;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.InvalidPropertyException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.IntrospectionSupport;

/**
 * Default implementation for components which do not inherit from {@link UriEndpointComponent} and
 * do not have Endpoint classes annotated with {@link org.apache.camel.spi.UriEndpoint}
 */
@Deprecated
public class DefaultComponentConfiguration extends ComponentConfigurationSupport {

    public DefaultComponentConfiguration(Component component) {
        super(component);
    }

    @Override
    public Object getEndpointParameter(Endpoint endpoint, String name) throws RuntimeCamelException {
        try {
            return IntrospectionSupport.getProperty(endpoint, name);
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to get property " + name + " on endpoint " + endpoint + " due to " + e.getMessage(), e);
        }
    }

    @Override
    public void setEndpointParameter(Endpoint endpoint, String name, Object value) throws RuntimeCamelException {
        boolean answer;
        try {
            answer = IntrospectionSupport.setProperty(endpoint, name, value);
        } catch (Exception e) {
            throw new RuntimeCamelException(
                    "Failed to set property " + name + " with value " + value + " on endpoint " + endpoint + " due to " + e.getMessage(), e);
        }
        if (!answer) {
            throw new InvalidPropertyException(endpoint, name);
        }
    }

    /**
     * Since we have no parameter metadata lets just return parameter configurations for each parameter we
     * have right now.
     *
     * @return configurations for each current property value
     */
    @Override
    public SortedMap<String, ParameterConfiguration> getParameterConfigurationMap() {
        SortedMap<String, ParameterConfiguration> answer = new TreeMap<String, ParameterConfiguration>();
        Set<Map.Entry<String, Object>> entries = getParameters().entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            String name = entry.getKey();
            Object value = entry.getValue();
            Class<?> type = (value != null) ? value.getClass() : String.class;
            answer.put(name, new ParameterConfiguration(name, type));
        }
        return answer;
    }

}
