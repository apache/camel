package org.apache.camel.component.schematron;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.schematron.engine.SchematronEngineFactory;
import org.apache.camel.component.schematron.exception.SchematronConfigException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ResourceHelper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.InputStream;

/**
 * Schematron Endpoint.
 */
public class SchematronEndpoint extends DefaultEndpoint {

    private Logger logger = LoggerFactory.getLogger(SchematronEndpoint.class);
    private SchematronEngineFactory factory;
    private String remaining;

    public SchematronEndpoint() {
    }

    public SchematronEndpoint(String uri, String remaining, SchematronComponent component) {
        super(uri, component);
        this.remaining = remaining;
    }

    public SchematronEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        return new SchematronProducer(this, factory);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not implemented for this component");
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (factory == null) {

            InputStream rules = null;
            try {
                // Attempt to read the schematron rules  from the class path first.
                logger.info("Reading schematron rules from class path {}", remaining);
                rules = ResourceHelper.resolveMandatoryResourceAsInputStream
                        (getCamelContext().getClassResolver(), remaining);
            } catch (Exception e) {
                // Attempts from the file system.
                logger.info("Schamatron rules not found in class path, attempting file system {}", remaining);
                rules = FileUtils.openInputStream(new File(remaining));
            }

            if (rules == null) {
                logger.error("Schematron rules not found {}", remaining);
                throw new SchematronConfigException("Failed to load rules: " + remaining);
            }
            factory = SchematronEngineFactory.newInstance(rules);
        }


    }
}
