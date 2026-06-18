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
package org.apache.camel;

import java.io.IOException;
import java.util.Objects;

/**
 * Thrown by the Camel {@code FactoryFinder} mechanism when no implementation class can be located for a given service
 * URI.
 * <p/>
 * The {@code FactoryFinder} scans the classpath for service descriptor files under
 * {@code META-INF/services/org/apache/camel/} to discover pluggable implementations of Camel SPIs (components, data
 * formats, languages, dumpers, etc.). When the required JAR is absent from the classpath, or when the service
 * descriptor file is missing or misconfigured, this exception is thrown with the full service URI that could not be
 * resolved. It extends {@link java.io.IOException} so callers that use the raw I/O-based discovery path can catch it at
 * the right level; higher-level Camel code typically wraps it in a {@link RuntimeCamelException} for propagation across
 * unchecked API boundaries.
 *
 * @see RuntimeCamelException
 */
public class NoFactoryAvailableException extends IOException {

    private final String uri;

    /**
     * @param uri the URI for which no factory class could be found
     */
    public NoFactoryAvailableException(String uri) {
        super("Cannot find factory class for resource: " + Objects.requireNonNull(uri, "uri"));
        this.uri = uri;
    }

    /**
     * @param uri   the URI for which no factory class could be found
     * @param cause the cause of the failure
     */
    public NoFactoryAvailableException(String uri, Throwable cause) {
        this(uri);
        initCause(Objects.requireNonNull(cause, "cause"));
    }

    public String getUri() {
        return uri;
    }
}
