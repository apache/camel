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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class MllpTcpClientProducerOptionalEndOfDataWithValidationTest
        extends TcpClientProducerEndOfDataAndValidationTestSupport {
    @Override
    boolean requireEndOfData() {
        return false;
    }

    @Override
    boolean validatePayload() {
        return true;
    }

    @Override
    @Test
    public void testSendSingleMessageWithoutEndOfData() {
        expectedAACount = 1;

        assertDoesNotThrow(() -> runSendSingleMessageWithoutEndOfData());
    }

    @Override
    @Test
    public void testSendMultipleMessagesWithoutEndOfDataByte() {
        assertDoesNotThrow(() -> runSendMultipleMessagesWithoutEndOfDataByte(aa));
    }

    @Override
    @Test
    public void testEmptyAcknowledgement() {
        assertDoesNotThrow(() -> runEmptyAcknowledgement(invalid));
    }

    @Override
    @Test
    public void testInvalidAcknowledgement() {
        assertDoesNotThrow(() -> runInvalidAcknowledgement(invalid));
    }

    @Override
    @Test
    public void testMissingEndOfDataByte() {
        expectedAACount = 3;

        assertDoesNotThrow(() -> runMissingEndOfDataByte());
    }

    @Override
    @Test
    public void testSendMultipleMessagesWithoutSomeEndOfDataByte() {
        expectedAACount = 3;

        assertDoesNotThrow(() -> runSendMultipleMessagesWithoutSomeEndOfDataByte());
    }

    @Override
    @Test
    public void testInvalidAcknowledgementContainingEmbeddedStartOfBlock() {
        expectedAACount = 1;

        assertDoesNotThrow(() -> runInvalidAcknowledgementContainingEmbeddedEndOfBlockByte());
    }

    @Override
    @Test
    public void testInvalidAcknowledgementContainingEmbeddedEndOfBlockByte() {
        expectedTimeoutCount = 1;

        assertDoesNotThrow(() -> runInvalidAcknowledgementContainingEmbeddedEndOfBlockByte());
    }

}
