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
package org.apache.camel.issues;

import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Based on user forum issue
 */
public class TwoDoTryAndThrowInInnerCatchIssueTest extends ContextTestSupport {

    @Test
    public void testSendThatIsCaught() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("myroute"));
        log.info(xml);

        getMockEndpoint("mock:catch1").expectedMessageCount(0);
        getMockEndpoint("mock:catch2").expectedMessageCount(0);
        getMockEndpoint("mock:catch3").expectedMessageCount(0);
        getMockEndpoint("mock:catch4").expectedMessageCount(1);
        getMockEndpoint("mock:catch5").expectedMessageCount(1);

        try {
            template.requestBody("direct:test", "test", String.class);
        } catch (Exception e) {
            fail("Should not fail");
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("direct:test").routeId("myroute")
                    .doTry().
                        doTry().
                            throwException(new IllegalArgumentException("Forced by me"))
                        .doCatch(IOException.class)
                            .to("mock:catch1")
                            .log("docatch 1")
                            // end this doCatch block
                            .endDoTry()
                        .doCatch(NullPointerException.class)
                            .to("mock:catch2")
                            .log("docatch 2")
                            // no end this catch block as Camel can fix this itself
                        .doCatch(MalformedURLException.class)
                            .to("mock:catch3")
                            .log("docatch 3")
                            // end this doCatch block
                            .endDoTry()
                        .doCatch(Exception.class)
                            .to("mock:catch4")
                            .log("docatch 4")
                            .throwException(new IllegalArgumentException("Second forced by me"))
                            .endDoTry() // end catch block
                        .endDoTry() // end inner doTry block
                    .doCatch(Exception.class)
                        .to("mock:catch5")
                        .log("docatch 5")
                    .end();
            }
        };
    }
}
