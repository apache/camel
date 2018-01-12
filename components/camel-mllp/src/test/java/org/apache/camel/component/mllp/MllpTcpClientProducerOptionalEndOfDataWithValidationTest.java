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

public class MllpTcpClientProducerOptionalEndOfDataWithValidationTest extends TcpClientProducerEndOfDataAndValidationTestSupport {
    @Override
    boolean requireEndOfData() {
        return false;
    }

    @Override
    boolean validatePayload() {
        return true;
    }

    @Override
    public void testSendSingleMessageWithoutEndOfData() throws Exception {
        expectedAACount = 1;

        runSendSingleMessageWithoutEndOfData();
    }

    @Override
    public void testSendMultipleMessagesWithoutEndOfDataByte() throws Exception {
        runSendMultipleMessagesWithoutEndOfDataByte(aa);
    }

    @Override
    public void testEmptyAcknowledgement() throws Exception {
        runEmptyAcknowledgement(invalid);
    }

    @Override
    public void testInvalidAcknowledgement() throws Exception {
        runInvalidAcknowledgement(invalid);
    }

    @Override
    public void testMissingEndOfDataByte() throws Exception {
        expectedAACount = 3;

        runMissingEndOfDataByte();
    }

    @Override
    public void testSendMultipleMessagesWithoutSomeEndOfDataByte() throws Exception {
        expectedAACount = 3;

        runSendMultipleMessagesWithoutSomeEndOfDataByte();
    }

    @Override
    public void testInvalidAcknowledgementContainingEmbeddedStartOfBlock() throws Exception {
        expectedAACount = 1;

        runInvalidAcknowledgementContainingEmbeddedEndOfBlockByte();
    }

    @Override
    public void testInvalidAcknowledgementContainingEmbeddedEndOfBlockByte() throws Exception {
        expectedTimeoutCount = 1;

        runInvalidAcknowledgementContainingEmbeddedEndOfBlockByte();
    }

}
