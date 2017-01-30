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
package org.apache.camel.component.reactive.streams.tck;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.impl.DefaultCamelContext;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

public class CamelPublisherConversionVerificationTest extends PublisherVerification<Long> {

    public CamelPublisherConversionVerificationTest() {
        super(new TestEnvironment(2000L));
    }

    @Override
    public Publisher<Long> createPublisher(long l) {

        CamelContext context = new DefaultCamelContext();
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:tick?delay=500&period=50&repeatCount=" + l)
                        .setBody().simple("random(1000)")
                        .to("reactive-streams:prod");
            }
        };

        Publisher<Long> pub = CamelReactiveStreams.get(context).getPublisher("prod", Long.class);

        try {
            builder.addRoutesToCamelContext(context);
            context.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return pub;
    }

    @Override
    public long maxElementsFromPublisher() {
        // It's an active publisher
        return publisherUnableToSignalOnComplete(); // == Long.MAX_VALUE == unbounded
    }

    @Override
    public Publisher<Long> createFailedPublisher() {
        return null;
    }
}
