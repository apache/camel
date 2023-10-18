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
package org.apache.camel.component.mock;

import org.apache.camel.Exchange;

public class ExpectedBodiesAssertionTask implements AssertionTask {

    private final MockEndpoint mockEndpoint;

    protected ExpectedBodiesAssertionTask(MockEndpoint mockEndpoint) {
        this.mockEndpoint = mockEndpoint;
    }

    @Override
    public void assertOnIndex(int i) {
        Exchange exchange = this.mockEndpoint.getReceivedExchange(i);

        Object expectedBody = this.mockEndpoint.expectedBodyValues.get(i);
        Object actualBody = null;
        if (i < this.mockEndpoint.actualBodyValues.size()) {
            actualBody = this.mockEndpoint.actualBodyValues.get(i);
        }
        actualBody = this.mockEndpoint.extractActualValue(exchange, actualBody, expectedBody);
        assertOnExtractedBody(i, expectedBody, actualBody);
    }

    protected void assertOnExtractedBody(int index, Object expectedBody, Object actualBody) {
        this.mockEndpoint.assertEquals("Body of message: " + index, expectedBody, actualBody);
    }

    public void run() {
        for (int i = 0; i < this.mockEndpoint.expectedBodyValues.size(); i++) {
            assertOnIndex(i);
        }
    }
}
