package org.apache.camel.itest.jaxb;

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.itest.jaxb.example.Bar;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.junit.Test;

public class JaxbFallbackTypeConverterTest extends CamelTestSupport {
    
    @Test
    public void testJaxbFallbackTypeConverter() {
        Bar bar = new Bar();
        bar.setName("camel");
        bar.setValue("cool");
        String result = template.requestBody("direct:start", bar, String.class);
        assertNotNull(result);
        assertTrue("Get a wrong xml string", result.indexOf("<bar name=\"camel\" value=\"cool\"/>") > 0);
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:start").process(new Processor() {

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        RequestEntity entity = in.getBody(RequestEntity.class);
                        assertNull("We should not get the entity here", entity);
                        InputStream is = in.getMandatoryBody(InputStream.class);
                        // make sure we can get the InputStream rightly.
                        exchange.getOut().setBody(is);
                    }
                    
                });
            }
        };
    }

}
