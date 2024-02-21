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
package org.apache.camel.component.aws.cloudtrail;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.model.*;

public class CloudtrailConsumer extends ScheduledBatchPollingConsumer {
    private static Instant lastTime;

    public CloudtrailConsumer(CloudtrailEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        LookupEventsRequest.Builder eventsRequestBuilder
                = LookupEventsRequest.builder().maxResults(getEndpoint().getConfiguration().getMaxResults());

        List<LookupAttribute> attributes = new ArrayList<>();
        if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getEventSource())) {
            LookupAttribute eventSource = LookupAttribute.builder().attributeKey(LookupAttributeKey.EVENT_SOURCE)
                    .attributeValue(getEndpoint().getConfiguration().getEventSource()).build();
            attributes.add(eventSource);
        }
        if (!attributes.isEmpty()) {
            eventsRequestBuilder.lookupAttributes(attributes);
        }
        if (lastTime != null) {
            eventsRequestBuilder.startTime(lastTime.plusMillis(1000));
        }

        LookupEventsResponse response = getClient().lookupEvents(eventsRequestBuilder.build());

        // okay we have some response from aws so lets mark the consumer as ready
        forceConsumerAsReady();

        if (!response.events().isEmpty()) {
            lastTime = response.events().get(0).eventTime();
        }

        Queue<Exchange> exchanges = createExchanges(response.events());
        return processBatch(CastUtils.cast(exchanges));
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int processedExchanges = 0;
        while (!exchanges.isEmpty()) {
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
            processedExchanges++;
        }
        return processedExchanges;
    }

    private CloudTrailClient getClient() {
        return getEndpoint().getClient();
    }

    @Override
    public CloudtrailEndpoint getEndpoint() {
        return (CloudtrailEndpoint) super.getEndpoint();
    }

    private Queue<Exchange> createExchanges(List<Event> events) {
        Queue<Exchange> exchanges = new ArrayDeque<>();
        for (Event event : events) {
            exchanges.add(createExchange(event));
        }
        return exchanges;
    }

    protected Exchange createExchange(Event event) {
        Exchange exchange = createExchange(true);
        exchange.getMessage().setBody(event.cloudTrailEvent().getBytes(StandardCharsets.UTF_8));
        exchange.getMessage().setHeader(CloudtrailConstants.EVENT_ID, event.eventId());
        exchange.getMessage().setHeader(CloudtrailConstants.EVENT_NAME, event.eventName());
        exchange.getMessage().setHeader(CloudtrailConstants.EVENT_SOURCE, event.eventSource());
        exchange.getMessage().setHeader(CloudtrailConstants.USERNAME, event.username());
        return exchange;
    }
}
