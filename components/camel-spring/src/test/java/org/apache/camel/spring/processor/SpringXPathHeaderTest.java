package org.apache.camel.spring.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.CamelContext;
import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Spring basesd XPathHeaderTest.
 */
public class SpringXPathHeaderTest extends ContextTestSupport {

    public void testChoiceWithHeaderSelectCamel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:camel");
        mock.expectedBodiesReceived("<name>King</name>");
        mock.expectedHeaderReceived("type", "Camel");

        template.sendBodyAndHeader("direct:in", "<name>King</name>", "type", "Camel");

        mock.assertIsSatisfied();
    }

    public void testChoiceWithNoHeaderSelectDonkey() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:donkey");
        mock.expectedBodiesReceived("<name>Kong</name>");

        template.sendBody("direct:in", "<name>Kong</name>");

        mock.assertIsSatisfied();
    }

    public void testChoiceWithNoHeaderSelectOther() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:other");
        mock.expectedBodiesReceived("<name>Other</name>");

        template.sendBody("direct:in", "<name>Other</name>");

        mock.assertIsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/SpringXPathHeaderTest-context.xml");
    }

}
