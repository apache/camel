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
package org.apache.camel.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class PropertyDescriptionsAdapter extends XmlAdapter<PropertyDefinitions, Map<String, String>> {

    @Override
    public Map<String, String> unmarshal(PropertyDefinitions value) throws Exception {
        Map<String, String> ret = new HashMap<>();

        if (value != null) {
            value.getProperties().forEach(p -> ret.put(p.getKey(), p.getValue()));
        }

        return ret;
    }

    @Override
    public PropertyDefinitions marshal(Map<String, String> value) throws Exception {
        PropertyDefinitions ret = new PropertyDefinitions();

        if (value != null) {
            final List<PropertyDefinition> properties = ret.getProperties();
            value.forEach((k, v) -> properties.add(new PropertyDefinition(k, v)));
        }

        return ret;
    }

}
