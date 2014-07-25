/**
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
 * Exception when failing to create a {@link org.apache.camel.Route}.
 *
 * @version 
 */
public class FailedToCreateRouteException extends CamelException {
    private static final long serialVersionUID = 1L;
    private final String routeId;

    public FailedToCreateRouteException(String routeId, String route, Throwable cause) {
        super("Failed to create route " + routeId + ": " + getRouteMessage(route) + " because of " + getExceptionMessage(cause), cause);
        this.routeId = routeId;
    }

    public FailedToCreateRouteException(String routeId, String route, String at, Throwable cause) {
        super("Failed to create route " + routeId + " at: >>> " + at + " <<< in route: " + getRouteMessage(route) + " because of " + cause.getMessage(), cause);
        this.routeId = routeId;
    }

    public String getRouteId() {
        return routeId;
    }
    
    protected static String getExceptionMessage(Throwable cause) {
        if (cause.getMessage() != null) {
            return cause.getMessage();
        } else {
            return cause.getClass().getSimpleName();
        }
    }

    protected static String getRouteMessage(String route) {
        // cut the route after 60 chars so it won't be too big in the message
        // users just need to be able to identify the route so they know where to look
        if (route.length() > 60) {
            return route.substring(0, 60) + "...";
        } else {
            return route;
        }
    }
}