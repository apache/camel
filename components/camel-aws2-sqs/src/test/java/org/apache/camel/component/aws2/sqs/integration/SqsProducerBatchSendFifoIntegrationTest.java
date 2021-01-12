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
package org.apache.camel.component.aws2.sqs.integration;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.apache.camel.test.infra.aws2.common.SystemPropertiesAWSCredentialsProvider;
import org.apache.camel.test.infra.aws2.common.TestAWSCredentialsProvider;
import org.apache.camel.test.infra.aws2.services.AWSServiceFactory;
import org.apache.camel.test.infra.common.SharedNameGenerator;
import org.apache.camel.test.infra.common.TestEntityNameGenerator;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentials;

public class SqsProducerBatchSendFifoIntegrationTest extends CamelTestSupport {

    @RegisterExtension
    public static AWSService service = AWSServiceFactory.createSQSService();

    @RegisterExtension
    public static SharedNameGenerator sharedNameGenerator = new TestEntityNameGenerator();

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(5);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                Collection c = new ArrayList<Integer>();
                c.add("2");
                c.add("1");
                c.add("3");
                c.add("4");
                c.add("5");
                exchange.getIn().setBody(c);
            }
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        TestAWSCredentialsProvider credentialsProvider = new SystemPropertiesAWSCredentialsProvider();
        AwsCredentials credentials = credentialsProvider.resolveCredentials();

        final String sqsEndpointUri = String.format(
                "aws2-sqs://%s.fifo?accessKey=RAW(%s)&secretKey=RAW(%s)&region=eu-west-1&messageGroupIdStrategy=useExchangeId"
                                                    + "&messageDeduplicationIdStrategy=useContentBasedDeduplication"
                                                    + "&configuration=%s",
                sharedNameGenerator.getName(),
                credentials.accessKeyId(),
                credentials.secretAccessKey(),
                "#class:" + TestSqsConfiguration.class.getName());

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").startupOrder(2).setHeader(Sqs2Constants.SQS_OPERATION, constant("sendBatchMessage"))
                        .to(sqsEndpointUri);

                fromF("aws2-sqs://%s.fifo?accessKey=RAW(%s)&secretKey=RAW(%s)&region=eu-west-1&deleteAfterRead=false&configuration=%s",
                        sharedNameGenerator.getName(),
                        credentials.accessKeyId(),
                        credentials.secretAccessKey(),
                        "#class:" + TestSqsConfiguration.class.getName())
                                .startupOrder(1)
                                .log("${body}")
                                .to("mock:result");
            }
        };
    }
}
