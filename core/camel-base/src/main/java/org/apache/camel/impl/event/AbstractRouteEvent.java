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
package org.apache.camel.impl.event;

import java.util.EventObject;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.spi.CamelEvent.RouteEvent;

/**
 * Base class for {@link CamelContext} events.
 */
public abstract class AbstractRouteEvent extends EventObject implements RouteEvent {
    private static final long serialVersionUID = 1L;
    private final Route route;
    private long timestamp;

    public AbstractRouteEvent(Route source) {
        super(source);
        this.route = source;
    }

    @Override
    public Route getRoute() {
        return route;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
