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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

public class MapOfMapsConverter implements Converter {

    private static final String ATTRIBUTES_PROPERTY = "attributes";

    @Override
    public boolean canConvert(final Class type) {
        return true;
    }

    @Override
    public void marshal(final Object source, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        context.convertAnother(source);
    }

    @Override
    public Object unmarshal(final HierarchicalStreamReader reader, final UnmarshallingContext context) {
        final Map<String, Object> ret = new HashMap<>();

        while (reader.hasMoreChildren()) {
            readMap(reader, ret);
        }

        return ret;
    }

    Object readMap(final HierarchicalStreamReader reader, final Map<String, Object> map) {
        if (reader.hasMoreChildren()) {
            reader.moveDown();
            final String key = reader.getNodeName();

            final Map<String, String> attributes = new HashMap<>();
            final Iterator attributeNames = reader.getAttributeNames();
            while (attributeNames.hasNext()) {
                final String attributeName = (String)attributeNames.next();
                attributes.put(attributeName, reader.getAttribute(attributeName));
            }

            Object nested = readMap(reader, new HashMap<>());
            if (!attributes.isEmpty()) {
                if (nested instanceof String) {
                    final Map<Object, Object> newNested = new HashMap<>();
                    newNested.put(key, nested);
                    newNested.put(ATTRIBUTES_PROPERTY, attributes);
                    nested = newNested;
                } else {
                    @SuppressWarnings("unchecked")
                    final Map<String, Object> nestedMap = (Map<String, Object>)nested;
                    nestedMap.put(ATTRIBUTES_PROPERTY, attributes);
                }
            }

            map.put(key, nested);
            reader.moveUp();

            readMap(reader, map);
        } else {
            return reader.getValue();
        }

        return map;
    }

}
