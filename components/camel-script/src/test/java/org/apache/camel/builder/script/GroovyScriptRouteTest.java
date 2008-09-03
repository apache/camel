package org.apache.camel.builder.script;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.builder.script.ScriptBuilder.groovy;

/**
 * Unit test for a Groovy script based on end-user question.
 */
public class GroovyScriptRouteTest extends ContextTestSupport {

    public void testGroovyScript() throws Exception {
        //TODO: fix me
        //MockEndpoint mock = getMockEndpoint("mock:result");
        //mock.expectedHeaderReceived("foo", "Hello World");

        //template.sendBodyAndHeader("seda:a", "Hello World", "foo", "London");

        //mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("seda:a").setHeader("foo", groovy("request.body")).to("mock:result");
            }
        };
    }
}
