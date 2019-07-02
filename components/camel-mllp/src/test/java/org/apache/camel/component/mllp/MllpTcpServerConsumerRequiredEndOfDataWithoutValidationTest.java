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
import org.junit.Test;

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
    @Test
    public void testInvalidMessage() throws Exception {
        runNthInvalidMessage();
    }

    @Override
    @Test
    public void testNthInvalidMessage() throws Exception {
        runNthInvalidMessage();
    }

    @Override
    @Test
    public void testMessageContainingEmbeddedStartOfBlock() throws Exception {
        expectedCompleteCount = 1;

        runMessageContainingEmbeddedStartOfBlock();
    }


    @Override
    @Test
    public void testNthMessageContainingEmbeddedStartOfBlock() throws Exception {
        runNthMessageContainingEmbeddedStartOfBlock();
    }


    @Override
    @Test
    public void testMessageContainingEmbeddedEndOfBlock() throws Exception {
        setExpectedCounts();

        NotifyBuilder done = new NotifyBuilder(context()).whenDone(1).create();

        mllpClient.sendFramedData(Hl7TestMessageGenerator.generateMessage().replaceFirst("PID", "PID" + MllpProtocolConstants.END_OF_BLOCK));

        assertFalse("Exchange should not have completed", done.matches(5, TimeUnit.SECONDS));
    }

    @Override
    @Test
    public void testInvalidMessageContainingEmbeddedEndOfBlock() throws Exception {
        expectedInvalidCount = 1;

        runInvalidMessageContainingEmbeddedEndOfBlock();
    }

    @Override
    @Test
    public void testNthMessageContainingEmbeddedEndOfBlock() throws Exception {
        expectedInvalidCount = 1;

        runNthMessageContainingEmbeddedEndOfBlock();
    }

    @Override
    @Test
    public void testInitialMessageWithoutEndOfDataByte() throws Exception {
        setExpectedCounts();

        mllpClient.setSendEndOfData(false);

        mllpClient.sendFramedData(Hl7TestMessageGenerator.generateMessage());
    }

    @Override
    @Test
    public void testMessageWithoutEndOfDataByte() throws Exception {
        expectedCompleteCount = 1;
        expectedInvalidCount = 1;

        runMessageWithoutEndOfDataByte();
    }
}

