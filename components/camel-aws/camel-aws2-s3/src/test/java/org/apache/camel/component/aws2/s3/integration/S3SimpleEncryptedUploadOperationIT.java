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

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.S3Object;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class S3SimpleEncryptedUploadOperationIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @EndpointInject("mock:resultGet")
    private MockEndpoint resultGet;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);
        resultGet.expectedMessageCount(1);

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "camel.txt");
                exchange.getIn().setBody("Camel rocks!");
            }
        });

        template.request("direct:listObjects", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_NAME, "mycamel");
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
            }
        });

        Exchange c = template.request("direct:getObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "camel.txt");
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.getObject);
            }
        });

        List<S3Object> resp = result.getExchanges().get(0).getMessage().getBody(List.class);
        assertEquals(1, resp.size());
        assertEquals("camel.txt", resp.get(0).key());

        assertEquals("Camel rocks!", c.getIn().getBody(String.class));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String key = createKmsKey();
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint = "aws2-s3://mycamel?autoCreateBucket=true&useAwsKMS=true&awsKMSKeyId=" + key;

                from("direct:putObject").to(awsEndpoint);

                from("direct:listObjects").to(awsEndpoint).to("mock:result");

                from("direct:getObject").to("aws2-s3://mycamel?autoCreateBucket=true").to("mock:resultGet");

            }
        };
    }
}
