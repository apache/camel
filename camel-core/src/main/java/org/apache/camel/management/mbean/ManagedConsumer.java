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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version $Revision$
 */
@ManagedResource(description = "Managed Consumer")
public class ManagedConsumer extends ManagedService {

    private final Consumer consumer;
    private final Route route;

    public ManagedConsumer(CamelContext context, Consumer consumer) {
        this(context, consumer, null);
    }

    public ManagedConsumer(CamelContext context, Consumer consumer, Route route) {
        super(context, consumer);
        this.consumer = consumer;
        this.route = route;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public Route getRoute() {
        return route;
    }

    @ManagedAttribute(description = "Endpoint Uri")
    public String getEndpointUri() {
        return consumer.getEndpoint().getEndpointUri();
    }

    @ManagedAttribute(description = "Route id")
    public String getRouteId() {
        if (route != null) {
            return route.getId();
        }
        return null;
    }

}
