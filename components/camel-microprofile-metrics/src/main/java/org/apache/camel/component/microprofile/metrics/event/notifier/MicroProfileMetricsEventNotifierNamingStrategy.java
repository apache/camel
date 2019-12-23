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
package org.apache.camel.component.microprofile.metrics.event.notifier;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.eclipse.microprofile.metrics.Tag;

import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.ENDPOINT_NAME;
import static org.apache.camel.component.microprofile.metrics.MicroProfileMetricsConstants.EVENT_TYPE_TAG;

public interface MicroProfileMetricsEventNotifierNamingStrategy {

    String getName(Exchange exchange, Endpoint endpoint);

    default Tag[] getTags(ExchangeEvent event, Endpoint endpoint) {
        return new Tag[] {
            new Tag(CAMEL_CONTEXT_TAG, event.getExchange().getContext().getName()),
            new Tag(ENDPOINT_NAME, endpoint.getEndpointUri()),
            new Tag(EVENT_TYPE_TAG, event.getClass().getSimpleName()),
        };
    }
}
