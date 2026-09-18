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
package org.apache.camel.language.python3;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class Python3LanguageEndpointTest extends CamelTestSupport {

    @Test
    void languageEndpointEvaluatesInlineExpression() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(6);

        template.sendBody("direct:start", 3);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void languageEndpointHonorsResultType() throws Exception {
        getMockEndpoint("mock:typed").expectedBodiesReceived(8);
        getMockEndpoint("mock:typed").message(0).body().isInstanceOf(Integer.class);

        template.sendBody("direct:typed", 4);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String script = URLEncoder.encode("body * 2", StandardCharsets.UTF_8);
                from("direct:start").to("language:python3:" + script).to("mock:result");
                from("direct:typed").to("language:python3:" + script + "?resultType=java.lang.Integer").to("mock:typed");
            }
        };
    }
}
