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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A runtime exception if a given bean could not be found in the {@link org.apache.camel.spi.Registry}
 */
public class NoSuchBeanException extends RuntimeCamelException {

    private final @Nullable String name;

    /**
     * @param name the bean name that could not be found
     */
    public NoSuchBeanException(String name) {
        super("No bean could be found in the registry for: " + Objects.requireNonNull(name, "name"));
        this.name = name;
    }

    /**
     * @param name the bean name that was looked up
     * @param size the number of matching beans found (0 means none, &gt;1 means ambiguous)
     */
    public NoSuchBeanException(String name, int size) {
        super(size > 0
                ? "Found " + size + " beans for: " + Objects.requireNonNull(name, "name")
                  + " in the registry, only 1 bean excepted."
                : "No bean could be found in the registry for: " + name);
        this.name = name;
    }

    /**
     * @param name the bean name that could not be found, or {@code null} if only the type matters
     * @param type the required bean type
     */
    public NoSuchBeanException(@Nullable String name, String type) {
        super("No bean could be found in the registry" + (name != null ? " for: " + name : "") + " of type: "
              + Objects.requireNonNull(type, "type"));
        this.name = name;
    }

    /**
     * @param name  the bean name that could not be found
     * @param cause the cause of the failure
     */
    public NoSuchBeanException(String name, Throwable cause) {
        super("No bean could be found in the registry for: " + Objects.requireNonNull(name, "name") + ". Cause: "
              + Objects.requireNonNull(cause, "cause").getMessage(), cause);
        this.name = name;
    }

    /**
     * @param name    the bean name that could not be found
     * @param message the detail message
     * @param cause   the cause of the failure
     */
    public NoSuchBeanException(String name, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.name = Objects.requireNonNull(name, "name");
    }

    public @Nullable String getName() {
        return name;
    }
}
