package org.apache.camel.component.schematron;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.schematron.util.Utils;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.DifferenceListener;
import org.custommonkey.xmlunit.IgnoreTextAndAttributeValuesDifferenceListener;
import org.junit.Test;

/**
 * Schematron Component Test.
 */
public class SchematronComponentTest extends CamelTestSupport {


    /**
     * @throws Exception
     */
    @Test
    public void testSendBodyAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        String payload = IOUtils.toString(ClassLoader.getSystemResourceAsStream("xml/article-1.xml"));
        String expected = IOUtils.toString(ClassLoader.getSystemResourceAsStream("result/article-1-result.xml"));
        template.sendBody("direct:start", payload);
        assertMockEndpointsSatisfied();
        String result = mock.getExchanges().get(0).getIn().getBody(String.class);
        DifferenceListener myDifferenceListener = new IgnoreTextAndAttributeValuesDifferenceListener();
        Diff myDiff = new Diff(expected, result);
        myDiff.overrideDifferenceListener(myDifferenceListener);
        assertTrue(myDiff.similar());
    }

    /**
     * @throws Exception
     */
    @Test
    public void testSendBodyAsInputStreamInvalidXML() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        String payload = IOUtils.toString(ClassLoader.getSystemResourceAsStream("xml/article-2.xml"));
        String expected = IOUtils.toString(ClassLoader.getSystemResourceAsStream("result/article-1-result.xml"));
        template.sendBody("direct:start", payload);
        assertMockEndpointsSatisfied();
        String result = mock.getExchanges().get(0).getIn().getBody(String.class);


        // should throw two assertions because of the missing chapters in the XML.
        assertEquals("A chapter should have a title", Utils.evaluate("//svrl:failed-assert [@location='/doc[1]/chapter[1]']/svrl:text", result));
        assertEquals("A chapter should have a title", Utils.evaluate("//svrl:failed-assert [@location='/doc[1]/chapter[2]']/svrl:text", result));

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("schematron://sch/sample-schematron.sch")
                        .to("mock:result");
            }
        };
    }
}
