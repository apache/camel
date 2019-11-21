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
package org.apache.camel.component.aws.s3;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class S3ComponentDownloadLinkTest extends CamelTestSupport {

    @BindToRegistry("amazonS3Client")
    AmazonS3ClientMock clientMock = new AmazonS3ClientMock();

    @EndpointInject("direct:downloadLink")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put(S3Constants.KEY, "test");
        template.sendBodyAndHeaders("direct:downloadLink", headers);
        assertMockEndpointsSatisfied();

        assertResultExchange(result.getExchanges().get(0));

    }

    private void assertResultExchange(Exchange resultExchange) {
        String dlLink = resultExchange.getIn().getHeader(S3Constants.DOWNLOAD_LINK, String.class);
        assertEquals("http://aws.amazonas.s3/file.zip", dlLink);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String awsEndpoint = "aws-s3://mycamelbucket?amazonS3Client=#amazonS3Client&operation=downloadLink";

                from("direct:downloadLink").to(awsEndpoint).to("mock:result");

            }
        };
    }
}
