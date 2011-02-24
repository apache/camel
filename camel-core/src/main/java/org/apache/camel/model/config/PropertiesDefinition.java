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
package org.apache.camel.model.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents the XML type for &lt;properties&gt;.
 */
@XmlRootElement(name = "properties")
@XmlAccessorType(XmlAccessType.FIELD)
public class PropertiesDefinition {
    @XmlElement(name = "property")
    private List<PropertyDefinition> properties;
    
    public PropertiesDefinition() {
    }
    
    public void setProperties(List<PropertyDefinition> properties) {
        this.properties = properties;
    }
    
    public List<PropertyDefinition> getProperties() {
        return properties;
    }
    
    /***
     * @return A Map of the contained DataFormatType's indexed by id.
     */
    public Map<String, String> asMap() {
        Map<String, String> propertiesAsMap = new HashMap<String, String>();
        for (PropertyDefinition propertyType : getProperties()) {
            propertiesAsMap.put(propertyType.getKey(), propertyType.getValue());
        }
        return propertiesAsMap;
    }

}
