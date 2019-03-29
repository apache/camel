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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.function.Predicate;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelEvent.RouteEvent;

import static org.apache.camel.component.micrometer.MicrometerConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTES_ADDED;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_ROUTES_RUNNING;
import static org.apache.camel.component.micrometer.MicrometerConstants.EVENT_TYPE_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.SERVICE_NAME;

public interface MicrometerRouteEventNotifierNamingStrategy {

    Predicate<Meter.Id> EVENT_NOTIFIERS = id -> MicrometerEventNotifierService.class.getSimpleName().equals(id.getTag(SERVICE_NAME));
    MicrometerRouteEventNotifierNamingStrategy DEFAULT = new MicrometerRouteEventNotifierNamingStrategy() {
        @Override
        public String getRouteAddedName() {
            return DEFAULT_CAMEL_ROUTES_ADDED;
        }

        @Override
        public String getRouteRunningName() {
            return DEFAULT_CAMEL_ROUTES_RUNNING;
        }
    };

    String getRouteAddedName();
    String getRouteRunningName();

    default Tags getTags(CamelContext camelContext) {
        return Tags.of(
                SERVICE_NAME, MicrometerEventNotifierService.class.getSimpleName(),
                CAMEL_CONTEXT_TAG, camelContext.getName(),
                EVENT_TYPE_TAG, RouteEvent.class.getSimpleName());
    }
}
