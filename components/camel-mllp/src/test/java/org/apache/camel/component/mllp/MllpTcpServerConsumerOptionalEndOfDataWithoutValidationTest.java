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

package org.apache.camel.component.mllp;

import org.apache.camel.component.mllp.internal.Hl7Util;

import org.apache.camel.test.mllp.Hl7TestMessageGenerator;

import org.junit.Test;

public class MllpTcpServerConsumerOptionalEndOfDataWithoutValidationTest extends TcpServerConsumerEndOfDataAndValidationTestSupport {

    @Override
    boolean validatePayload() {
        return false;
    }

    @Override
    boolean requireEndOfData() {
        return false;
    }

    @Override
    public void testInvalidMessage() throws Exception {
        expectedCompleteCount = 1;

        runInvalidMessage();
    }

    @Override
    public void testNthInvalidMessage() throws Exception {
        runNthInvalidMessage();
    }

    @Override
    public void testMessageContainingEmbeddedStartOfBlock() throws Exception {
        expectedCompleteCount = 1;

        runMessageContainingEmbeddedStartOfBlock();
    }


    @Override
    public void testNthMessageContainingEmbeddedStartOfBlock() throws Exception {
        runNthMessageContainingEmbeddedStartOfBlock();
    }


    @Override
    public void testMessageContainingEmbeddedEndOfBlock() throws Exception {
        expectedCompleteCount = 1;

        runMessageContainingEmbeddedEndOfBlock();
    }

    @Override
    public void testNthMessageContainingEmbeddedEndOfBlock() throws Exception {
        runNthMessageContainingEmbeddedEndOfBlock();
    }

    @Override
    public void testInvalidMessageContainingEmbeddedEndOfBlock() throws Exception {
        runInvalidMessageContainingEmbeddedEndOfBlock();
    }

    @Test
    public void testInitialMessageWithoutEndOfDataByte() throws Exception {
        expectedCompleteCount = 1;

        setExpectedCounts();

        mllpClient.setSendEndOfData(false);

        mllpClient.sendFramedData(Hl7TestMessageGenerator.generateMessage());
    }

    @Test
    public void testMessageWithoutEndOfDataByte() throws Exception {
        expectedCompleteCount = 2;

        setExpectedCounts();

        mllpClient.sendMessageAndWaitForAcknowledgement(Hl7TestMessageGenerator.generateMessage());

        mllpClient.setSendEndOfData(false);

        mllpClient.sendFramedData(Hl7TestMessageGenerator.generateMessage());
    }
}

