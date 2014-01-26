package org.apache.camel.component.schematron;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.schematron.engine.SchematronEngineFactory;
import org.apache.camel.component.schematron.exception.SchematronValidationException;
import org.apache.camel.component.schematron.util.Constants;
import org.apache.camel.component.schematron.engine.SchematronEngine;
import org.apache.camel.component.schematron.util.Utils;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * The schematron producer.
 */
public class SchematronProducer extends DefaultProducer {
    private static final Logger logger = LoggerFactory.getLogger(SchematronProducer.class);
    private SchematronEndpoint endpoint;
    private SchematronEngineFactory factory;

    /**
     *
     * @param endpoint the schematron endpoint.
     * @param factory the schematron factory.
     */
    public SchematronProducer(SchematronEndpoint endpoint, SchematronEngineFactory factory) {
        super(endpoint);
        this.endpoint = endpoint;
        this.factory = factory;
    }

    /**
     * Processes the payload. Validates the XML using the SchematronEngine
     *
     * @param exchange
     * @throws Exception
     */
    public void process(Exchange exchange) throws Exception {

        String payload = getPayload(exchange);
        SchematronEngine engine = factory.newScehamtronEngine(Constants.XSLT_VERSION_2_0);
        String report  = engine.validate(payload);
        String status =  Utils.getValidationStatus(report);

        if (this.endpoint.isAbort() && Constants.FAILED.equals(status))
        {
           throw new SchematronValidationException("Schematron validation failure \n" + report);
        }

        // set the body out
        exchange.getOut().setBody(report);
        logger.info("Schematron validation status : {}", status);
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
        exchange.getOut().setHeader(Constants.VALIDATION_STATUS,status);

    }



    /**
     * Gets the body payload either inputStream or String.
     *
     * @param exchange
     * @return
     */
    private String getPayload(Exchange exchange) {
        Object body =  exchange.getIn().getBody();
        try {
            return (body instanceof InputStream) ? IOUtils.toString((InputStream) body) : (String) body;
        } catch (IOException e) {
            logger.error("Unknown payload type: {}", body);
            throw new RuntimeCamelException("Unknown payload type", e);
        }
    }

}
