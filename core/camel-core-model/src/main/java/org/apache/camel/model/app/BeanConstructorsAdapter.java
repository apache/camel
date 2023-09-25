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

public class BeanConstructorsAdapter extends XmlAdapter<BeanConstructorsDefinition, Map<Integer, Object>> {

    @Override
    public Map<Integer, Object> unmarshal(BeanConstructorsDefinition v) {
        if (v == null) {
            return null;
        }
        int counter = 0;
        Map<Integer, Object> result = new LinkedHashMap<>();
        for (BeanConstructorDefinition pd : v.getConstructors()) {
            Integer idx = pd.getIndex();
            if (idx == null) {
                idx = counter;
                counter++;
            }
            result.put(idx, pd.getValue());
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BeanConstructorsDefinition marshal(Map<Integer, Object> v) {
        if (v == null) {
            return null;
        }
        final BeanConstructorDefinition[] result = new BeanConstructorDefinition[v.size()];
        int pos = 0;
        for (Map.Entry<Integer, Object> entry : v.entrySet()) {
            Integer idx = entry.getKey();
            Object value = entry.getValue();
            BeanConstructorDefinition pd = new BeanConstructorDefinition();
            if (idx != null) {
                pd.setIndex(idx);
            }
            pd.setValue(value.toString());
            result[pos++] = pd;
        }
        BeanConstructorsDefinition def = new BeanConstructorsDefinition();
        def.setConstructors(Arrays.asList(result));
        return def;
    }

}
