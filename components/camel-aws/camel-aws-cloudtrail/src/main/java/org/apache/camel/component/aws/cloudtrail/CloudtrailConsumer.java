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
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.model.Event;
import software.amazon.awssdk.services.cloudtrail.model.LookupAttribute;
import software.amazon.awssdk.services.cloudtrail.model.LookupAttributeKey;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsRequest;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsResponse;

public class CloudtrailConsumer extends ScheduledBatchPollingConsumer {
    private static Instant lastTime;

    private WritableHealthCheckRepository healthCheckRepository;
    private CloudtrailConsumerHealthCheck consumerHealthCheck;

    public CloudtrailConsumer(CloudtrailEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "components",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            consumerHealthCheck = new CloudtrailConsumerHealthCheck(this, getRouteId());
            healthCheckRepository.addHealthCheck(consumerHealthCheck);
        }
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

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && consumerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(consumerHealthCheck);
            consumerHealthCheck = null;
        }
        super.doStop();
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
        exchange.getIn().setBody(event.cloudTrailEvent().getBytes(StandardCharsets.UTF_8));
        return exchange;
    }
}
