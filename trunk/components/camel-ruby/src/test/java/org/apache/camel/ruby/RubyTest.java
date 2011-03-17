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
package org.apache.camel.ruby;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jruby.Main;
import org.junit.Test;

/**
 * @version 
 */
public class RubyTest extends CamelTestSupport {
    protected String expected = "<hello>world!</hello>";
    protected String scriptName = "src/test/java/org/apache/camel/ruby/example.rb";

    @Test
    public void testSendMatchingMessage() throws Exception {
        if (isPlatform("windows")) {
            return;
        }

        MockEndpoint resultEndpoint = getMockEndpoint("mock:results");
        resultEndpoint.expectedBodiesReceived(expected);

        template.sendBodyAndHeader("direct:a", expected, "foo", "bar");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNotMatchingMessage() throws Exception {
        if (isPlatform("windows")) {
            return;
        }

        MockEndpoint resultEndpoint = getMockEndpoint("mock:results");
        resultEndpoint.expectedMessageCount(0);
        
        template.sendBodyAndHeader("direct:a", expected, "foo", "123");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        // appears to cause issue on some windows with loading jruby
        if (isPlatform("windows")) {
            return super.createCamelContext();
        }
        CamelContext answer = super.createCamelContext();
        RubyCamel.setCamelContext(answer);

        // TODO make a better way to load ruby based route definitions!

        // must clear before running script
        RubyCamel.clearRoutes();

        // now lets run the script
        runScript(scriptName);
        List<RouteBuilder> list = RubyCamel.getRoutes();

        log.info("Found route builders: " + list);
        for (RouteBuilder routeBuilder : list) {
            answer.addRoutes(routeBuilder);
        }

        return answer;
    }

    public static void runScript(String name) {
        String[] args = {name};
        Main.main(args);
    }
}
