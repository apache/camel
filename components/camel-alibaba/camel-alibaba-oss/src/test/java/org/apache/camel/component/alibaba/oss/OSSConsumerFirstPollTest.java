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
package org.apache.camel.component.alibaba.oss;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Request;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Result;
import com.aliyun.sdk.service.oss2.models.ObjectSummary;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scheduler may run the first poll as soon as the consumer starts it (initialDelay=0), so the consumer must have
 * its client before it starts the scheduler. The scheduler of this test runs the first poll right away, in the thread
 * that starts the consumer.
 */
class OSSConsumerFirstPollTest extends CamelTestSupport {

    private final List<Exception> failedPolls = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("ossClient", ossClient());
        context.getRegistry().bind("pollOnStart", new PollOnStartScheduler());
        context.getRegistry().bind("recordFailedPolls", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                // noop
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                failedPolls.add(e);
                return false;
            }
        });
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("alibaba-oss:bucket?ossClient=#ossClient&scheduler=#pollOnStart&pollStrategy=#recordFailedPolls")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

    @Test
    void firstPollRunsWithTheClient() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("a.txt");

        mock.assertIsSatisfied();
        assertThat(failedPolls).as("the first poll does not fail").isEmpty();
    }

    private static OSSClient ossClient() {
        OSSClient client = Mockito.mock(OSSClient.class);
        ObjectSummary summary = Mockito.mock(ObjectSummary.class);
        Mockito.when(summary.key()).thenReturn("a.txt");
        ListObjectsV2Result listing = Mockito.mock(ListObjectsV2Result.class);
        Mockito.when(listing.contents()).thenReturn(List.of(summary));
        Mockito.when(client.listObjectsV2(Mockito.any(ListObjectsV2Request.class))).thenReturn(listing);
        Mockito.when(client.getObject(Mockito.any(GetObjectRequest.class))).thenAnswer(i -> {
            GetObjectResult result = Mockito.mock(GetObjectResult.class);
            Mockito.when(result.body()).thenReturn(new ByteArrayInputStream("a.txt".getBytes(StandardCharsets.UTF_8)));
            return result;
        });
        return client;
    }

    /**
     * Runs the task once when the scheduler is started.
     */
    private static final class PollOnStartScheduler extends ServiceSupport implements ScheduledPollConsumerScheduler {

        private CamelContext camelContext;
        private Runnable task;
        private boolean started;

        @Override
        public void onInit(Consumer consumer) {
            // noop
        }

        @Override
        public void scheduleTask(Runnable task) {
            this.task = task;
        }

        @Override
        public void unscheduleTask() {
            task = null;
        }

        @Override
        public void startScheduler() {
            if (!started && task != null) {
                started = true;
                task.run();
            }
        }

        @Override
        public boolean isSchedulerStarted() {
            return started;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }
    }
}
