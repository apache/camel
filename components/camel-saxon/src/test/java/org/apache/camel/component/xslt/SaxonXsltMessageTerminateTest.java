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
package org.apache.camel.component.xslt;

import net.sf.saxon.expr.instruct.TerminationException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SaxonXsltMessageTerminateTest extends CamelTestSupport {

    @Test
    public void testXsltTerminate() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();

        Exchange out = getMockEndpoint("mock:dead").getReceivedExchanges().get(0);
        assertNotNull(out);
        // this exception is just a generic xslt error
        Exception cause = out.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(cause);

        // we have the xsl termination message as a error property on the exchange as we set terminate=true
        Exception error = out.getProperty(Exchange.XSLT_FATAL_ERROR, Exception.class);
        assertNotNull(error);
        assertIsInstanceOf(TerminationException.class, error);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("file:src/test/data/?fileName=terminate.xml&noop=true").routeId("foo").noAutoStartup()
                        .to("xslt-saxon:org/apache/camel/component/xslt/terminate.xsl")
                        .to("log:foo")
                        .to("mock:result");
            }
        };
    }

}
