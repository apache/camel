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

import java.util.Map;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.ConverterLookup;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

public final class RichInputConverter implements Converter {

    private final ConverterLookup converterLookup;

    public RichInputConverter(final ConverterLookup converterLookup) {
        this.converterLookup = converterLookup;
    }

    @Override
    public boolean canConvert(final Class type) {
        return true;
    }

    @Override
    public void marshal(final Object source, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        if (source instanceof Map) {
            @SuppressWarnings("unchecked")
            final Map<String, String> map = (Map)source;

            for (final Map.Entry<String, String> e : map.entrySet()) {
                writer.startNode(e.getKey());
                writer.setValue(e.getValue());
                writer.endNode();
            }
        } else {
            final Class<?> clazz = source.getClass();

            writer.startNode(clazz.getSimpleName());
            final Converter converter = converterLookup.lookupConverterForType(source.getClass());
            converter.marshal(source, writer, context);
            writer.endNode();
        }
    }

    @Override
    public Object unmarshal(final HierarchicalStreamReader reader, final UnmarshallingContext context) {
        return null;
    }

}
