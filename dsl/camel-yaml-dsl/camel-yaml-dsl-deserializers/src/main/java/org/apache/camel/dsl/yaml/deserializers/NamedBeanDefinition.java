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
package org.apache.camel.dsl.yaml.deserializers;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.yaml.common.YamlSupport;
import org.apache.camel.support.PropertyBindingSupport;

public class NamedBeanDefinition {
    private String name;
    private String type;
    private Map<String, Object> properties;

    public NamedBeanDefinition() {
    }

    public NamedBeanDefinition(String name, String type, Map<String, Object> properties) {
        this.name = name;
        this.type = type;
        this.properties = properties;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Object newInstance(CamelContext context) throws Exception {
        final Object target = PropertyBindingSupport.resolveBean(context, this.type);

        if (this.properties != null && !this.properties.isEmpty()) {
            YamlSupport.setPropertiesOnTarget(context, target, this.properties);
        }

        return target;
    }
}
