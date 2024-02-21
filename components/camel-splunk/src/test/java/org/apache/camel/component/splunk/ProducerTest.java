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
package org.apache.camel.component.splunk;

import java.io.IOException;

import com.splunk.Args;
import com.splunk.Index;
import com.splunk.IndexCollection;
import com.splunk.InputCollection;
import com.splunk.TcpInput;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.splunk.event.SplunkEvent;
import org.apache.camel.component.splunk.support.DataWriter;
import org.apache.camel.component.splunk.support.StreamDataWriter;
import org.apache.camel.component.splunk.support.SubmitDataWriter;
import org.apache.camel.component.splunk.support.TcpDataWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
public class ProducerTest extends SplunkMockTestSupport {

    @EndpointInject("splunk://stream")
    protected SplunkEndpoint streamEndpoint;

    @EndpointInject("splunk://submit")
    protected SplunkEndpoint submitEndpoint;

    @EndpointInject("splunk://tcp")
    protected SplunkEndpoint tcpEndpoint;

    @Mock
    private TcpInput input;

    @Mock
    private Index index;

    @Mock
    private IndexCollection indexColl;

    @Mock
    private InputCollection inputCollection;

    @BeforeEach
    public void setup() throws IOException {
        when(service.getIndexes()).thenReturn(indexColl);
        when(service.getInputs()).thenReturn(inputCollection);
        when(input.attach()).thenReturn(socket);
        when(input.getHost()).thenReturn("localhost");
        when(inputCollection.get(anyString())).thenReturn(input);
        when(indexColl.get(anyString())).thenReturn(index);
        when(index.attach(isA(Args.class))).thenReturn(socket);
        when(socket.getOutputStream()).thenReturn(System.out);
    }

    @Test
    public void testStreamWriter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:stream-result");
        mock.setExpectedMessageCount(1);
        SplunkEvent splunkEvent = new SplunkEvent();
        splunkEvent.addPair("key11", "value1");
        splunkEvent.addPair("key22", "value2");
        splunkEvent.addPair("key33", "value3");
        template.sendBody("direct:stream", splunkEvent);
        MockEndpoint.assertIsSatisfied(context);
        Producer streamProducer = streamEndpoint.createProducer();
        assertIsInstanceOf(StreamDataWriter.class, ((SplunkProducer) streamProducer).getDataWriter());
    }

    @Test
    public void testSubmitWriter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:submitresult");
        mock.setExpectedMessageCount(1);
        SplunkEvent splunkEvent = new SplunkEvent();
        splunkEvent.addPair("key1", "value1");
        splunkEvent.addPair("key2", "value2");
        splunkEvent.addPair("key3", "value1");
        template.sendBody("direct:submit", splunkEvent);
        MockEndpoint.assertIsSatisfied(context);
        Producer submitProducer = submitEndpoint.createProducer();
        assertIsInstanceOf(SubmitDataWriter.class, ((SplunkProducer) submitProducer).getDataWriter());
    }

    @Test
    public void testTcpWriter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tcpresult");
        mock.setExpectedMessageCount(1);
        SplunkEvent splunkEvent = new SplunkEvent();
        splunkEvent.addPair("key1", "value1");
        splunkEvent.addPair("key2", "value2");
        splunkEvent.addPair("key3", "value3");
        template.sendBody("direct:tcp", splunkEvent);
        MockEndpoint.assertIsSatisfied(context);
        Producer tcpProducer = tcpEndpoint.createProducer();
        assertIsInstanceOf(TcpDataWriter.class, ((SplunkProducer) tcpProducer).getDataWriter());
    }

    @Test
    public void testTcpWriterWithLocalReceiverPort() throws Exception {
        try {
            tcpEndpoint.getConfiguration().setTcpReceiverLocalPort(-1);
            Producer tcpProducer = tcpEndpoint.createProducer();

            DataWriter dw = ((SplunkProducer) tcpProducer).getDataWriter();
            //connection is created to socket localhost:-1, which has to fail
            Assertions.assertThrows(Exception.class, () -> dw.start());
        } finally {
            tcpEndpoint.getConfiguration().setTcpReceiverLocalPort(null);
        }
    }

    @Test
    public void testTcpWriterWithDifferentHost() throws Exception {
        String host = tcpEndpoint.getConfiguration().getHost();
        try {
            tcpEndpoint.getConfiguration().setHost("foo");
            Producer tcpProducer = tcpEndpoint.createProducer();

            DataWriter dw = ((SplunkProducer) tcpProducer).getDataWriter();
            //connection is created to socket foo:2222, which has to fail
            Assertions.assertThrows(RuntimeException.class, () -> dw.start());
        } finally {
            tcpEndpoint.getConfiguration().setHost(host);
        }
    }

    @Test
    public void testBodyWithoutRawOption() {
        assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:tcp", "foobar"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:stream").to(
                        "splunk://stream?username=foo&password=bar&index=myindex&sourceType=StreamSourceType&source=StreamSource")
                        .to("mock:stream-result");

                from("direct:submit")
                        .to("splunk://submit?username=foo&password=bar&index=myindex&sourceType=testSource&source=test")
                        .to("mock:submitresult");

                from("direct:tcp").to(
                        "splunk://tcp?username=foo&password=bar&tcpReceiverPort=2222&index=myindex&sourceType=testSource&source=test")
                        .to("mock:tcpresult");
            }
        };
    }
}
