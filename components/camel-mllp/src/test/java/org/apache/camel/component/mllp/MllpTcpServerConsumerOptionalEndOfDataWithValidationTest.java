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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("https://issues.apache.org/jira/browse/CAMEL-20215")
public class MllpTcpServerConsumerOptionalEndOfDataWithValidationTest
        extends TcpServerConsumerEndOfDataAndValidationTestSupport {

    @Override
    boolean validatePayload() {
        return true;
    }

    @Override
    boolean requireEndOfData() {
        return false;
    }

    @Override
    @Test
    public void testInvalidMessage() {
        expectedInvalidCount = 1;

        assertDoesNotThrow(this::runInvalidMessage);
    }

    @Override
    @Test
    public void testNthInvalidMessage() {
        expectedInvalidCount = 1;

        assertDoesNotThrow(this::runNthInvalidMessage);
    }

    @Override
    @Test
    public void testMessageContainingEmbeddedStartOfBlock() {
        expectedInvalidCount = 1;

        assertDoesNotThrow(this::runMessageContainingEmbeddedStartOfBlock);
    }

    @Override
    @Test
    public void testNthMessageContainingEmbeddedStartOfBlock() {
        expectedInvalidCount = 1;

        assertDoesNotThrow(this::runNthMessageContainingEmbeddedStartOfBlock);
    }

    @Override
    @Test
    public void testMessageContainingEmbeddedEndOfBlock() {
        expectedInvalidCount = 1;

        setExpectedCounts();

        NotifyBuilder done = new NotifyBuilder(context()).whenDone(1).create();

        mllpClient.sendFramedData(
                Hl7TestMessageGenerator.generateMessage().replaceFirst("PID", "PID" + MllpProtocolConstants.END_OF_BLOCK));

        assertTrue(done.matches(5, TimeUnit.SECONDS), "Exchange should have completed");
    }

    @Override
    @Test
    public void testInvalidMessageContainingEmbeddedEndOfBlock() {
        expectedInvalidCount = 1;

        assertDoesNotThrow(this::runInvalidMessageContainingEmbeddedEndOfBlock);
    }

    @Override
    @Test
    public void testNthMessageContainingEmbeddedEndOfBlock() {
        expectedInvalidCount = 1;

        assertDoesNotThrow(this::runNthMessageContainingEmbeddedEndOfBlock);
    }

    @Override
    @Test
    public void testMessageWithoutEndOfDataByte() {
        expectedCompleteCount = 1;

        assertDoesNotThrow(this::runMessageWithoutEndOfDataByte);
    }
}
