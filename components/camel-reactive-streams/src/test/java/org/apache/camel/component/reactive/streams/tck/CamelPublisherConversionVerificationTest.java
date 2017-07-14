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
import org.apache.camel.impl.DefaultShutdownStrategy;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterTest;

public class CamelPublisherConversionVerificationTest extends PublisherVerification<Long> {

    private CamelContext context;

    public CamelPublisherConversionVerificationTest() {
        super(new TestEnvironment(2000L));
    }


    @Override
    public Publisher<Long> createPublisher(long l) {
        init();

        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:tick?delay=500&period=50&repeatCount=" + l)
                        .setBody().simple("random(1000)")
                        .to("reactive-streams:prod");
            }
        };

        try {
            builder.addRoutesToCamelContext(context);
            context.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Publisher<Long> pub = CamelReactiveStreams.get(context).fromStream("prod", Long.class);
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

    protected void init() {
        tearDown();
        this.context = new DefaultCamelContext();
        DefaultShutdownStrategy shutdownStrategy = new DefaultShutdownStrategy();
        shutdownStrategy.setShutdownNowOnTimeout(true);
        shutdownStrategy.setTimeout(1);
        this.context.setShutdownStrategy(shutdownStrategy);
    }

    @AfterTest
    protected void tearDown() {
        try {
            if (this.context != null) {
                this.context.stop();
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
