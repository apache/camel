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
package org.apache.camel.component.aws2.textract;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Textract2ProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonTextractClient")
    AmazonAWSTextractMock clientMock = new AmazonAWSTextractMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void detectDocumentTextTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectDocumentText", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Textract2Constants.S3_BUCKET, "testbucket");
                exchange.getIn().setHeader(Textract2Constants.S3_OBJECT, "testobject.pdf");
                exchange.getIn().setBody("test document content");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectDocumentTextResponse resultGet = exchange.getIn().getBody(DetectDocumentTextResponse.class);
        assertNotNull(resultGet);
        assertNotNull(resultGet.blocks());
        assertEquals(1, resultGet.blocks().size());
        assertEquals("Mock detected text", resultGet.blocks().get(0).text());

    }

    @Test
    public void detectDocumentTextPojoTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectDocumentTextPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                Document document = Document.builder()
                        .bytes(SdkBytes.fromByteArray("test document content".getBytes()))
                        .build();

                exchange.getIn()
                        .setBody(DetectDocumentTextRequest.builder()
                                .document(document)
                                .build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectDocumentTextResponse resultGet = exchange.getIn().getBody(DetectDocumentTextResponse.class);
        assertNotNull(resultGet);
        assertNotNull(resultGet.blocks());
        assertEquals(1, resultGet.blocks().size());
        assertEquals("Mock detected text", resultGet.blocks().get(0).text());

    }

    @Test
    public void detectDocumentTextTestOptions() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectDocumentTextOptions", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("test document content");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectDocumentTextResponse resultGet = exchange.getIn().getBody(DetectDocumentTextResponse.class);
        assertNotNull(resultGet);
        assertNotNull(resultGet.blocks());
        assertEquals(1, resultGet.blocks().size());
        assertEquals("Mock detected text", resultGet.blocks().get(0).text());

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:detectDocumentText")
                        .to("aws2-textract://test?textractClient=#amazonTextractClient&operation=detectDocumentText")
                        .to("mock:result");
                from("direct:detectDocumentTextPojo").to(
                        "aws2-textract://test?textractClient=#amazonTextractClient&operation=detectDocumentText&pojoRequest=true")
                        .to("mock:result");
                from("direct:detectDocumentTextOptions").to(
                        "aws2-textract://test?textractClient=#amazonTextractClient&operation=detectDocumentText&s3Bucket=testbucket&s3Object=testobject.pdf")
                        .to("mock:result");
            }
        };
    }
}
