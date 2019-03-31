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

import org.junit.Test;

public class MllpTcpClientProducerRequiredEndOfDataWithValidationTest extends TcpClientProducerEndOfDataAndValidationTestSupport {

    @Override
    boolean requireEndOfData() {
        return true;
    }


    @Override
    boolean validatePayload() {
        return false;
    }


    @Override
    @Test
    public void testSendSingleMessageWithoutEndOfData() throws Exception {
        expectedTimeoutCount = 1;

        runSendSingleMessageWithoutEndOfData();
    }

    @Override
    @Test
    public void testSendMultipleMessagesWithoutEndOfDataByte() throws Exception {
        runSendMultipleMessagesWithoutEndOfDataByte(ackTimeoutError);
    }

    @Override
    @Test
    public void testEmptyAcknowledgement() throws Exception {
        runEmptyAcknowledgement(aa);
    }

    @Override
    @Test
    public void testInvalidAcknowledgement() throws Exception {
        runInvalidAcknowledgement(aa);
    }

    @Override
    @Test
    public void testMissingEndOfDataByte() throws Exception {
        expectedAACount = 2;
        expectedTimeoutCount = 1;

        runMissingEndOfDataByte();
    }

    @Override
    @Test
    public void testInvalidAcknowledgementContainingEmbeddedStartOfBlock() throws Exception {
        expectedAACount = 1;

        runInvalidAcknowledgementContainingEmbeddedEndOfBlockByte();
    }

    @Override
    @Test
    public void testInvalidAcknowledgementContainingEmbeddedEndOfBlockByte() throws Exception {
        expectedTimeoutCount = 1;

        runInvalidAcknowledgementContainingEmbeddedEndOfBlockByte();
    }

    @Override
    @Test
    public void testSendMultipleMessagesWithoutSomeEndOfDataByte() throws Exception {
        expectedAACount = 2;
        expectedTimeoutCount = 1;

        runSendMultipleMessagesWithoutSomeEndOfDataByte();
    }

}
