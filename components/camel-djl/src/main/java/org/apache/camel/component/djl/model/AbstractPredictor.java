package org.apache.camel.component.djl.model;

import org.apache.camel.Exchange;

public abstract class AbstractPredictor {

    public abstract void process(Exchange exchange) throws Exception;

}
