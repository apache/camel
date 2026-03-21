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
package org.apache.camel.component.camelevent;

import org.apache.camel.spi.Metadata;

/**
 * Constants for the Camel Event component headers.
 */
public final class CamelEventConstants {

    @Metadata(label = "consumer", description = "The event type name (e.g., RouteStarted, ExchangeCompleted)",
              javaType = "String")
    public static final String HEADER_EVENT_TYPE = "CamelEventType";

    @Metadata(label = "consumer", description = "The event timestamp in milliseconds since epoch (if available)",
              javaType = "Long")
    public static final String HEADER_EVENT_TIMESTAMP = "CamelEventTimestamp";

    @Metadata(label = "consumer",
              description = "The route ID. For route events, the route that triggered the event."
                            + " For exchange events, the route the exchange originated from.",
              javaType = "String")
    public static final String HEADER_EVENT_ROUTE_ID = "CamelEventRouteId";

    @Metadata(label = "consumer", description = "The exchange ID (for exchange events)", javaType = "String")
    public static final String HEADER_EVENT_EXCHANGE_ID = "CamelEventExchangeId";

    @Metadata(label = "consumer",
              description = "The endpoint URI. For ExchangeSent/ExchangeSending events, the target endpoint."
                            + " For other exchange events, the from endpoint of the exchange.",
              javaType = "String")
    public static final String HEADER_EVENT_ENDPOINT_URI = "CamelEventEndpointUri";

    @Metadata(label = "consumer",
              description = "The exception message from FailureEvent.getCause() (for failure events such as"
                            + " ExchangeFailed, RouteRestartingFailure, ServiceStartupFailure, etc.)",
              javaType = "String")
    public static final String HEADER_EVENT_EXCEPTION = "CamelEventException";

    @Metadata(label = "consumer",
              description = "The time taken in milliseconds for ExchangeSent events",
              javaType = "Long")
    public static final String HEADER_EVENT_DURATION = "CamelEventDuration";

    @Metadata(label = "consumer",
              description = "The step ID (for step events such as StepStarted, StepCompleted, StepFailed)",
              javaType = "String")
    public static final String HEADER_EVENT_STEP_ID = "CamelEventStepId";

    @Metadata(label = "consumer",
              description = "The redelivery attempt number (for ExchangeRedelivery events)",
              javaType = "Integer")
    public static final String HEADER_EVENT_REDELIVERY_ATTEMPT = "CamelEventRedeliveryAttempt";

    @Metadata(label = "consumer",
              description = "The number of events in a batch (only set when batchSize is configured)",
              javaType = "Integer")
    public static final String HEADER_EVENT_BATCH_SIZE = "CamelEventBatchSize";

    private CamelEventConstants() {
    }
}
