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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.utils.IoUtils;

public class S3GetObjectDynamicKeyOperationIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "${variable.global:myVar}.txt");
                exchange.getIn().setHeader(AWS2S3Constants.CONTENT_TYPE, "application/text");
                exchange.getIn().setBody("Camel rocks again!");
            }
        });

        template.request("direct:getObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.BUCKET_NAME, name.get());
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "${variable.global:myVar}.txt");
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.getObject);
            }
        });

        Message resp = result.getExchanges().get(0).getMessage();
        assertEquals("Camel rocks again!", new String(IoUtils.toByteArray(resp.getBody(InputStream.class))));
        assertEquals("application/text", resp.getHeader(AWS2S3Constants.CONTENT_TYPE));
        assertEquals("myCamel.txt", resp.getHeader(AWS2S3Constants.PRODUCED_KEY));
        assertEquals(name.get(), resp.getHeader(AWS2S3Constants.PRODUCED_BUCKET_NAME));
        assertNotNull(resp.getHeader(AWS2S3Constants.E_TAG));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setVariable("myVar", "myCamel");

                String awsEndpoint = "aws2-s3://" + name.get() + "?autoCreateBucket=true";

                from("direct:putObject").to(awsEndpoint);

                from("direct:getObject").to(awsEndpoint).to("mock:result");
            }
        };
    }
}
