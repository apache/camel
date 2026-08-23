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
package org.apache.camel.component.redis;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

import org.apache.camel.support.DeserializationFilterHelper;
import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.core.serializer.Deserializer;

/**
 * A Spring {@link Deserializer} that installs an {@link ObjectInputFilter} on the stream before the object graph is
 * read.
 * <p/>
 * Spring's own {@code DefaultDeserializer} reads through a {@link ConfigurableObjectInputStream} without any filter, so
 * the default {@code JdkSerializationRedisSerializer} would otherwise materialise arbitrary types. This deserializer is
 * behaviourally identical apart from the filter, and in particular resolves classes the same way by passing the same
 * {@code null} class loader that {@code DefaultDeserializer} uses by default.
 */
class FilteringDeserializer implements Deserializer<Object> {

    private final String deserializationFilter;

    FilteringDeserializer(String deserializationFilter) {
        this.deserializationFilter = deserializationFilter;
    }

    @Override
    public Object deserialize(InputStream inputStream) throws IOException {
        ObjectInputStream ois = new ConfigurableObjectInputStream(inputStream, null);
        ois.setObjectInputFilter(DeserializationFilterHelper.resolveDeserializationFilter(deserializationFilter));
        try {
            return ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize object type", e);
        }
    }
}
