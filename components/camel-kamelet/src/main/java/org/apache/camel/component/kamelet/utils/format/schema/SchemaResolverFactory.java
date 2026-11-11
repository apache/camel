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
package org.apache.camel.component.kamelet.utils.format.schema;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Processor;

/**
 * Creates the schema resolvers of one Jackson line. The resolvers are created with plain constructor calls, so that a
 * native image sees them without reflection configuration. Each line lives in its own class: when its artifact is not
 * on the classpath, only the calls into that class fail.
 */
abstract class SchemaResolverFactory {

    enum Format {
        JSON,
        AVRO,
        PROTOBUF
    }

    // The classpath of a factory does not change, so a missing class is not tried again
    private final Set<Format> missing = ConcurrentHashMap.newKeySet();

    /**
     * The resolver for the format, or null when the artifact of this line is not on the classpath.
     */
    Processor create(Format format, String schema, String contentClass) {
        if (missing.contains(format)) {
            return null;
        }
        try {
            return doCreate(format, schema, contentClass);
        } catch (NoClassDefFoundError e) {
            missing.add(format);
            return null;
        }
    }

    abstract Processor doCreate(Format format, String schema, String contentClass);

    /**
     * The class name of the resolver for the format, to load it from a class resolver of its own (Camel JBang downloads
     * the artifact at runtime).
     */
    abstract String className(Format format);

    /**
     * The artifact that holds the resolver for the format.
     */
    abstract String artifactId(Format format);
}
