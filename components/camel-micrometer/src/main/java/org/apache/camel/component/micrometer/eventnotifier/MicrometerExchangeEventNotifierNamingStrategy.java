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
package org.apache.camel.component.micrometer.eventnotifier;

import java.util.function.Predicate;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.management.event.AbstractExchangeEvent;
import static org.apache.camel.component.micrometer.MicrometerConstants.CAMEL_CONTEXT_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.ENDPOINT_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.EVENT_TYPE_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.FAILED_TAG;
import static org.apache.camel.component.micrometer.MicrometerConstants.SERVICE_NAME;

public interface MicrometerExchangeEventNotifierNamingStrategy {

    Predicate<Meter.Id> EVENT_NOTIFIERS = id -> MicrometerEventNotifierService.class.getSimpleName().equals(id.getTag(SERVICE_NAME));
    MicrometerExchangeEventNotifierNamingStrategy DEFAULT = (event, endpoint) -> DEFAULT_CAMEL_EXCHANGE_EVENT_METER_NAME;

    String getName(Exchange exchange, Endpoint endpoint);

    default Tags getTags(AbstractExchangeEvent event, Endpoint endpoint) {
        return Tags.of(
                CAMEL_CONTEXT_TAG, event.getExchange().getContext().getName(),
                SERVICE_NAME, MicrometerEventNotifierService.class.getSimpleName(),
                EVENT_TYPE_TAG, event.getClass().getSimpleName(),
                ENDPOINT_NAME, endpoint.getEndpointUri(),
                FAILED_TAG, Boolean.toString(event.getExchange().isFailed())
        );
    }
}
