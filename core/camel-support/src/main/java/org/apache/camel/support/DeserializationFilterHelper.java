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
package org.apache.camel.support;

import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for resolving the JEP-290 {@link ObjectInputFilter} that components apply when deserializing Java objects from
 * potentially untrusted input (message payloads, persisted exchange state, and so on).
 *
 * <p>
 * The resolution order is the same for every component:
 * <ol>
 * <li>an explicitly configured filter pattern (typically a {@code deserializationFilter} endpoint or component
 * option);</li>
 * <li>the JVM-wide filter configured via the {@code jdk.serialFilter} system property or
 * {@link ObjectInputFilter.Config#setSerialFilter(ObjectInputFilter)};</li>
 * <li>a conservative Camel default.</li>
 * </ol>
 *
 * @since 4.22
 */
public final class DeserializationFilterHelper {

    /**
     * Default deserialization filter pattern applied while reading from an {@link ObjectInputStream} (via
     * {@link ObjectInputStream#setObjectInputFilter(ObjectInputFilter)}). Denies {@code java.net.**}, allows other
     * standard Java types and Apache Camel types, rejects everything else, and applies JEP-290 graph-shape limits
     * ({@code maxdepth}, {@code maxrefs}, {@code maxbytes}) as defense-in-depth against resource-exhaustion payloads.
     */
    public static final String DEFAULT_DESERIALIZATION_FILTER
            = "!java.net.**;java.**;javax.**;org.apache.camel.**;maxdepth=20;maxrefs=10000;maxbytes=10485760;!*";

    /**
     * Default deserialization filter pattern for checking the class of an object that a third-party library has
     * <em>already</em> deserialized, for example the payload returned by {@code jakarta.jms.ObjectMessage#getObject()}.
     * Same allow-list as {@link #DEFAULT_DESERIALIZATION_FILTER} but deliberately without the JEP-290 graph-shape
     * limits: stream metrics such as depth, reference count and byte count are not available once deserialization has
     * already happened, so including the limits would only advertise protection that can never be enforced.
     */
    public static final String DEFAULT_CLASS_DESERIALIZATION_FILTER
            = "!java.net.**;java.**;javax.**;org.apache.camel.**;!*";

    private static final Logger LOG = LoggerFactory.getLogger(DeserializationFilterHelper.class);

    private DeserializationFilterHelper() {
    }

    /**
     * Resolves the deserialization filter to apply, falling back to {@link #DEFAULT_DESERIALIZATION_FILTER}.
     *
     * @param  configuredPattern the filter pattern configured on the component or endpoint, may be null or blank
     * @return                   the filter to apply, never null
     */
    public static ObjectInputFilter resolveDeserializationFilter(String configuredPattern) {
        return resolveDeserializationFilter(configuredPattern, DEFAULT_DESERIALIZATION_FILTER);
    }

    /**
     * Resolves the deserialization filter to apply: the configured pattern when present, otherwise the JVM-wide filter
     * ({@code jdk.serialFilter}) when set, otherwise the given default pattern.
     *
     * @param  configuredPattern the filter pattern configured on the component or endpoint, may be null or blank
     * @param  defaultPattern    the default pattern to fall back to, must not be null
     * @return                   the filter to apply, never null
     */
    public static ObjectInputFilter resolveDeserializationFilter(String configuredPattern, String defaultPattern) {
        if (configuredPattern != null && !configuredPattern.isBlank()) {
            return ObjectInputFilter.Config.createFilter(configuredPattern);
        }
        ObjectInputFilter jvmFilter = ObjectInputFilter.Config.getSerialFilter();
        if (jvmFilter != null) {
            return jvmFilter;
        }
        LOG.debug("No JVM-wide deserialization filter set, applying default Camel filter: {}", defaultPattern);
        return ObjectInputFilter.Config.createFilter(defaultPattern);
    }

    /**
     * Checks the class of an already-deserialized object against the given filter. This is for components where a
     * third-party library performs the actual deserialization and Camel can only vet the result after the fact. Only
     * the class is checked; JEP-290 graph-shape limits ({@code maxdepth} and friends) in the filter pattern cannot be
     * evaluated in this mode, so resolve the filter with {@link #DEFAULT_CLASS_DESERIALIZATION_FILTER} as the default.
     *
     * @param  filter the filter to check against
     * @param  clazz  the class of the deserialized object
     * @return        the filter decision; callers should treat {@link ObjectInputFilter.Status#REJECTED} as fatal
     */
    public static ObjectInputFilter.Status checkClass(ObjectInputFilter filter, Class<?> clazz) {
        return filter.checkInput(new ClassOnlyFilterInfo(clazz));
    }

    private static final class ClassOnlyFilterInfo implements ObjectInputFilter.FilterInfo {
        private final Class<?> clazz;

        private ClassOnlyFilterInfo(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Class<?> serialClass() {
            return clazz;
        }

        @Override
        public long arrayLength() {
            return -1;
        }

        @Override
        public long depth() {
            return 0;
        }

        @Override
        public long references() {
            return 0;
        }

        @Override
        public long streamBytes() {
            return 0;
        }
    }
}
