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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

import org.apache.camel.CamelContext;

/**
 * An {@link ObjectInputStream} that resolves classes against the Camel application classloader and installs a JEP-290
 * {@link ObjectInputFilter} while reading, as a defense-in-depth measure against unsafe deserialization.
 *
 * <p>
 * As this is the shared stream used by Camel deserialization consumers, the filter is applied by default so that every
 * caller inherits it. When no explicit pattern is supplied the JVM-wide {@code jdk.serialFilter} is honoured if set,
 * otherwise {@link DeserializationFilterHelper#DEFAULT_DESERIALIZATION_FILTER} is applied.
 */
public class CamelObjectInputStream extends ObjectInputStream {
    private final ClassLoader classLoader;

    public CamelObjectInputStream(InputStream in, CamelContext context) throws IOException {
        this(in, context, null);
    }

    /**
     * Creates a {@link CamelObjectInputStream} that applies a JEP-290 {@link ObjectInputFilter} while reading.
     *
     * @param  in                    the input stream to read from
     * @param  context               the camel context used to resolve the application classloader; may be {@code null}
     * @param  deserializationFilter an {@link ObjectInputFilter} pattern (same syntax as {@code jdk.serialFilter}) to
     *                               apply; when {@code null} or blank the JVM-wide {@code jdk.serialFilter} is used if
     *                               present, otherwise
     *                               {@link DeserializationFilterHelper#DEFAULT_DESERIALIZATION_FILTER} is applied
     * @throws IOException           if an I/O error occurs while reading the stream header
     * @since                        4.22
     */
    public CamelObjectInputStream(InputStream in, CamelContext context, String deserializationFilter) throws IOException {
        super(in);
        if (context != null) {
            this.classLoader = context.getApplicationContextClassLoader();
        } else {
            this.classLoader = null;
        }
        setObjectInputFilter(DeserializationFilterHelper.resolveDeserializationFilter(deserializationFilter));
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws ClassNotFoundException, IOException {
        if (classLoader != null) {
            return Class.forName(desc.getName(), false, classLoader);
        } else {
            // If the application classloader is not set we just fallback to use old behaivor
            return super.resolveClass(desc);
        }
    }
}
