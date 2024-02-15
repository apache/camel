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
package org.apache.camel.component.salesforce;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.PlatformEvent;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.entry;

@Tag("standalone")
public class PlatformEventsConsumerManualIT extends AbstractSalesforceTestBase {

    @Test
    public void shouldConsumePlatformEvents() throws InterruptedException, ExecutionException {
        final ExecutorService parallel = Executors.newSingleThreadExecutor();

        final Future<PlatformEvent> futurePlatformEvent
                = parallel.submit(
                        () -> consumer.receiveBody("salesforce:subscribe:event/TestEvent__e?replayId=-1", PlatformEvent.class));

        // it takes some time for the subscriber to subscribe, so we'll try to
        // send repeated platform events and wait until the first one is
        // received
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            template.sendBody("direct:sendPlatformEvent", "{\"Test_Field__c\": \"data\"}");

            Assertions.assertThat(futurePlatformEvent.isDone()).isTrue();
        });

        final PlatformEvent platformEvent = futurePlatformEvent.get();
        Assertions.assertThat(platformEvent).isNotNull();
        Assertions.assertThat(platformEvent.getEventData()).containsOnly(entry("Test_Field__c", "data"));
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:sendPlatformEvent").to("salesforce:createSObject?sObjectName=TestEvent__e");
            }
        };
    }
}
