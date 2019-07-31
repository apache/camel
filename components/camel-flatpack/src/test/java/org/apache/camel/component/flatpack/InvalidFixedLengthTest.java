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
package org.apache.camel.component.flatpack;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.util.Assert;

@ContextConfiguration
public class InvalidFixedLengthTest extends AbstractJUnit4SpringContextTests {

    @EndpointInject("mock:results")
    protected MockEndpoint results;

    @EndpointInject("mock:error")
    protected MockEndpoint error;

    @Test
    public void testCamel() throws Exception {
        results.expectedMessageCount(0);
        results.assertIsSatisfied();

        error.expectedMessageCount(1);
        error.assertIsSatisfied();

        Exchange e = error.getReceivedExchanges().get(0);
        FlatpackException cause = e.getProperty(Exchange.EXCEPTION_CAUGHT, FlatpackException.class);
        Assert.notNull(cause, "Exception should not be null");

        Assert.hasText("Flatpack has found 4 errors while parsing. Exchange[PEOPLE-FixedLength.txt]", cause.getMessage());
        Assert.hasText("Line:4 Level:2 Desc:LINE TOO LONG. LINE IS 278 LONG. SHOULD BE 277", cause.getMessage());
    }
}
