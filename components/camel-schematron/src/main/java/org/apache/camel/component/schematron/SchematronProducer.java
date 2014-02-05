package org.apache.camel.component.schematron;

import org.apache.camel.Exchange;
import org.apache.camel.component.schematron.engine.SchematronEngineFactory;
import org.apache.camel.component.schematron.exception.SchematronValidationException;
import org.apache.camel.component.schematron.util.Constants;
import org.apache.camel.component.schematron.util.Utils;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Schematron producer.
 */
public class SchematronProducer extends DefaultProducer {
    private static final Logger logger = LoggerFactory.getLogger(SchematronProducer.class);
    private SchematronEndpoint endpoint;
    private SchematronEngineFactory factory;

    /**
     * @param endpoint the schematron endpoint.
     * @param factory  the schematron factory.
     */
    public SchematronProducer(final SchematronEndpoint endpoint, final SchematronEngineFactory factory) {
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

        String payload = exchange.getIn().getBody(String.class);
        String report = factory.newScehamtronEngine().validate(payload);

        logger.debug("Schematron validation report \n {}", report);
        String status = getValidationStatus(report);
        logger.info("Schematron validation status : {}", status);

        // if exchange pattern is In and Out set details on the Out message.
        if (exchange.getPattern().isOutCapable()) {
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            exchange.getOut().setHeader(Constants.VALIDATION_STATUS, status);
            exchange.getOut().setHeader(Constants.VALIDATION_REPORT, report);
        } else {
            exchange.getIn().setHeader(Constants.VALIDATION_STATUS, status);
            exchange.getIn().setHeader(Constants.VALIDATION_REPORT, report);
        }

    }

    /**
     * Get validation status, SUCCESS or FAILURE
     *
     * @param report
     * @return
     */
    private String getValidationStatus(final String report) {
        String status = Utils.getValidationStatus(report);
        if (this.endpoint.isAbort() && Constants.FAILED.equals(status)) {
            throw new SchematronValidationException("Schematron validation failure \n" + report);
        }
        return status;
    }
}
