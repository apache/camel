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
package org.apache.camel.component.aws2.translate.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.translate.Translate2Constants;
import org.apache.camel.component.aws2.translate.Translate2LanguageEnum;
import org.apache.camel.component.aws2.translate.Translate2Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("This test must be manually started, you need to specify AWS Credentials")
public class Translate2ProducerIntegrationTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void translateTextTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:translateText", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Translate2Constants.OPERATION, Translate2Operations.translateText);
                exchange.getIn().setHeader(Translate2Constants.SOURCE_LANGUAGE, Translate2LanguageEnum.ITALIAN);
                exchange.getIn().setHeader(Translate2Constants.TARGET_LANGUAGE, Translate2LanguageEnum.GERMAN);
                exchange.getIn().setBody("Ciao Signorina");
            }
        });

        assertMockEndpointsSatisfied();

        String resultGet = (String)exchange.getIn().getBody();
        assertEquals("Hallo, Miss.", resultGet);
    }

    @Test
    public void translateTextAutodetectSourceTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:translateTextAuto", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Translate2Constants.OPERATION, Translate2Operations.translateText);
                exchange.getIn().setHeader(Translate2Constants.TARGET_LANGUAGE, Translate2LanguageEnum.GERMAN);
                exchange.getIn().setBody("Ciao Signorina");
            }
        });

        assertMockEndpointsSatisfied();

        String resultGet = (String)exchange.getIn().getBody();
        assertEquals("Hallo, Miss.", resultGet);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:translateText").to("aws2-translate://test?accessKey=RAW(xxxx)&secretKey=RAW(xxxx)&region=eu-west-1&operation=translateText").to("mock:result");
                from("direct:translateTextAuto")
                    .to("aws2-translate://test?accessKey=RAW(xxxx)&secretKey=RAW(xxxx)&region=eu-west-1&operation=translateText&autodetectSourceLanguage=true").to("mock:result");
            }
        };
    }
}
