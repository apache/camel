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

import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.component.huaweicloud.obs.constants.OBSHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test download objects with all parameters set as default
 */
public class DownloadFunctionalTest extends CamelTestSupport {
    private static final String ACCESS_KEY = "replace_this_with_access_key";
    private static final String SECRET_KEY = "replace_this_with_secret_key";
    private static final String REGION = "replace_this_with_region";
    private static final String BUCKET_NAME = "replace_this_with_bucket_name";
    private static final boolean INCLUDE_FOLDERS = true;
    private static final int NUMBER_TO_CONSUME = 10;

    @BindToRegistry("serviceKeys")
    ServiceKeys serviceKeys = new ServiceKeys(ACCESS_KEY, SECRET_KEY);

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("hwcloud-obs:?" +
                     "serviceKeys=#serviceKeys" +
                     "&region=" + REGION +
                     "&bucketName=" + BUCKET_NAME +
                     "&ignoreSslVerification=true" +
                     "&maxMessagesPerPoll=10" +
                     "&includeFolders=" + INCLUDE_FOLDERS +
                     "&deleteAfterRead=false" +
                     "&moveAfterRead=false")
                        .log("Download objects successful")
                        .to("log:LOG?showAll=true")
                        .to("mock:download_objects_result");
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
    @Disabled("Manually enable this once you configure the parameters in the placeholders above")
    @Test
    public void testListBuckets() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:download_objects_result");
        mock.expectedMinimumMessageCount(NUMBER_TO_CONSUME);
        mock.assertIsSatisfied();
        List<Exchange> exchanges = mock.getExchanges();

        assertTrue(exchanges.size() >= NUMBER_TO_CONSUME);
        for (Exchange exchange : exchanges) {
            assertTrue(exchange.getIn().getHeader(OBSHeaders.OBJECT_KEY, String.class).length() > 0);
            if (exchange.getIn().getHeader(Exchange.CONTENT_LENGTH, Integer.class) > 0) {
                assertNotNull(exchange.getIn().getBody(String.class));
                assertTrue(exchange.getIn().getBody(String.class).length() > 0);
            }
        }
    }
}
