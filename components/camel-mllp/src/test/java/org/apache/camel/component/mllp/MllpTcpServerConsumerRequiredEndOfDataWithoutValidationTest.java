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

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.test.mllp.Hl7TestMessageGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class MllpTcpServerConsumerRequiredEndOfDataWithoutValidationTest
        extends TcpServerConsumerEndOfDataAndValidationTestSupport {

    @Override
    boolean validatePayload() {
        return false;
    }

    @Override
    boolean requireEndOfData() {
        return true;
    }

    @Override
    @Test
    public void testInvalidMessage() {
        assertDoesNotThrow(() -> runNthInvalidMessage());
    }

    @Override
    @Test
    public void testNthInvalidMessage() {
        assertDoesNotThrow(() -> runNthInvalidMessage());
    }

    @Override
    @Test
    public void testMessageContainingEmbeddedStartOfBlock() {
        expectedCompleteCount = 1;

        assertDoesNotThrow(() -> runMessageContainingEmbeddedStartOfBlock());
    }

    @Override
    @Test
    public void testNthMessageContainingEmbeddedStartOfBlock() {
        assertDoesNotThrow(() -> runNthMessageContainingEmbeddedStartOfBlock());
    }

    @Override
    @Test
    public void testMessageContainingEmbeddedEndOfBlock() {
        setExpectedCounts();

        NotifyBuilder done = new NotifyBuilder(context()).whenDone(1).create();

        mllpClient.sendFramedData(
                Hl7TestMessageGenerator.generateMessage().replaceFirst("PID", "PID" + MllpProtocolConstants.END_OF_BLOCK));

        assertFalse(done.matches(5, TimeUnit.SECONDS), "Exchange should not have completed");
    }

    @Override
    @Test
    public void testInvalidMessageContainingEmbeddedEndOfBlock() {
        expectedInvalidCount = 1;

        assertDoesNotThrow(() -> runInvalidMessageContainingEmbeddedEndOfBlock());
    }

    @Override
    @Test
    public void testNthMessageContainingEmbeddedEndOfBlock() {
        expectedInvalidCount = 1;

        assertDoesNotThrow(() -> runNthMessageContainingEmbeddedEndOfBlock());
    }

    @Override
    @Test
    public void testInitialMessageWithoutEndOfDataByte() {
        setExpectedCounts();

        mllpClient.setSendEndOfData(false);

        assertDoesNotThrow(() -> mllpClient.sendFramedData(Hl7TestMessageGenerator.generateMessage()));
    }

    @Override
    @Test
    public void testMessageWithoutEndOfDataByte() {
        expectedCompleteCount = 1;
        expectedInvalidCount = 1;

        assertDoesNotThrow(() -> runMessageWithoutEndOfDataByte());
    }
}
