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
package org.apache.camel.component.aws2.lambda.integration;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.Runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Must be manually tested. Provide your own accessKey and secretKey!")
public class LambdaComponentIntegrationTest extends CamelTestSupport {

    @Test
    public void lambdaListFunctionsTest() throws Exception {
        Exchange exchange = template.send("direct:listFunctions", ExchangePattern.InOut, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        assertNotNull(exchange.getMessage().getBody(ListFunctionsResponse.class));
        assertEquals(exchange.getMessage().getBody(ListFunctionsResponse.class).functions().size(), 2);
    }

    @Test
    public void lambdaGetFunctionTest() throws Exception {
        Exchange exchange = template.send("direct:getFunction", ExchangePattern.InOut, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });
        GetFunctionResponse result = exchange.getMessage().getBody(GetFunctionResponse.class);
        assertNotNull(result);
        assertEquals(result.configuration().functionName(), "twitterTrends");
        assertEquals(result.configuration().runtime(), Runtime.JAVA8);

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:listFunctions")
                    .to("aws2-lambda://myFunction?operation=listFunctions&accessKey=xxx&secretKey=yyy&region=eu-west-1");

                from("direct:getFunction")
                    .to("aws2-lambda://twitterTrends?operation=getFunction&accessKey=xxx&secretKey=yyy&region=eu-west-1");

            }
        };
    }
}
