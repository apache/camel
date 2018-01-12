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

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.NotifyBuilder;

import org.apache.camel.test.mllp.Hl7TestMessageGenerator;

public class MllpTcpServerConsumerRequiredEndOfDataWithoutValidationTest extends TcpServerConsumerEndOfDataAndValidationTestSupport {

    @Override
    boolean validatePayload() {
        return false;
    }

    @Override
    boolean requireEndOfData() {
        return true;
    }

    @Override
    public void testInvalidMessage() throws Exception {
        runNthInvalidMessage();
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
        expectedInvalidCount = 1;

        runMessageContainingEmbeddedEndOfBlock();
    }

    @Override
    public void testInvalidMessageContainingEmbeddedEndOfBlock() throws Exception {
        expectedInvalidCount = 1;

        runInvalidMessageContainingEmbeddedEndOfBlock();
    }

    @Override
    public void testNthMessageContainingEmbeddedEndOfBlock() throws Exception {
        expectedInvalidCount = 1;

        runNthMessageContainingEmbeddedEndOfBlock();
    }

    @Override
    public void testInitialMessageWithoutEndOfDataByte() throws Exception {
        setExpectedCounts();

        mllpClient.setSendEndOfData(false);

        mllpClient.sendFramedData(Hl7TestMessageGenerator.generateMessage());
    }

    @Override
    public void testMessageWithoutEndOfDataByte() throws Exception {
        expectedCompleteCount = 1;
        expectedInvalidCount = 1;

        runMessageWithoutEndOfDataByte();
    }
}

