package org.apache.camel.component.djl;

import org.apache.camel.Exchange;
import org.apache.camel.component.djl.model.AbstractPredictor;
import org.apache.camel.component.djl.model.ModelPredictorProducer;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The DJL producer.
 */
public class DJLProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(DJLProducer.class);
    private DJLEndpoint endpoint;
    private AbstractPredictor abstractPredictor;

    public DJLProducer(DJLEndpoint endpoint) throws Exception {
        super(endpoint);
        this.endpoint = endpoint;
        if (endpoint.getArtifactId() !=null) {
            this.abstractPredictor = ModelPredictorProducer.getZooPredictor(endpoint.getApplication(), endpoint.getArtifactId());
        } else {
            this.abstractPredictor = ModelPredictorProducer.getCustomPredictor(endpoint.getApplication(), endpoint.getModel(), endpoint.getTranslator());
        }
    }

    public void process(Exchange exchange) throws Exception {
        this.abstractPredictor.process(exchange);
    }
}
