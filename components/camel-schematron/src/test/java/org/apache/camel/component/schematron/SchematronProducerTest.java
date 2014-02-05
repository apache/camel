package org.apache.camel.component.schematron;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.schematron.engine.SchematronEngineFactory;
import org.apache.camel.component.schematron.exception.SchematronValidationException;
import org.apache.camel.component.schematron.util.Constants;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Schematron Producer Unit Test.
 * <p/>
 * Created by akhettar on 31/12/2013.
 */
public class SchematronProducerTest extends CamelTestSupport {

    private static SchematronProducer producer;

    @BeforeClass
    public static void setUP() {
        SchematronEngineFactory fac = SchematronEngineFactory.newInstance(ClassLoader.
                getSystemResourceAsStream("sch/sample-schematron.sch"));
        SchematronEndpoint endpoint = new SchematronEndpoint();
        producer = new SchematronProducer(endpoint, fac);
    }

    @Test
    public void testProcessValidXML() throws Exception {
        Exchange exc = new DefaultExchange(context, ExchangePattern.InOut);
        exc.getIn().setBody(ClassLoader.getSystemResourceAsStream("xml/article-1.xml"));

        // process xml payload
        producer.process(exc);

        // assert
        assertTrue(exc.getOut().getHeader(Constants.VALIDATION_STATUS).equals(Constants.SUCCESS));
    }

    @Test
    public void testProcessInValidXML() throws Exception {
        Exchange exc = new DefaultExchange(context, ExchangePattern.InOut);
        exc.getIn().setBody(ClassLoader.getSystemResourceAsStream("xml/article-2.xml"));

        // process xml payload
        producer.process(exc);

        // assert
        assertTrue(exc.getOut().getHeader(Constants.VALIDATION_STATUS).equals(Constants.FAILED));

    }

}
