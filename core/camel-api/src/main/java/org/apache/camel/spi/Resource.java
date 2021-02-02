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
package org.apache.camel.spi;

import java.io.IOException;
import java.io.InputStream;

/**
 * Describe a resource, such as a file or class path resource.
 */
public interface Resource {
    /**
     * The location of the resource.
     */
    String getLocation();

    /**
     * Returns an input stream that reads from the underlying resource.
     * </p>
     * Each invocation must return a new {@link InputStream} instance.
     */
    InputStream getInputStream() throws IOException;

    /**
     * Finds a resource with a given name.
     *
     * @see Class#getResourceAsStream(String)
     *
     */
    static Resource fromClasspath(String location) {
        return fromClasspath(Resource.class, location);
    }

    /**
     * Finds a resource with a given name.
     *
     * @see Class#getResourceAsStream(String)
     *
     */
    static Resource fromClasspath(Class<?> type, String location) {
        return new Resource() {
            @Override
            public String getLocation() {
                return location;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return type.getResourceAsStream(location);
            }
        };
    }
}
