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
package org.apache.camel.component.netty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.codec.ObjectDecoder;
import org.apache.camel.component.netty.codec.ObjectEncoder;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NettyConcurrentTest extends BaseNettyTest {

    private static final Logger LOG = LoggerFactory.getLogger(NettyConcurrentTest.class);

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testSmallConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    @Test
    public void testMediumConcurrentProducers() throws Exception {
        doSendMessages(10000, 20);
    }

    @Test
    @Disabled
    public void testLargeConcurrentProducers() throws Exception {
        doSendMessages(250000, 100);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        StopWatch watch = new StopWatch();
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(files).create();

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        // we access the responses Map below only inside the main thread,
        // so no need for a thread-safe Map implementation
        Map<Integer, Future<String>> responses = new HashMap<>();
        for (int i = 0; i < files; i++) {
            final int index = i;
            Future<String> out = executor.submit(new Callable<String>() {
                public String call() {
                    String reply = template.requestBody("netty:tcp://localhost:{{port}}?encoders=#encoder&decoders=#decoder",
                            index, String.class);
                    LOG.debug("Sent {} received {}", index, reply);
                    assertEquals("Bye " + index, reply);
                    return reply;
                }
            });
            responses.put(index, out);
        }

        notify.matches(60, TimeUnit.SECONDS);
        LOG.info("Took {} millis to process {} messages using {} client threads.", watch.taken(), files, poolSize);
        assertEquals(files, responses.size());

        // get all responses
        Set<String> unique = new HashSet<>();
        for (Future<String> future : responses.values()) {
            unique.add(future.get());
        }

        // should be 'files' unique responses
        assertEquals(files, unique.size(), "Should be " + files + " unique responses");
        executor.shutdownNow();
    }

    @BindToRegistry("encoder")
    public ChannelHandler getEncoder() {
        return new ShareableChannelHandlerFactory(new ObjectEncoder());
    }

    @BindToRegistry("decoder")
    public ChannelHandler getDecoder() {
        return new DefaultChannelHandlerFactory() {
            @Override
            public ChannelHandler newChannelHandler() {
                return new ObjectDecoder(ClassResolvers.weakCachingResolver(null));
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty:tcp://localhost:{{port}}?sync=true&encoders=#encoder&decoders=#decoder").process(new Processor() {
                    public void process(Exchange exchange) {
                        String body = exchange.getIn().getBody(String.class);
                        exchange.getMessage().setBody("Bye " + body);
                    }
                }).to("log:progress?groupSize=1000");
            }
        };
    }

}
