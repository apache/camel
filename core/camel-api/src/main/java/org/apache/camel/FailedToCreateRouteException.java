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

import org.apache.camel.util.URISupport;
import org.jspecify.annotations.Nullable;

/**
 * Exception when failing to create a {@link org.apache.camel.Route}.
 */
public class FailedToCreateRouteException extends RuntimeCamelException {

    private final @Nullable String routeId;
    private final @Nullable String location;

    /**
     * @param cause a description of why route creation failed
     */
    public FailedToCreateRouteException(String cause) {
        super("Failed to create route because: " + Objects.requireNonNull(cause, "cause"));
        this.routeId = null;
        this.location = null;
    }

    /**
     * @param routeId  the ID of the route that failed to be created
     * @param location the source location of the route definition, or {@code null} if unknown
     * @param route    the route definition string
     * @param cause    the error message describing why route creation failed
     */
    public FailedToCreateRouteException(String routeId, @Nullable String location, String route, String cause) {
        super("Failed to create route: " + Objects.requireNonNull(routeId, "routeId")
              + (location != null ? " (source: " + location + ")" : "") + ": "
              + getRouteMessage(Objects.requireNonNull(route, "route")) + " because: "
              + Objects.requireNonNull(cause, "cause"));
        this.routeId = routeId;
        this.location = location;
    }

    /**
     * @param routeId  the ID of the route that failed to be created
     * @param location the source location of the route definition, or {@code null} if unknown
     * @param route    the route definition string
     * @param cause    the cause of the failure
     */
    public FailedToCreateRouteException(String routeId, @Nullable String location, String route, Throwable cause) {
        super("Failed to create route: " + Objects.requireNonNull(routeId, "routeId")
              + (location != null ? " (source: " + location + ")" : "") + ": "
              + getRouteMessage(Objects.requireNonNull(route, "route")) + " because: "
              + getExceptionMessage(Objects.requireNonNull(cause, "cause")),
              cause);
        this.routeId = routeId;
        this.location = location;
    }

    /**
     * @param routeId  the ID of the route that failed to be created
     * @param location the source location of the route definition, or {@code null} if unknown
     * @param route    the route definition string
     * @param at       the location in the route definition where the error occurred
     * @param cause    the cause of the failure
     */
    public FailedToCreateRouteException(String routeId, @Nullable String location, String route, String at, Throwable cause) {
        super("Failed to create route: " + Objects.requireNonNull(routeId, "routeId")
              + (location != null ? " (source: " + location + ")" : "") + " at: >>> "
              + Objects.requireNonNull(at, "at")
              + " <<< in route: " + getRouteMessage(Objects.requireNonNull(route, "route"))
              + " because: " + getExceptionMessage(Objects.requireNonNull(cause, "cause")), cause);
        this.routeId = routeId;
        this.location = location;
    }

    public @Nullable String getRouteId() {
        return routeId;
    }

    public @Nullable String getLocation() {
        return location;
    }

    protected static String getExceptionMessage(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    protected static String getRouteMessage(String route) {
        Objects.requireNonNull(route, "route");
        // cut the route after 60 chars, so it won't be too big in the message
        // users just need to be able to identify the route, so they know where to look
        if (route.length() > 60) {
            route = route.substring(0, 60) + "...";
        }

        // ensure to sanitize uri's in the route, so we do not show sensitive information such as passwords
        route = URISupport.sanitizeUri(route);
        return route;
    }

}
