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
package org.apache.camel.component.aws2.s3.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class S3PresignerForcePathStyleIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @Test
    public void downloadLinkShouldUsePathStyleWhenForcePathStyleEnabled() {
        Exchange ex = template.request("direct:createDownloadLinkPathStyle", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "CamelUnitTest2");
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_NAME, name.get());
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.createDownloadLink);
            }
        });

        String downloadLink = ex.getMessage().getBody(String.class);
        assertNotNull(downloadLink);
        // With forcePathStyle=true, the URL should use path-style: http://localhost:8080/<bucket>/...
        // instead of virtual-hosted-style: http://<bucket>.localhost:8080/...
        assertTrue(downloadLink.startsWith("http://localhost:8080/" + name.get()),
                "Expected path-style URL starting with http://localhost:8080/" + name.get() + " but got: " + downloadLink);
    }

    @Test
    public void downloadLinkShouldUseVirtualHostedStyleByDefault() {
        Exchange ex = template.request("direct:createDownloadLinkVirtualHosted", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "CamelUnitTest2");
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_NAME, name.get());
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.createDownloadLink);
            }
        });

        String downloadLink = ex.getMessage().getBody(String.class);
        assertNotNull(downloadLink);
        // Without forcePathStyle, the URL should use virtual-hosted-style: http://<bucket>.localhost:8080/...
        assertTrue(downloadLink.startsWith("http://" + name.get() + ".localhost:8080"),
                "Expected virtual-hosted-style URL starting with http://" + name.get()
                                                                                        + ".localhost:8080 but got: "
                                                                                        + downloadLink);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint = "aws2-s3://" + name.get() + "?autoCreateBucket=false";

                from("direct:createDownloadLinkPathStyle")
                        .to(awsEndpoint
                            + "&accessKey=xxx&secretKey=yyy&region=eu-west-1"
                            + "&forcePathStyle=true&uriEndpointOverride=http://localhost:8080");

                from("direct:createDownloadLinkVirtualHosted")
                        .to(awsEndpoint
                            + "&accessKey=xxx&secretKey=yyy&region=eu-west-1"
                            + "&uriEndpointOverride=http://localhost:8080");
            }
        };
    }
}
