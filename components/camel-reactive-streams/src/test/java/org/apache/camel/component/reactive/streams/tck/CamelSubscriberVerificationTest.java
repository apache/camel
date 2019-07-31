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
package org.apache.camel.component.reactive.streams.tck;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreams;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultShutdownStrategy;
import org.apache.camel.support.DefaultExchange;
import org.reactivestreams.Subscriber;
import org.reactivestreams.tck.SubscriberBlackboxVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterTest;

public class CamelSubscriberVerificationTest extends SubscriberBlackboxVerification<Exchange> {

    private CamelContext context;

    public CamelSubscriberVerificationTest() {
        super(new TestEnvironment(2000L));
    }

    @Override
    public Subscriber<Exchange> createSubscriber() {
        init();

        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("reactive-streams:sub?maxInflightExchanges=20")
                        .to("log:INFO");
            }
        };

        Subscriber<Exchange> sub = CamelReactiveStreams.get(context).streamSubscriber("sub");

        try {
            builder.addRoutesToCamelContext(context);
            context.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return sub;
    }

    @Override
    public Exchange createElement(int element) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(element);
        return exchange;
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
