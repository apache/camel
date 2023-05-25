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
package org.apache.camel.model.app;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class BeanPropertiesAdapter extends XmlAdapter<BeanPropertiesDefinition, Map<String, Object>> {

    @Override
    public Map<String, Object> unmarshal(BeanPropertiesDefinition v) {
        if (v == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (BeanPropertyDefinition pd : v.getProperties()) {
            if (pd.getProperties() != null) {
                result.put(pd.getKey(), unmarshal(pd.getProperties()));
            } else {
                result.put(pd.getKey(), pd.getValue());
            }
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BeanPropertiesDefinition marshal(Map<String, Object> v) {
        if (v == null) {
            return null;
        }
        final BeanPropertyDefinition[] result = new BeanPropertyDefinition[v.size()];
        int pos = 0;
        for (Map.Entry<String, Object> entry : v.entrySet()) {
            String k = entry.getKey();
            Object value = entry.getValue();
            BeanPropertyDefinition pd = new BeanPropertyDefinition();
            pd.setKey(k);
            if (value instanceof Map) {
                pd.setProperties(marshal((Map<String, Object>) value));
            } else {
                pd.setValue(value.toString());
            }
            result[pos++] = pd;
        }
        BeanPropertiesDefinition propertiesDefinition = new BeanPropertiesDefinition();
        propertiesDefinition.setProperties(Arrays.asList(result));
        return propertiesDefinition;
    }

}
