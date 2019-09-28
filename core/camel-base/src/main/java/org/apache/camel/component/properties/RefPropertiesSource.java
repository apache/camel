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
package org.apache.camel.component.properties;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.OrderedProperties;

public class RefPropertiesSource implements LocationPropertiesSource  {

    private final PropertiesComponent propertiesComponent;
    private final PropertiesLocation location;

    public RefPropertiesSource(PropertiesComponent propertiesComponent, PropertiesLocation location) {
        this.propertiesComponent = propertiesComponent;
        this.location = location;
    }

    @Override
    public String getName() {
        return "RefPropertiesSource[" + getLocation().getPath() + "]";
    }

    @Override
    public PropertiesLocation getLocation() {
        return location;
    }

    @Override
    public String getProperty(String name) {
        // this will lookup the property on-demand
        Properties properties = lookupPropertiesInRegistry(propertiesComponent, location);
        if (properties != null) {
            return properties.getProperty(name);
        } else {
            return null;
        }
    }

    protected Properties lookupPropertiesInRegistry(PropertiesComponent propertiesComponent, PropertiesLocation location) {
        String path = location.getPath();
        Properties answer = null;

        Object obj = propertiesComponent.getCamelContext().getRegistry().lookupByName(path);
        if (obj instanceof Properties) {
            answer = (Properties) obj;
        } else if (obj instanceof Map) {
            answer = new OrderedProperties();
            answer.putAll((Map<?, ?>) obj);
        } else if (!propertiesComponent.isIgnoreMissingLocation() && !location.isOptional()) {
            throw RuntimeCamelException.wrapRuntimeCamelException(new FileNotFoundException("Properties " + path + " not found in registry"));
        }

        return answer;
    }
}
