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
package org.apache.camel.component.validator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ValidatorDtdAccessOnTest extends ValidatorDtdAccessAbstractTest {

    public ValidatorDtdAccessOnTest() {
        super(true);
    }

    /**
     * Tests that external DTD call is executed for StringSource by expecting an
     * UnkonwHostException.
     */
    @Test
    public void testInvalidMessageWithExternalDTDStringSource() throws Exception {
        unknownHostExceptionEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start", ssrfPayloud);

        MockEndpoint.assertIsSatisfied(validEndpoint, unknownHostExceptionEndpoint, finallyEndpoint);
    }

    /**
     * Tests that external DTD call is executed for StreamSourceby expecting an
     * UnknownHostException.
     */
    @Test
    public void testInvalidMessageWithExternalDTDStreamSource() throws Exception {
        unknownHostExceptionEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);
        InputStream is = new ByteArrayInputStream(ssrfPayloud.getBytes(StandardCharsets.UTF_8));
        template.sendBody("direct:start", is);

        MockEndpoint.assertIsSatisfied(validEndpoint, unknownHostExceptionEndpoint, finallyEndpoint);
    }

    /**
     * Tests that XXE is possible for StreamSource by expecting an
     * UnkonwHostException.
     */
    @Test
    public void testInvalidMessageXXESourceStream() throws Exception {
        unknownHostExceptionEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);
        InputStream is = new ByteArrayInputStream(xxePayloud.getBytes(StandardCharsets.UTF_8));
        template.sendBody("direct:start", is);

        MockEndpoint.assertIsSatisfied(validEndpoint, unknownHostExceptionEndpoint, finallyEndpoint);
    }

}
