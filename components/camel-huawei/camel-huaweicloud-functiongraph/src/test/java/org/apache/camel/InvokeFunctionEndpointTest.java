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
package org.apache.camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.constants.FunctionGraphProperties;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InvokeFunctionEndpointTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(InvokeFunctionTest.class.getName());

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("functionGraphClient")
    FunctionGraphMockClient mockClient = new FunctionGraphMockClient(null);

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:invoke_function")
                        .setProperty(FunctionGraphProperties.XCFFLOGTYPE, constant("tail"))
                        .to("hwcloud-functiongraph:invokeFunction?" +
                            "accessKey=" + testConfiguration.getProperty("accessKey") +
                            "&secretKey=" + testConfiguration.getProperty("secretKey") +
                            "&projectId=" + testConfiguration.getProperty("projectId") +
                            "&endpoint=" + testConfiguration.getProperty("endpoint") +
                            "&functionName=" + testConfiguration.getProperty("functionName") +
                            "&functionPackage=" + testConfiguration.getProperty("functionPackage") +
                            "&ignoreSslVerification=true" +
                            "&functionGraphClient=#functionGraphClient")
                        .log("Invoke function successful")
                        .to("mock:invoke_function_result");
            }
        };
    }

    @Test
    public void testInvokeFunction() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:invoke_function_result");
        mock.expectedMinimumMessageCount(1);
        String sampleBody = "{\n" +
                            "  \"department\": \"sales\",\n" +
                            "  \"vendor\": \"huawei\",\n" +
                            "  \"product\": \"monitors\",\n" +
                            "  \"price\": 20.13,\n" +
                            "  \"quantity\": 20\n" +
                            "}\n";
        template.sendBody("direct:invoke_function", sampleBody);
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertNotNull(responseExchange.getProperty(FunctionGraphProperties.XCFFLOGS));
        assertTrue(responseExchange.getProperty(FunctionGraphProperties.XCFFLOGS).toString().length() > 0);

        assertEquals(
                "{\"orderId\":1621950031517,\"department\":\"sales\",\"vendor\":\"huawei\",\"product\":\"monitors\",\"price\":20.13,\"quantity\":20,\"status\":\"order submitted successfully\"}",
                responseExchange.getIn().getBody(String.class));
        assertEquals(
                "2021-05-25 21:40:31.472+08:00 Start invoke request '1939bbbb-4009-4685-bcc0-2ff0381fa911', version: latest\n" +
                     "    { product: 'monitors',\n" +
                     "      quantity: 20,\n" +
                     "      vendor: 'huawei',\n" +
                     "      price: 20.13,\n" +
                     "      department: 'sales' }\n" +
                     "    2021-05-25 21:40:31.518+08:00 Finish invoke request '1939bbbb-4009-4685-bcc0-2ff0381fa911', duration: 45.204ms, billing duration: 100ms, memory used: 64.383MB.",
                responseExchange.getProperty(FunctionGraphProperties.XCFFLOGS));
    }
}
