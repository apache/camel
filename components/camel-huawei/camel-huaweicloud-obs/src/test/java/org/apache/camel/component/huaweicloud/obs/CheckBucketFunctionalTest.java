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
package org.apache.camel.component.huaweicloud.obs;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.obs.constants.OBSProperties;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

public class CheckBucketFunctionalTest extends CamelTestSupport {

    private static final String AUTHENTICATION_KEY = "replace_this_with_authentication_key";
    private static final String SECRET_KEY = "replace_this_with_secret_key";
    private static final String REGION = "replace_this_with_region";
    private static final String BUCKET_NAME = "replace_this_with_bucket_name";

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:check_bucket")
                        .to("hwcloud-obs:checkBucketExists?" +
                            "authenticationKey=" + AUTHENTICATION_KEY +
                            "&secretKey=" + SECRET_KEY +
                            "&region=" + REGION +
                            "&bucketName=" + BUCKET_NAME +
                            "&ignoreSslVerification=true")
                        .log("Check bucket exists successful")
                        .to("log:LOG?showAll=true")
                        .to("mock:check_bucket_result");
            }
        };
    }

    /**
     * The following test cases should be manually enabled to perform test against the actual HuaweiCloud OBS server
     * with real user credentials. To perform this test, manually comment out the @Ignore annotation and enter relevant
     * service parameters in the placeholders above (static variables of this test class)
     *
     * @throws Exception
     */
    @Ignore("Manually enable this once you configure the parameters in the placeholders above")
    @Test
    public void testListBuckets() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:check_bucket_result");
        mock.expectedMinimumMessageCount(1);
        template.sendBody("direct:check_bucket", "sampleBody");
        Exchange responseExchange = mock.getExchanges().get(0);

        mock.assertIsSatisfied();

        assertTrue(responseExchange.getProperty(OBSProperties.BUCKET_EXISTS, boolean.class));
    }
}
