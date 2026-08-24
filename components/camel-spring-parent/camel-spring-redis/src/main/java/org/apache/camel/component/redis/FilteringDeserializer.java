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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /**
     * Default {@link ObjectInputFilter} pattern applied when the default JDK serializer reads Redis payloads. Allows
     * standard Java types and Apache Camel types and rejects everything else. Can be overridden per-endpoint via
     * {@link RedisConfiguration#setDeserializationFilter(String)} or globally via the JVM system property
     * {@code jdk.serialFilter}.
     */
    static final String DEFAULT_DESERIALIZATION_FILTER
            = "!java.net.**;java.**;javax.**;org.apache.camel.**;!*";

    private static final Logger LOG = LoggerFactory.getLogger(FilteringDeserializer.class);

    private final String deserializationFilter;

    FilteringDeserializer(String deserializationFilter) {
        this.deserializationFilter = deserializationFilter;
    }

    @Override
    public Object deserialize(InputStream inputStream) throws IOException {
        ObjectInputStream ois = new ConfigurableObjectInputStream(inputStream, null);
        ois.setObjectInputFilter(resolveDeserializationFilter(deserializationFilter));
        try {
            return ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize object type", e);
        }
    }

    private static ObjectInputFilter resolveDeserializationFilter(String configuredPattern) {
        if (configuredPattern != null && !configuredPattern.isBlank()) {
            return ObjectInputFilter.Config.createFilter(configuredPattern);
        }
        ObjectInputFilter jvmFilter = ObjectInputFilter.Config.getSerialFilter();
        if (jvmFilter != null) {
            return jvmFilter;
        }
        LOG.debug("No JVM-wide deserialization filter set, applying default Camel filter: {}",
                DEFAULT_DESERIALIZATION_FILTER);
        return ObjectInputFilter.Config.createFilter(DEFAULT_DESERIALIZATION_FILTER);
    }
}
