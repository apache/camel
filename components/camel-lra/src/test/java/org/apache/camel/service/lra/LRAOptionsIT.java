/**
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
package org.apache.camel.service.lra;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;

public class LRAOptionsIT extends AbstractLRATestSupport {

    @Test
    public void testHeaderForwardedToComplete() throws Exception {

        MockEndpoint complete = getMockEndpoint("mock:complete");
        complete.expectedMessageCount(1);
        complete.expectedHeaderReceived("id", "myheader");
        complete.expectedHeaderReceived("name", "Nicola");
        complete.expectedMessagesMatches(ex -> ex.getIn().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION) != null);

        template.sendBodyAndHeader("direct:workflow", "Hello", "myname", "Nicola");

        complete.assertIsSatisfied();
    }

    @Test
    public void testHeaderForwardedToCompensate() throws Exception {

        MockEndpoint compensate = getMockEndpoint("mock:compensate");
        compensate.expectedMessageCount(1);
        compensate.expectedHeaderReceived("id", "myheader");
        compensate.expectedHeaderReceived("name", "Nicola");
        compensate.expectedMessagesMatches(ex -> ex.getIn().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION) != null);

        try {
            template.sendBodyAndHeader("direct:workflow", "compensate", "myname", "Nicola");
            Assert.fail("Should throw an exception");
        } catch (Exception ex) {
            // OK
        }

        compensate.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:workflow")
                        .saga()
                        .option("id", constant("myheader"))
                        .option("name", header("myname"))
                        .completion("mock:complete")
                        .compensation("mock:compensate")
                        .choice()
                        .when(body().isEqualTo("compensate"))
                        .process(ex -> {
                            throw new RuntimeException("forced compensate");
                        })
                        .end()
                        .setHeader("myname", constant("TryToOverride"))
                        .setHeader("name", constant("TryToOverride"))
                        .to("mock:endpoint");

            }
        };
    }

}
