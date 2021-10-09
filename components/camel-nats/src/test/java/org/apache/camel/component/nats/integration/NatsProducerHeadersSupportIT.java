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

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.junit.jupiter.api.Test;

class NatsProducerHeadersSupportIT extends NatsITSupport {

    private static final String HEADER_KEY_1 = "header1";

    private static final String HEADER_KEY_2 = "header2";

    private static final String HEADER_VALUE_1 = "value1";

    private static final String HEADER_VALUE_2 = "value2";

    private static final String HEADER_VALUE_3 = "value3";

    private static final String CAMEL_HEADER_KEY_1 = "CamelReactiveStreamsEventType";

    private static final String CAMEL_HEADER_KEY_2 = "org.apache.camel.test";

    @EndpointInject("mock:result")
    private MockEndpoint mockResultEndpoint;

    @BindToRegistry("customHeaderFilterStrategy")
    private final ConsumerHeaderFilterStrategy headerFilterStrategy = new ConsumerHeaderFilterStrategy();

    @Test
    void testNatsProducerShouldForwardHeaders() throws InterruptedException {

        this.mockResultEndpoint.expectedHeaderReceived(HEADER_KEY_1, HEADER_VALUE_1);
        this.mockResultEndpoint.expectedHeaderReceived(HEADER_KEY_2,
                Arrays.asList(HEADER_VALUE_2, HEADER_VALUE_3));

        this.template.setDefaultEndpoint(this.mockResultEndpoint);
        final Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_KEY_1, HEADER_VALUE_1);
        headers.put(HEADER_KEY_2, Arrays.asList(HEADER_VALUE_2, HEADER_VALUE_3));
        headers.put(CAMEL_HEADER_KEY_1, HEADER_VALUE_2);
        headers.put(CAMEL_HEADER_KEY_2, HEADER_VALUE_2);

        this.template.requestBodyAndHeaders("test", headers);

        this.mockResultEndpoint.assertIsSatisfied();
        this.mockResultEndpoint.message(0).header(CAMEL_HEADER_KEY_1).isNull();
        this.mockResultEndpoint.message(0).header(CAMEL_HEADER_KEY_2).isNull();
    }

    private class ConsumerHeaderFilterStrategy extends DefaultHeaderFilterStrategy {
        ConsumerHeaderFilterStrategy() {
            // allow all outbound headers to pass through except the camels one
            getOutFilter().addAll(Arrays.asList(DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH));
        }
    }

}
