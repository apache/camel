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

import org.apache.camel.util.URISupport;

/**
 * Exception when failing to create a {@link org.apache.camel.Route}.
 */
public class FailedToCreateRouteException extends RuntimeCamelException {

    private final String routeId;
    private final String location;

    public FailedToCreateRouteException(String cause) {
        super("Failed to create route because: " + cause);
        this.routeId = null;
        this.location = null;
    }

    public FailedToCreateRouteException(String routeId, String location, String route, String cause) {
        super("Failed to create route: " + routeId + (location != null ? " (source: " + location + ")" : "") + ": "
                + getRouteMessage(route) + " because: " + cause);
        this.routeId = routeId;
        this.location = location;
    }

    public FailedToCreateRouteException(String routeId, String location, String route, Throwable cause) {
        super(
                "Failed to create route: " + routeId + (location != null ? " (source: " + location + ")" : "") + ": "
                        + getRouteMessage(route) + " because: " + getExceptionMessage(cause),
                cause);
        this.routeId = routeId;
        this.location = location;
    }

    public FailedToCreateRouteException(String routeId, String location, String route, String at, Throwable cause) {
        super(
                "Failed to create route: " + routeId + (location != null ? " (source: " + location + ")" : "")
                        + " at: >>> " + at
                        + " <<< in route: " + getRouteMessage(route)
                        + " because: " + getExceptionMessage(cause),
                cause);
        this.routeId = routeId;
        this.location = location;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getLocation() {
        return location;
    }

    protected static String getExceptionMessage(Throwable cause) {
        return cause.getMessage() != null
                ? cause.getMessage()
                : cause.getClass().getSimpleName();
    }

    protected static String getRouteMessage(String route) {
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
