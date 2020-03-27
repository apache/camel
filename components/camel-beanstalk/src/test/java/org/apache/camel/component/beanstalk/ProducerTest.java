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
package org.apache.camel.component.beanstalk;

import com.surftools.BeanstalkClient.BeanstalkException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.beanstalk.processors.BuryCommand;
import org.apache.camel.component.beanstalk.processors.DeleteCommand;
import org.apache.camel.component.beanstalk.processors.PutCommand;
import org.apache.camel.component.beanstalk.processors.ReleaseCommand;
import org.apache.camel.component.beanstalk.processors.TouchCommand;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisabledOnOs(OS.WINDOWS)
public class ProducerTest extends BeanstalkMockTestSupport {

    @EndpointInject("beanstalk:tube")
    protected BeanstalkEndpoint endpoint;

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce("direct:start")
    protected ProducerTemplate direct;

    private final String testMessage = "hello, world";

    @Test
    void testPut() throws Exception {
        final long priority = BeanstalkComponent.DEFAULT_PRIORITY;
        final int delay = BeanstalkComponent.DEFAULT_DELAY;
        final int timeToRun = BeanstalkComponent.DEFAULT_TIME_TO_RUN;
        final byte[] payload = Helper.stringToBytes(testMessage);
        final long jobId = 111;

        when(client.put(priority, delay, timeToRun, payload)).thenReturn(jobId);

        final Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(PutCommand.class));

        // TODO: SetBodyProcessor(?)
        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> exchange1.getIn().setBody(testMessage));

        assertEquals(Long.valueOf(jobId), exchange.getIn().getHeader(Headers.JOB_ID, Long.class), "Job ID in exchange");
        verify(client).put(priority, delay, timeToRun, payload);
    }

    @Test
    void testPutOut() throws Exception {
        final long priority = BeanstalkComponent.DEFAULT_PRIORITY;
        final int delay = BeanstalkComponent.DEFAULT_DELAY;
        final int timeToRun = BeanstalkComponent.DEFAULT_TIME_TO_RUN;
        final byte[] payload = Helper.stringToBytes(testMessage);
        final long jobId = 111;

        when(client.put(priority, delay, timeToRun, payload)).thenReturn(jobId);

        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(PutCommand.class));

        // TODO: SetBodyProcessor(?)
        final Exchange exchange = template.send(endpoint, ExchangePattern.InOut, exchange1 -> exchange1.getIn().setBody(testMessage));

        assertEquals(Long.valueOf(jobId), exchange.getMessage().getHeader(Headers.JOB_ID, Long.class), "Job ID in exchange");
        verify(client).put(priority, delay, timeToRun, payload);
    }

    @Test
    void testPutWithHeaders() throws Exception {
        final long priority = 111;
        final int delay = 5;
        final int timeToRun = 65;
        final byte[] payload = Helper.stringToBytes(testMessage);
        final long jobId = 111;

        when(client.put(priority, delay, timeToRun, payload)).thenReturn(jobId);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(PutCommand.class));

        // TODO: SetBodyProcessor(?)
        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> {
            exchange1.getIn().setHeader(Headers.PRIORITY, priority);
            exchange1.getIn().setHeader(Headers.DELAY, delay);
            exchange1.getIn().setHeader(Headers.TIME_TO_RUN, timeToRun);
            exchange1.getIn().setBody(testMessage);
        });

        assertEquals(Long.valueOf(jobId), exchange.getIn().getHeader(Headers.JOB_ID, Long.class), "Job ID in exchange");
        verify(client).put(priority, delay, timeToRun, payload);
    }

    @Test
    void testBury() throws Exception {
        final long priority = BeanstalkComponent.DEFAULT_PRIORITY;
        final long jobId = 111;

        endpoint.setCommand(BeanstalkCommand.bury);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(BuryCommand.class));

        when(client.bury(jobId, priority)).thenReturn(true);

        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> exchange1.getIn().setHeader(Headers.JOB_ID, jobId));

        assertEquals(Boolean.TRUE, exchange.getIn().getHeader(Headers.RESULT, Boolean.class), "Op result");
        assertEquals(Long.valueOf(jobId), exchange.getIn().getHeader(Headers.JOB_ID, Long.class), "Job ID in exchange");
        verify(client).bury(jobId, priority);
    }

    @Test
    void testBuryNoJobId() throws Exception {
        endpoint.setCommand(BeanstalkCommand.bury);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(BuryCommand.class));

        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> {
        });

        assertTrue(exchange.isFailed(), "Exchange failed");

        verify(client, never()).bury(anyLong(), anyLong());
    }

    @Test
    void testBuryWithHeaders() throws Exception {
        final long priority = 1000;
        final long jobId = 111;

        endpoint.setCommand(BeanstalkCommand.bury);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(BuryCommand.class));

        when(client.bury(jobId, priority)).thenReturn(true);

        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Headers.PRIORITY, priority);
                exchange.getIn().setHeader(Headers.JOB_ID, jobId);
            }
        });

        assertEquals(Boolean.TRUE, exchange.getIn().getHeader(Headers.RESULT, Boolean.class), "Op result");
        assertEquals(Long.valueOf(jobId), exchange.getIn().getHeader(Headers.JOB_ID, Long.class), "Job ID in exchange");
        verify(client).bury(jobId, priority);
    }

    @Test
    void testDelete() throws Exception {
        final long jobId = 111;

        endpoint.setCommand(BeanstalkCommand.delete);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(DeleteCommand.class));

        when(client.delete(jobId)).thenReturn(true);

        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> exchange1.getIn().setHeader(Headers.JOB_ID, jobId));

        assertEquals(Boolean.TRUE, exchange.getIn().getHeader(Headers.RESULT, Boolean.class), "Op result");
        assertEquals(Long.valueOf(jobId), exchange.getIn().getHeader(Headers.JOB_ID, Long.class), "Job ID in exchange");
        verify(client).delete(jobId);
    }

    @Test
    void testDeleteNoJobId() throws Exception {
        endpoint.setCommand(BeanstalkCommand.delete);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(DeleteCommand.class));

        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> {
        });

        assertTrue(exchange.isFailed(), "Exchange failed");

        verify(client, never()).delete(anyLong());
    }

    @Test
    void testRelease() throws Exception {
        final long priority = BeanstalkComponent.DEFAULT_PRIORITY;
        final int delay = BeanstalkComponent.DEFAULT_DELAY;
        final long jobId = 111;

        endpoint.setCommand(BeanstalkCommand.release);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(ReleaseCommand.class));

        when(client.release(jobId, priority, delay)).thenReturn(true);

        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> exchange1.getIn().setHeader(Headers.JOB_ID, jobId));

        assertEquals(Boolean.TRUE, exchange.getIn().getHeader(Headers.RESULT, Boolean.class), "Op result");
        assertEquals(Long.valueOf(jobId), exchange.getIn().getHeader(Headers.JOB_ID, Long.class), "Job ID in exchange");
        verify(client).release(jobId, priority, delay);
    }

    @Test
    void testReleaseNoJobId() throws Exception {
        endpoint.setCommand(BeanstalkCommand.release);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(ReleaseCommand.class));

        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> {
        });

        assertTrue(exchange.isFailed(), "Exchange failed");

        verify(client, never()).release(anyLong(), anyLong(), anyInt());
    }

    @Test
    void testReleaseWithHeaders() throws Exception {
        final long priority = 1001;
        final int delay = 124;
        final long jobId = 111;

        endpoint.setCommand(BeanstalkCommand.release);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(ReleaseCommand.class));

        when(client.release(jobId, priority, delay)).thenReturn(true);

        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> {
            exchange1.getIn().setHeader(Headers.JOB_ID, jobId);
            exchange1.getIn().setHeader(Headers.PRIORITY, priority);
            exchange1.getIn().setHeader(Headers.DELAY, delay);
        });

        assertEquals(Boolean.TRUE, exchange.getIn().getHeader(Headers.RESULT, Boolean.class), "Op result");
        assertEquals(Long.valueOf(jobId), exchange.getIn().getHeader(Headers.JOB_ID, Long.class), "Job ID in exchange");
        verify(client).release(jobId, priority, delay);
    }

    @Test
    void testTouch() throws Exception {
        final long jobId = 111;

        endpoint.setCommand(BeanstalkCommand.touch);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(TouchCommand.class));

        when(client.touch(jobId)).thenReturn(true);

        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> exchange1.getIn().setHeader(Headers.JOB_ID, jobId));

        assertEquals(Boolean.TRUE, exchange.getIn().getHeader(Headers.RESULT, Boolean.class), "Op result");
        assertEquals(Long.valueOf(jobId), exchange.getIn().getHeader(Headers.JOB_ID, Long.class), "Job ID in exchange");
        verify(client).touch(jobId);
    }

    @Test
    void testTouchNoJobId() throws Exception {
        endpoint.setCommand(BeanstalkCommand.touch);
        Producer producer = endpoint.createProducer();
        assertNotNull(producer, "Producer");
        assertThat("Producer class", producer, instanceOf(BeanstalkProducer.class));
        assertThat("Processor class", ((BeanstalkProducer) producer).getCommand(), instanceOf(TouchCommand.class));

        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> {
        });

        assertTrue(exchange.isFailed(), "Exchange failed");

        verify(client, never()).touch(anyLong());
    }

    @Test
    void testHeaderOverride() throws Exception {
        final long priority = 1020;
        final int delay = 50;
        final int timeToRun = 75;
        final byte[] payload = Helper.stringToBytes(testMessage);
        final long jobId = 113;

        when(client.put(priority, delay, timeToRun, payload)).thenReturn(jobId);

        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.allMessages().body().isEqualTo(testMessage);
        resultEndpoint.allMessages().header(Headers.JOB_ID).isEqualTo(jobId);

        direct.sendBodyAndHeader(testMessage, Headers.TIME_TO_RUN, timeToRun);
        resultEndpoint.assertIsSatisfied();

        final Long jobIdIn = resultEndpoint.getReceivedExchanges().get(0).getIn().getHeader(Headers.JOB_ID, Long.class);
        assertNotNull(jobIdIn, "Job ID in 'In' message");

        verify(client).put(priority, delay, timeToRun, payload);
    }

    @Test
    void test1BeanstalkException() throws Exception {
        final long priority = 1020;
        final int delay = 50;
        final int timeToRun = 75;
        final byte[] payload = Helper.stringToBytes(testMessage);
        final long jobId = 113;

        when(client.put(priority, delay, timeToRun, payload))
                .thenThrow(new BeanstalkException("test"))
                .thenReturn(jobId);

        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.allMessages().body().isEqualTo(testMessage);
        resultEndpoint.allMessages().header(Headers.JOB_ID).isEqualTo(jobId);

        direct.sendBodyAndHeader(testMessage, Headers.TIME_TO_RUN, timeToRun);
        resultEndpoint.assertIsSatisfied();

        final Long jobIdIn = resultEndpoint.getReceivedExchanges().get(0).getIn().getHeader(Headers.JOB_ID, Long.class);
        assertNotNull(jobIdIn, "Job ID in 'In' message");

        verify(client, times(1)).close();
        verify(client, times(2)).put(priority, delay, timeToRun, payload);
    }

    @Test
    void test2BeanstalkException() throws Exception {
        final long jobId = 111;

        when(client.touch(jobId))
                .thenThrow(new BeanstalkException("test"));

        endpoint.setCommand(BeanstalkCommand.touch);
        final Exchange exchange = template.send(endpoint, ExchangePattern.InOnly, exchange1 -> exchange1.getIn().setHeader(Headers.JOB_ID, jobId));

        assertTrue(exchange.isFailed(), "Exchange failed");

        verify(client, times(2)).touch(jobId);
        verify(client, times(1)).close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("beanstalk:tube?jobPriority=1020&jobDelay=50&jobTimeToRun=65").to("mock:result");
            }
        };
    }
}
