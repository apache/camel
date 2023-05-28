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
package org.apache.camel.component.extension.metadata;

import java.util.Collections;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.extension.MetaDataExtension;

public class DefaultMetaData implements MetaDataExtension.MetaData {
    private final Map<String, Object> attributes;
    private final Object payload;
    private final CamelContext camelContext;

    public DefaultMetaData(CamelContext camelContext, Map<String, Object> attributes, Object payload) {
        this.camelContext = camelContext;
        this.attributes = attributes;
        this.payload = payload;
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public <T> T getAttribute(String name, Class<T> type) {
        Object value = attributes.get(name);
        if (camelContext != null) {
            return camelContext.getTypeConverter().convertTo(type, value);
        }

        throw new IllegalStateException("Unable to perform conversion as CamelContext is not set");
    }

    @Override
    public Object getPayload() {
        return payload;
    }

    @Override
    public <T> T getPayload(Class<T> type) {
        if (camelContext != null) {
            return camelContext.getTypeConverter().convertTo(type, payload);
        }

        throw new IllegalStateException("Unable to perform conversion as CamelContext is not set");
    }
}
