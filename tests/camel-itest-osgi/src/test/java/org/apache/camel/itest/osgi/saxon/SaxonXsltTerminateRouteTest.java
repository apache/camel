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
package org.apache.camel.itest.osgi.saxon;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.OptionUtils.combine;


@RunWith(PaxExam.class)
public class SaxonXsltTerminateRouteTest extends OSGiIntegrationTestSupport {

    private String data = "<staff>\n"
            + "\n"
            + "  <programmer>\n"
            + "    <name>Bugs Bunny</name>\n"
            + "    <dob>03/21/1970</dob>\n"
            + "    <age>31</age>\n"
            + "    <address>4895 Wabbit Hole Road</address>\n"
            + "    <phone>865-111-1111</phone>\n"
            + "  </programmer>\n"
            + "\n"
            + "  <programmer>\n"
            + "    <name>Daisy Duck</name>\n"
            + "    <dob></dob>\n"
            + "    <age></age>\n"
            + "    <address>748 Golden Pond</address>\n"
            + "    <phone>865-222-2222</phone>\n"
            + "  </programmer>\n"
            + "\n"
            + "</staff>";

    @Test
    public void testXsltTerminate() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:start", data);

        assertMockEndpointsSatisfied();

        Exchange out = getMockEndpoint("mock:dead").getReceivedExchanges().get(0);
        assertNotNull(out);
        // this exception is just a generic xslt error
        Exception cause = out.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(cause);

        // we have the xsl termination message as a error property on the exchange as we set terminate=true
        Exception error = out.getProperty(Exchange.XSLT_ERROR, Exception.class);
        assertNotNull(error);
        assertEquals("Error: DOB is an empty string!", error.getMessage());
    }

    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
                getDefaultCamelKarafOptions(),
                // using the features to install the other camel components
                loadCamelFeatures("camel-saxon"));

        return options;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start")
                        .to("xslt:org/apache/camel/itest/osgi/core/xslt/terminate.xsl?saxon=true")
                        .to("log:foo")
                        .to("mock:result");

            }
        };
    }
    
    

}
