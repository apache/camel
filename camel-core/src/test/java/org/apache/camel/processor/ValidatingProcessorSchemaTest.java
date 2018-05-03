package org.apache.camel.processor;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.validation.ValidatingProcessor;
import org.xml.sax.SAXParseException;

public class ValidatingProcessorSchemaTest extends ContextTestSupport {

  protected ValidatingProcessor validating;

  @Override
  protected void setUp() throws Exception {
    validating = new ValidatingProcessor();
    validating.setSchemaFile(new File("src/test/resources/org/apache/camel/processor/ValidatingProcessorFailed.xsd"));

    super.setUp();
  }

  public void testSchemaWithValidMessage() throws Exception {
    MockEndpoint mock = getMockEndpoint("mock:error");
    mock.expectedMessageCount(1);

    String xml = "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>"
        + "<user xmlns=\"http://foo.com/bar\">"
        + "  <id>1</id>"
        + "  <username>davsclaus</username>"
        + "</user>";

    template.sendBody("direct:start", xml);
    System.out.println(mock.getExchanges().get(0));

    assertMockEndpointsSatisfied();
  }

  protected RouteBuilder createRouteBuilder() {
    return new RouteBuilder() {
      public void configure() {
        //errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

        //onException(SAXParseException.class).to("mock:schemeError");

        from("direct:start").
            process(validating).
            to("mock:valid");
      }
    };
  }

}
