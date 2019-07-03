/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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

public class RefPropertiesSource extends LocationPropertiesSourceSupport  {

    public RefPropertiesSource(PropertiesComponent propertiesComponent, PropertiesLocation location, boolean ignoreMissingLocation) {
        super(propertiesComponent, location, ignoreMissingLocation);
    }

    @Override
    public String getName() {
        return "RefPropertiesSource[" + getLocation().getPath() + "]";
    }

    protected Properties loadPropertiesFromLocation(PropertiesComponent propertiesComponent, boolean ignoreMissingLocation, PropertiesLocation location) {
        String path = location.getPath();
        Properties answer;
        try {
            answer = propertiesComponent.getCamelContext().getRegistry().lookupByNameAndType(path, Properties.class);
        } catch (Exception ex) {
            // just look up the Map as a fault back
            Map map = propertiesComponent.getCamelContext().getRegistry().lookupByNameAndType(path, Map.class);
            answer = new OrderedProperties();
            answer.putAll(map);
        }
        if (answer == null && (!ignoreMissingLocation && !location.isOptional())) {
            throw RuntimeCamelException.wrapRuntimeCamelException(new FileNotFoundException("Properties " + path + " not found in registry"));
        }
        return answer != null ? answer : new OrderedProperties();
    }

}
