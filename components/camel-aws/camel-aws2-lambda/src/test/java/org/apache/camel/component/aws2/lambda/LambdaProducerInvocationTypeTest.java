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
package org.apache.camel.component.aws2.lambda;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LogType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The invocation type and the log type of invokeFunction come from the headers, else from the endpoint options.
 */
public class LambdaProducerInvocationTypeTest extends CamelTestSupport {

    private static final String LOG = Base64.getEncoder().encodeToString("START RequestId: 1".getBytes(StandardCharsets.UTF_8));

    @BindToRegistry("awsLambdaClient")
    AmazonLambdaClientMock clientMock = new AmazonLambdaClientMock() {
        @Override
        public InvokeResponse invoke(InvokeRequest invokeRequest) {
            this.invokeRequest = invokeRequest;
            InvokeResponse.Builder response = InvokeResponse.builder();
            if (invokeRequest.invocationType() == InvocationType.EVENT) {
                // Lambda queues the event and answers at once without a payload
                return response.statusCode(202).payload(SdkBytes.fromUtf8String("")).build();
            }
            if (invokeRequest.invocationType() == InvocationType.DRY_RUN) {
                // Lambda only checks that the function can be invoked
                return response.statusCode(204).payload(SdkBytes.fromUtf8String("")).build();
            }
            response.statusCode(200).payload(SdkBytes.fromUtf8String("{\"Hello\":\"Camel\"}"));
            if (invokeRequest.logType() == LogType.TAIL) {
                response.logResult(LOG);
            }
            return response.build();
        }
    };

    @Test
    public void defaultIsRequestResponseWithoutLog() {
        Exchange exchange = template.request("direct:default", e -> e.getIn().setBody("{\"name\":\"Camel\"}"));

        assertEquals(InvocationType.REQUEST_RESPONSE, clientMock.invokeRequest.invocationType());
        assertEquals(LogType.NONE, clientMock.invokeRequest.logType());
        assertEquals("{\"Hello\":\"Camel\"}", exchange.getMessage().getBody(String.class));
        assertEquals(200, exchange.getMessage().getHeader(Lambda2Constants.STATUS_CODE));
        assertNull(exchange.getMessage().getHeader(Lambda2Constants.LOG_RESULT));
    }

    @Test
    public void eventFromTheOption() {
        Exchange exchange = template.request("direct:event", e -> e.getIn().setBody("{\"name\":\"Camel\"}"));

        assertEquals(InvocationType.EVENT, clientMock.invokeRequest.invocationType());
        assertEquals(202, exchange.getMessage().getHeader(Lambda2Constants.STATUS_CODE));
        assertEquals("", exchange.getMessage().getBody(String.class));
    }

    @Test
    public void headerOverridesTheOption() {
        Exchange exchange = template.request("direct:event", e -> {
            e.getIn().setBody("{\"name\":\"Camel\"}");
            e.getIn().setHeader(Lambda2Constants.INVOCATION_TYPE, "DryRun");
        });

        assertEquals(InvocationType.DRY_RUN, clientMock.invokeRequest.invocationType());
        assertEquals(204, exchange.getMessage().getHeader(Lambda2Constants.STATUS_CODE));
        assertEquals("", exchange.getMessage().getBody(String.class));
    }

    @Test
    public void tailFromTheOptionFillsTheLogResult() {
        Exchange exchange = template.request("direct:tail", e -> e.getIn().setBody("{\"name\":\"Camel\"}"));

        assertEquals(LogType.TAIL, clientMock.invokeRequest.logType());
        assertEquals(LOG, exchange.getMessage().getHeader(Lambda2Constants.LOG_RESULT));
    }

    @Test
    public void tailFromTheHeader() {
        Exchange exchange = template.request("direct:default", e -> {
            e.getIn().setBody("{\"name\":\"Camel\"}");
            e.getIn().setHeaders(Map.of(Lambda2Constants.LOG_TYPE, "Tail"));
        });

        assertEquals(LogType.TAIL, clientMock.invokeRequest.logType());
        assertEquals(LOG, exchange.getMessage().getHeader(Lambda2Constants.LOG_RESULT));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:default")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=invokeFunction");
                from("direct:event")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=invokeFunction"
                            + "&invocationType=Event");
                from("direct:tail")
                        .to("aws2-lambda://GetHelloWithName?awsLambdaClient=#awsLambdaClient&operation=invokeFunction"
                            + "&logType=Tail");
            }
        };
    }
}
