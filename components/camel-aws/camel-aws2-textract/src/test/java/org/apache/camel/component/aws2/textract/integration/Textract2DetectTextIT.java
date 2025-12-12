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
package org.apache.camel.component.aws2.textract.integration;

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Must be manually tested.")
public class Textract2DetectTextIT extends CamelTestSupport {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:start")
                        .to("aws2-textract://pippo?useDefaultCredentialsProvider=true&region=eu-west-1&s3Bucket=kamelets-demo&s3Object=SinglePage.pdf&operation=detectDocumentText")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testDetectText() throws InterruptedException {
        result.expectedMessageCount(1);

        DetectDocumentTextResponse s = (DetectDocumentTextResponse) template.requestBody("direct:start", "peppe");

        Awaitility.await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, result.getExchanges().size()));

        assertTrue(s.hasBlocks());
        assertTrue(s.documentMetadata().pages() >= 1);
    }
}
