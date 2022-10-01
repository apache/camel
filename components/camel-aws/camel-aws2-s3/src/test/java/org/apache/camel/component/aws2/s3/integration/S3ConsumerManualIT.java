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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.manual.access.key and -Daws.manual.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.manual.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
public class S3ConsumerManualIT extends CamelTestSupport {
    private static final String ACCESS_KEY = System.getProperty("aws.manual.access.key");
    private static final String SECRET_KEY = System.getProperty("aws.manual.secret.key");

    @BindToRegistry("amazonS3Client")
    S3Client client
            = S3Client.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                    .region(Region.EU_WEST_1).build();

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(3);

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "test.txt");
                exchange.getIn().setBody("Test");
            }
        });

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "test1.txt");
                exchange.getIn().setBody("Test1");
            }
        });

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "test2.txt");
                exchange.getIn().setBody("Test2");
            }
        });

        Thread.sleep(10000);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    @DisplayName("Should consume S3StreamObject when include body is true and should close the stream when autocloseBody is true")
    public void shouldConsumeS3StreamObjectWhenIncludeBodyIsTrueAndNotCloseStreamWhenAutoCloseBodyIsTrue()
            throws InterruptedException {
        result.reset();

        result.expectedMessageCount(2);

        template.setDefaultEndpointUri("direct:includeBodyTrueAutoCloseTrue");

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(AWS2S3Constants.KEY, "test1.txt");
            exchange.getIn().setBody("Test");
        });

        Map<String, Object> headers = new HashMap<>();
        headers.put(AWS2S3Constants.KEY, "test1.txt");
        headers.put(Exchange.FILE_NAME, "test1.txt");

        template.sendBodyAndHeaders("direct:includeBodyTrueAutoCloseTrue", headers);
        result.assertIsSatisfied();

        final Exchange exchange = result.getExchanges().get(1);

        assertThat(exchange.getIn().getBody().getClass(), is(equalTo(String.class)));
        assertThat(exchange.getIn().getBody(String.class), is("Test"));
    }

    @Test
    @DisplayName("Should not consume S3StreamObject when include body is false and should not close the stream when autocloseBody is false")
    public void shouldNotConsumeS3StreamObjectWhenIncludeBodyIsFalseAndNotCloseStreamWhenAutoCloseBodyIsFalse()
            throws InterruptedException {
        result.reset();

        result.expectedMessageCount(2);

        template.setDefaultEndpointUri("direct:includeBodyFalseAutoCloseFalse");

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(AWS2S3Constants.KEY, "test1.txt");
            exchange.getIn().setBody("Test");
        });

        Map<String, Object> headers = new HashMap<>();
        headers.put(AWS2S3Constants.KEY, "test1.txt");
        headers.put(Exchange.FILE_NAME, "test1.txt");

        template.sendBodyAndHeaders("direct:includeBodyFalseAutoCloseFalse", headers);
        result.assertIsSatisfied();

        final Exchange exchange = result.getExchanges().get(1);

        assertThat(exchange.getIn().getBody().getClass(), is(equalTo(ResponseInputStream.class)));
        assertDoesNotThrow(() -> {
            final ResponseInputStream<GetObjectResponse> inputStream = exchange.getIn().getBody(ResponseInputStream.class);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                final String text = reader.lines().collect(Collectors.joining());
                assertThat(text, is("Test"));
            }
        });
    }

    @Test
    @DisplayName("Should not consume S3StreamObject when include body is false and should close the stream when autocloseBody is true")
    public void shouldNotConsumeS3StreamObjectWhenIncludeBodyIsFalseAndCloseStreamWhenAutoCloseBodyIsTrue()
            throws InterruptedException {
        result.reset();

        result.expectedMessageCount(2);

        template.setDefaultEndpointUri("direct:includeBodyFalseAutoCloseTrue");

        template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(AWS2S3Constants.KEY, "test1.txt");
            exchange.getIn().setBody("Test");
        });

        Map<String, Object> headers = new HashMap<>();
        headers.put(AWS2S3Constants.KEY, "test1.txt");
        headers.put(Exchange.FILE_NAME, "test1.txt");

        template.sendBodyAndHeaders("direct:includeBodyFalseAutoCloseTrue", headers);
        result.assertIsSatisfied();

        final Exchange exchange = result.getExchanges().get(1);

        assertThat(exchange.getIn().getBody().getClass(), is(equalTo(ResponseInputStream.class)));
        assertThrows(IOException.class, () -> {
            final ResponseInputStream<GetObjectResponse> inputStream = exchange.getIn().getBody(ResponseInputStream.class);
            inputStream.read();
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String template = "aws2-s3://mycamel?autoCreateBucket=true&includeBody=%s&autocloseBody=%s";
                String includeBodyTrueAutoCloseTrue = String.format(template, true, true);
                String includeBodyFalseAutoCloseFalse = String.format(template, false, false);
                String includeBodyFalseAutoCloseTrue = String.format(template, false, true);
                from("direct:includeBodyTrueAutoCloseTrue").pollEnrich(includeBodyTrueAutoCloseTrue, 5000).to("mock:result");
                from("direct:includeBodyFalseAutoCloseFalse").pollEnrich(includeBodyFalseAutoCloseFalse, 5000)
                        .to("mock:result");
                from("direct:includeBodyFalseAutoCloseTrue").pollEnrich(includeBodyFalseAutoCloseTrue, 5000).to("mock:result");

                String awsEndpoint = "aws2-s3://mycamel?autoCreateBucket=false";

                from("direct:putObject").startupOrder(1).to(awsEndpoint).to("mock:result");

                from("aws2-s3://mycamel?moveAfterRead=true&destinationBucket=camel-kafka-connector&autoCreateBucket=false&destinationBucketPrefix=RAW(movedPrefix)&destinationBucketSuffix=RAW(movedSuffix)")
                        .startupOrder(2).log("${body}");

            }
        };
    }
}
