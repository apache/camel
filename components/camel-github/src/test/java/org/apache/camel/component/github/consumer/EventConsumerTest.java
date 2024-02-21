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
package org.apache.camel.component.github.consumer;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github.GitHubComponentTestBase;
import org.eclipse.egit.github.core.event.Event;
import org.eclipse.egit.github.core.event.IssueCommentPayload;
import org.eclipse.egit.github.core.event.PushPayload;
import org.eclipse.egit.github.core.event.WatchPayload;
import org.junit.jupiter.api.Test;

public class EventConsumerTest extends GitHubComponentTestBase {

    @Test
    public void getEvents() throws Exception {
        Event watchEvent = new Event();
        watchEvent.setId("1");
        watchEvent.setPayload(new WatchPayload());
        watchEvent.setType("watchEvent");
        eventService.addEvent(watchEvent);

        Event pushEvent = new Event();
        pushEvent.setId("2");
        pushEvent.setPayload(new PushPayload());
        pushEvent.setType("pushEvent");
        eventService.addEvent(pushEvent);

        Event issueCommentEvent = new Event();
        issueCommentEvent.setId("3");
        issueCommentEvent.setPayload(new IssueCommentPayload());
        issueCommentEvent.setType("issueCommentEvent");
        eventService.addEvent(issueCommentEvent);

        mockResultEndpoint.expectedBodiesReceivedInAnyOrder("watchEvent", "pushEvent", "issueCommentEvent");
        mockResultEndpoint.assertIsSatisfied(5000);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("github:event?repoOwner=anotherguy&repoName=somerepo")
                        .to(mockResultEndpoint);
            }
        };
    }
}
