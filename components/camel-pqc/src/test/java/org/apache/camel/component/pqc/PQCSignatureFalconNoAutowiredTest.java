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
package org.apache.camel.component.pqc;

import java.security.*;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PQCSignatureFalconNoAutowiredTest extends CamelTestSupport {

    @EndpointInject("mock:sign")
    protected MockEndpoint resultSign;

    @EndpointInject("mock:verify")
    protected MockEndpoint resultVerify;

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    public PQCSignatureFalconNoAutowiredTest() throws NoSuchAlgorithmException {
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sign").to("pqc:sign?operation=sign&signatureAlgorithm=FALCON").to("mock:sign")
                        .to("pqc:verify?operation=verify&signatureAlgorithm=FALCON")
                        .to("mock:verify");
            }
        };
    }

    @BeforeAll
    public static void startup() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastlePQCProvider());
    }

    @Test
    void testSignAndVerify() throws Exception {
        resultSign.expectedMessageCount(1);
        resultVerify.expectedMessageCount(1);
        templateSign.sendBody("Hello");
        resultSign.assertIsSatisfied();
        resultVerify.assertIsSatisfied();
        assertTrue(resultVerify.getExchanges().get(0).getMessage().getHeader(PQCConstants.VERIFY, Boolean.class));
    }
}
