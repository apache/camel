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
package org.apache.camel.component.aws.translate;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class TranslateProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonTranslateClient")
    AmazonAWSTranslateMock clientMock = new AmazonAWSTranslateMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void translateTextTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:translateText", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(TranslateConstants.SOURCE_LANGUAGE, TranslateLanguageEnum.ITALIAN);
                exchange.getIn().setHeader(TranslateConstants.TARGET_LANGUAGE, TranslateLanguageEnum.ENGLISH);
                exchange.getIn().setBody("ciao");
            }
        });

        assertMockEndpointsSatisfied();

        String resultGet = exchange.getIn().getBody(String.class);
        assertEquals("Hello", resultGet);

    }

    @Test
    public void translateTextTestOptions() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:translateTextOptions", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("ciao");
            }
        });

        assertMockEndpointsSatisfied();

        String resultGet = exchange.getIn().getBody(String.class);
        assertEquals("Hello", resultGet);

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:translateText").to("aws-translate://test?translateClient=#amazonTranslateClient&operation=translateText").to("mock:result");
                from("direct:translateTextOptions").to("aws-translate://test?translateClient=#amazonTranslateClient&operation=translateText&sourceLanguage=it&targetLanguage=en")
                    .to("mock:result");
            }
        };
    }
}
