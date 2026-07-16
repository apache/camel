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

import java.time.Instant;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.model.Event;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsRequest;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CloudtrailConsumerTest {

    @Mock
    private CloudTrailClient cloudTrailClient;
    @Mock
    private AsyncProcessor processor;

    private final CamelContext context = new DefaultCamelContext();
    private final CloudtrailComponent component = new CloudtrailComponent(context);

    private CloudtrailConsumer underTest;

    private static Event event(String id, Instant time) {
        return Event.builder().eventId(id).eventName("name").eventSource("src").username("user")
                .eventTime(time).cloudTrailEvent("{}").build();
    }

    @BeforeEach
    public void setup() throws Exception {
        component.start();
        CloudtrailConfiguration configuration = new CloudtrailConfiguration();
        configuration.setCloudTrailClient(cloudTrailClient);
        CloudtrailEndpoint endpoint = new CloudtrailEndpoint("aws-cloudtrail://test", configuration, component);
        endpoint.start();
        underTest = new CloudtrailConsumer(endpoint, processor);
        underTest.start();
    }

    @Test
    public void pollDrainsEveryPage() throws Exception {
        Instant t = Instant.parse("2099-01-01T00:00:00Z");
        // Page 1 carries a nextToken; page 2 does not. Both pages must be delivered in a single poll.
        var page1 = LookupEventsResponse.builder()
                .events(event("e1", t.plusSeconds(3)), event("e2", t.plusSeconds(2)))
                .nextToken("token")
                .build();
        var page2 = LookupEventsResponse.builder()
                .events(event("e3", t.plusSeconds(1)))
                .build();
        when(cloudTrailClient.lookupEvents(any(LookupEventsRequest.class))).thenReturn(page1, page2);

        int processed = underTest.poll();

        Assertions.assertEquals(3, processed, "poll() must deliver events from every page of the window");
        verify(cloudTrailClient, times(2)).lookupEvents(any(LookupEventsRequest.class));
    }

    @Test
    public void pollDoesNotRedeliverBoundaryEventsAcrossPolls() throws Exception {
        Instant t = Instant.parse("2099-01-01T00:00:00Z");
        // startTime is inclusive, so the newest event of poll #1 is returned again in poll #2 and must be skipped.
        var firstPoll = LookupEventsResponse.builder()
                .events(event("e2", t.plusSeconds(2)), event("e1", t.plusSeconds(1)))
                .build();
        var secondPoll = LookupEventsResponse.builder()
                .events(event("e3", t.plusSeconds(3)), event("e2", t.plusSeconds(2)))
                .build();
        when(cloudTrailClient.lookupEvents(any(LookupEventsRequest.class))).thenReturn(firstPoll, secondPoll);

        Assertions.assertEquals(2, underTest.poll(), "first poll delivers both new events");
        Assertions.assertEquals(1, underTest.poll(), "second poll must skip the already-delivered boundary event e2");
    }
}
