package org.apache.camel.component.schematron;


import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.schematron.exception.SchematronValidationException;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Created by akhettar on 20/12/2013.
 */
public class SchematronEndpointTest extends CamelTestSupport {


    @Test
    public void testSchematronFileReadFromClassPath()throws Exception {


        String payload = IOUtils.toString(ClassLoader.getSystemResourceAsStream("xml/article-1.xml"));
        Endpoint endpoint = context().getEndpoint("schematron://sch/sample-schematron.sch");
        Producer producer = endpoint.createProducer();
        Exchange exchange = new DefaultExchange(context);

        exchange.getIn().setBody(payload);

        // invoke the component.
        producer.process(exchange);

        String report = extractReport(exchange);
        assertNotNull(report);
    }

    @Test
    public void testSchematronFileReadFromFileSystem()throws Exception {


        String payload = IOUtils.toString(ClassLoader.getSystemResourceAsStream("xml/article-2.xml"));
        String path = ClassLoader.getSystemResource("sch/sample-schematron.sch").getPath();
        Endpoint endpoint = context().getEndpoint("schematron://" + path);
        Producer producer = endpoint.createProducer();
        Exchange exchange = new DefaultExchange(context);

        exchange.getIn().setBody(payload);

        // invoke the component.
        producer.process(exchange);

        String report = extractReport(exchange);
        assertNotNull(report);
    }

    @Test(expected = SchematronValidationException.class)
    public void testThrowSchematronValidationException() throws Exception
    {
        String payload = IOUtils.toString(ClassLoader.getSystemResourceAsStream("xml/article-2.xml"));
        Endpoint endpoint = context().getEndpoint("schematron://sch/sample-schematron.sch?abort=true");
        Producer producer = endpoint.createProducer();
        Exchange exchange = new DefaultExchange(context);

        exchange.getIn().setBody(payload);

        // invoke the component.
        producer.process(exchange);

    }
    /**
     * Retrieves the body in string from format from the exchange.
     * @param exchange
     * @return
     * @throws IOException
     */
    private String extractReport(Exchange exchange) throws IOException {

        return IOUtils.toString(exchange.getOut().getBody(InputStream.class));
    }
}
