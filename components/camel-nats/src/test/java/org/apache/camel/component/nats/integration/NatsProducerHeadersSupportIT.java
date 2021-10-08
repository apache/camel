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
package org.apache.camel.component.nats.integration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

class NatsProducerHeadersSupportIT extends NatsITSupport {

    private static final String HEADER_KEY_1 = "header1";

    private static final String HEADER_KEY_2 = "header2";

    private static final String HEADER_VALUE_1 = "value1";

    private static final String HEADER_VALUE_2 = "value2";

    private static final String HEADER_VALUE_3 = "value3";

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    void testNatsProducerShouldForwardHeaders() throws InterruptedException {

        this.mockResultEndpoint.expectedHeaderReceived(HEADER_KEY_1, HEADER_VALUE_1);
        this.mockResultEndpoint.expectedHeaderReceived(HEADER_KEY_2,
                Arrays.asList(HEADER_VALUE_2, HEADER_VALUE_3));

        this.template.setDefaultEndpoint(this.mockResultEndpoint);
        final Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_KEY_1, HEADER_VALUE_1);
        headers.put(HEADER_KEY_2, Arrays.asList(HEADER_VALUE_2, HEADER_VALUE_3));

        this.template.requestBodyAndHeaders("test", headers);

        this.mockResultEndpoint.assertIsSatisfied();
    }
}
