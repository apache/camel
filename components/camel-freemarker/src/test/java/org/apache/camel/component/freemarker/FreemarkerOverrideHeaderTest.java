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
package org.apache.camel.component.freemarker;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class FreemarkerOverrideHeaderTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private Exchange createLetter() {
        Exchange exchange = context.getEndpoint("direct:a").createExchange();

        Message msg = exchange.getIn();
        msg.setHeader("firstName", "Claus");
        msg.setHeader("lastName", "Ibsen");
        msg.setHeader("item", "Camel in Action");
        msg.setBody("PS: Next beer is on me, James");
        msg.setHeader(FreemarkerConstants.FREEMARKER_RESOURCE_URI, "org/apache/camel/component/freemarker/letter.ftl");
        return exchange;
    }

    @Test
    public void testFreemarkerLetter() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a")
                        .to("freemarker:org/apache/camel/component/freemarker/example.ftl")
                        .to("mock:result");
            }
        });
        context.start();

        Exchange out = template.send("direct:a", createLetter());
        assertTrue(out.isFailed());

        context.stop();
    }

    @Test
    public void testFreemarkerLetterAllowed() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a")
                        .to("freemarker:org/apache/camel/component/freemarker/example.ftl?allowTemplateFromHeader=true")
                        .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().contains("Dear Ibsen, Claus");
        mock.message(0).body().contains("Thanks for the order of Camel in Action.");

        template.send("direct:a", createLetter());

        mock.assertIsSatisfied();

        context.stop();
    }

}
