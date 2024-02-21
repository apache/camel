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
package org.apache.camel.component.micrometer.messagehistory;

import java.util.function.Predicate;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.component.micrometer.MicrometerUtils;

import static org.apache.camel.component.micrometer.MicrometerConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.NODE_ID_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.ROUTE_ID_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.SERVICE_NAME;

/**
 * Provides a strategy to derive a meter name from the route and node
 */
public interface MicrometerMessageHistoryNamingStrategy {

    Predicate<Meter.Id> MESSAGE_HISTORIES
            = id -> MicrometerMessageHistoryService.class.getSimpleName().equals(id.getTag(SERVICE_NAME));

    /**
     * Default naming strategy that uses micrometer naming convention.
     */
    MicrometerMessageHistoryNamingStrategy DEFAULT = (route, node) -> DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME;

    /**
     * Naming strategy that uses the classic/legacy naming style (camelCase)
     */
    MicrometerMessageHistoryNamingStrategy LEGACY = new MicrometerMessageHistoryNamingStrategy() {
        @Override
        public String getName(Route route, NamedNode node) {
            return MicrometerUtils.legacyName(DEFAULT_CAMEL_MESSAGE_HISTORY_METER_NAME);
        }
    };

    String getName(Route route, NamedNode node);

    default String formatName(String name) {
        return name;
    }

    default Tags getTags(Route route, NamedNode node) {
        return Tags.of(
                CAMEL_CONTEXT_TAG, route.getCamelContext().getName(),
                SERVICE_NAME, MicrometerMessageHistoryService.class.getSimpleName(),
                ROUTE_ID_TAG, route.getId(),
                NODE_ID_TAG, node.getId());
    }

}
