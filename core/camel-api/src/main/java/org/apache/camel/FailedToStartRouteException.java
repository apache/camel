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
 * Thrown when a previously built {@link Route} fails to start as part of the {@link CamelContext} startup or a
 * {@link org.apache.camel.spi.RouteController} {@code startRoute} call.
 * <p/>
 * Carries the failing {@code routeId} and the source location (where available) so error messages can point at the
 * offending DSL. For failures during route construction itself use {@link FailedToCreateRouteException}.
 */
public class FailedToStartRouteException extends RuntimeCamelException {

    private final String routeId;
    private final @Nullable String location;

    /**
     * @param routeId the route id that failed to start
     * @param message the detail message
     */
    public FailedToStartRouteException(String routeId, String message) {
        super("Failed to start route: " + Objects.requireNonNull(routeId, "routeId") + " because: "
              + Objects.requireNonNull(message, "message"));
        this.routeId = routeId;
        this.location = null;
    }

    /**
     * @param routeId the route id that failed to start
     * @param message the detail message
     * @param cause   the cause of the failure
     */
    public FailedToStartRouteException(String routeId, String message, Throwable cause) {
        super("Failed to start route: " + Objects.requireNonNull(routeId, "routeId") + " because: "
              + Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.routeId = routeId;
        this.location = null;
    }

    /**
     * @param routeId  the route id that failed to start
     * @param location the source location of the route definition, or {@code null} if unknown
     * @param message  the detail message
     * @param cause    the cause of the failure
     */
    public FailedToStartRouteException(String routeId, @Nullable String location, String message, Throwable cause) {
        super("Failed to start route: " + Objects.requireNonNull(routeId, "routeId")
              + (location != null ? " (source: " + location + ")" : "") + " because: "
              + Objects.requireNonNull(message, "message"),
              Objects.requireNonNull(cause, "cause"));
        this.routeId = routeId;
        this.location = location;
    }

    public String getRouteId() {
        return routeId;
    }

    public @Nullable String getLocation() {
        return location;
    }
}
