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
package org.apache.camel.dataformat.base64;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public abstract class SpringBase64DataFormatTestBase extends CamelSpringTestSupport {

    protected Base64DataFormat format = new Base64DataFormat();

    @EndpointInject("mock:result")
    private MockEndpoint result;

    protected void runEncoderTest(byte[] raw, byte[] expected) throws Exception {
        result.setExpectedMessageCount(1);

        template.sendBody("direct:startEncode", raw);

        assertMockEndpointsSatisfied();

        byte[] encoded = result.getReceivedExchanges().get(0).getIn().getBody(byte[].class);
        assertArrayEquals(expected, encoded);
    }

    protected void runDecoderTest(byte[] encoded, byte[] expected) throws Exception {
        result.setExpectedMessageCount(1);

        template.sendBody("direct:startDecode", encoded);

        assertMockEndpointsSatisfied();

        byte[] decoded = result.getReceivedExchanges().get(0).getIn().getBody(byte[].class);
        assertArrayEquals(expected, decoded);
    }

}
