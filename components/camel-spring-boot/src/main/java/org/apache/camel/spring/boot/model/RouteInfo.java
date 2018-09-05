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
package org.apache.camel.spring.boot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.camel.Route;
import org.apache.camel.StatefulService;

/**
 * Container for exposing {@link org.apache.camel.Route} information as JSON.
 */
@JsonPropertyOrder({"id", "description", "uptime", "uptimeMillis"})
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RouteInfo {

    private final String id;

    private final String description;

    private final String uptime;

    private final long uptimeMillis;

    private final String status;

    public RouteInfo(Route route) {
        this.id = route.getId();
        this.description = route.getDescription();
        this.uptime = route.getUptime();
        this.uptimeMillis = route.getUptimeMillis();

        if (route instanceof StatefulService) {
            this.status = ((StatefulService) route).getStatus().name();
        } else {
            this.status = null;
        }
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getUptime() {
        return uptime;
    }

    public long getUptimeMillis() {
        return uptimeMillis;
    }

    public String getStatus() {
        return status;
    }
}
