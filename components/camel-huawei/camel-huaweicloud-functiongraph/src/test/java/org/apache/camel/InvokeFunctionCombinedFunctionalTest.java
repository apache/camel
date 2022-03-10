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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InvokeFunctionCombinedFunctionalTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(InvokeFunctionCombinedFunctionalTest.class.getName());

    private static final String ACCESS_KEY = "replace_this_with_access_key";
    private static final String SECRET_KEY = "replace_this_with_secret_key";
    private static final String FUNCTION_NAME = "replace_this_with_function_name";
    private static final String FUNCTION_PACKAGE = "replace_this_with_function_package";
    private static final String PROJECT_ID = "replace_this_with_project_id";
    private static final String ENDPOINT = "replace_this_with_endpoint";

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:invoke_function")
                        .setProperty(FunctionGraphProperties.XCFFLOGTYPE, constant("tail"))
                        .setProperty(FunctionGraphProperties.OPERATION, constant("invokeFunction"))
                        .setProperty(FunctionGraphProperties.FUNCTION_NAME, constant(FUNCTION_NAME))
                        .setProperty(FunctionGraphProperties.FUNCTION_PACKAGE, constant(FUNCTION_PACKAGE))
                        .to("hwcloud-functiongraph:dummy-operation?" +
                            "accessKey=" + ACCESS_KEY +
                            "&secretKey=" + SECRET_KEY +
                            "&functionName=dummy-function-name" +
                            "&functionPackage=dummy-function-package" +
                            "&projectId=" + PROJECT_ID +
                            "&region=dummy-region" +
                            "&endpoint=" + ENDPOINT +
                            "&ignoreSslVerification=true")
                        .log("Invoke function successful")
                        .to("log:LOG?showAll=true")
                        .to("mock:invoke_function_result");
            }
        };
    }

    /**
     * The following test cases should be manually enabled to perform test against the actual HuaweiCloud FunctionGraph
     * server with real user credentials. To perform this test, manually comment out the @Ignore annotation and enter
     * relevant service parameters in the placeholders above (static variables of this test class)
     *
     * @throws Exception
     */
    @Disabled("Manually enable this once you configure the parameters in the placeholders above")
    @Test
    public void testInvokeFunction() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:invoke_function_result");
        mock.expectedMinimumMessageCount(1);
        String sampleBody = "replace_with_your_body";
        template.sendBody("direct:invoke_function", sampleBody);
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertNotNull(responseExchange.getProperty(FunctionGraphProperties.XCFFLOGS));
        assertTrue(responseExchange.getProperty(FunctionGraphProperties.XCFFLOGS).toString().length() > 0);
    }
}
