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
package org.apache.camel.component.mllp;

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

public class MllpTcpServerConsumerPipelinedMessagesTest extends CamelTestSupport {
    @RegisterExtension
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock://result")
    MockEndpoint result;

    @Override
    protected RouteBuilder createRouteBuilder() {
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("mllp://%s:%d?minBufferSize=8192", mllpClient.getMllpHost(), mllpClient.getMllpPort())
                        .to(result);
            }
        };
    }

    @Test
    public void testReceiveTwoPipelinedMessagesFromValidationRead() throws Exception {
        assertPipelinedMessagesReceived(1, 2);
    }

    @Test
    public void testReceiveThreePipelinedMessages() throws Exception {
        assertPipelinedMessagesReceived(1, 2, 3);
    }

    @Test
    public void testReceivePipelinedMessagesAfterAcknowledgedMessage() throws Exception {
        mllpClient.connect();
        String firstMessage = Hl7TestMessageGenerator.generateMessage(1);
        result.expectedMessageCount(3);
        result.message(0).body().isEqualTo(firstMessage);
        mllpClient.sendFramedData(firstMessage);
        assertAcknowledgement(1);

        String secondMessage = Hl7TestMessageGenerator.generateMessage(2);
        String thirdMessage = Hl7TestMessageGenerator.generateMessage(3);
        result.message(1).body().isEqualTo(secondMessage);
        result.message(2).body().isEqualTo(thirdMessage);
        mllpClient.sendFramedDataPipelined(secondMessage, thirdMessage);
        assertAcknowledgement(2);
        assertAcknowledgement(3);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    public void testReceivePipelinedMessageAfterJunkContainingStartOfBlock() throws Exception {
        String firstMessage = Hl7TestMessageGenerator.generateMessage(1);
        String secondMessage = Hl7TestMessageGenerator.generateMessage(2);
        result.expectedBodiesReceived(firstMessage, secondMessage);

        mllpClient.sendFramedDataPipelined(
                new byte[] { 'j', 0x0b, 'u', 'n', 'k', 0x1c, 'x' }, firstMessage, secondMessage);
        assertAcknowledgement(1);
        assertAcknowledgement(2);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Test
    public void testReceivePipelinedMessageSpanningReadsAfterLineFeed() throws Exception {
        String firstMessage = Hl7TestMessageGenerator.generateMessage(1);
        StringBuilder secondMessage = new StringBuilder(Hl7TestMessageGenerator.generateMessage(2));
        // Larger than the read buffer, so the second message is incomplete when the first one is processed
        for (int i = 1; secondMessage.length() < 32 * 1024; i++) {
            secondMessage.append("OBX|").append(i).append("|TX|NOTE^Note||Lorem ipsum dolor sit amet||||||F\r");
        }
        result.expectedBodiesReceived(firstMessage, secondMessage.toString());

        // Some senders terminate frames with <FS><CR><LF>
        mllpClient.sendFramedDataPipelined(new byte[] { '\n' }, firstMessage, secondMessage.toString());
        assertAcknowledgement(1);
        assertAcknowledgement(2);
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    private void assertPipelinedMessagesReceived(int... messageNumbers) throws Exception {
        String[] messages = new String[messageNumbers.length];
        result.expectedMessageCount(messageNumbers.length);
        for (int i = 0; i < messageNumbers.length; i++) {
            messages[i] = Hl7TestMessageGenerator.generateMessage(messageNumbers[i]);
            result.message(i).body().isEqualTo(messages[i]);
        }

        mllpClient.sendFramedDataPipelined(messages);
        for (int messageNumber : messageNumbers) {
            assertAcknowledgement(messageNumber);
        }
        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    private void assertAcknowledgement(int messageNumber) throws Exception {
        assertThat(mllpClient.receiveFramedData(),
                containsString(String.format("MSA|AA|%05d", messageNumber)));
    }
}
