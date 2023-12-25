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
package org.apache.camel.component.jte;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JteDataModelTest extends CamelTestSupport {

    @Test
    public void testJteDataModel() throws Exception {
        Exchange exchange = template.request("direct:a", new Processor() {
            @Override
            public void process(Exchange exchange) {
                MyModel myModel = new MyModel();
                myModel.foo = "Jack";
                myModel.age = 44;
                exchange.getIn().setHeader(JteConstants.JTE_DATA_MODEL, myModel);
            }
        });
        if (exchange.isFailed()) {
            throw exchange.getException();
        }

        assertEquals("Hello Jack you are 44 years old", exchange.getMessage().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                JteComponent jte = context.getComponent("jte", JteComponent.class);
                jte.setWorkDir("target/jte-classes");

                // START SNIPPET: example
                from("direct:a").to(
                        "jte:org/apache/camel/component/jte/custom.jte?allowTemplateFromHeader=true");
                // END SNIPPET: example
            }
        };
    }
}
