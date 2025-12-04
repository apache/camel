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

/**
 * Exception when failing to start a {@link Route}.
 */
public class FailedToStartRouteException extends RuntimeCamelException {

    private final String routeId;
    private final String location;

    public FailedToStartRouteException(String routeId, String message) {
        super("Failed to start route: " + routeId + " because: " + message);
        this.routeId = routeId;
        this.location = null;
    }

    public FailedToStartRouteException(String routeId, String message, Throwable cause) {
        super("Failed to start route: " + routeId + " because: " + message, cause);
        this.routeId = routeId;
        this.location = null;
    }

    public FailedToStartRouteException(String routeId, String location, String message, Throwable cause) {
        super(
                "Failed to start route: " + routeId + (location != null ? " (source: " + location + ")" : "")
                        + " because: " + message,
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
}
