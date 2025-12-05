package org.apache.camel.mdc;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.processor.DelegateProcessor;

@Deprecated
public class MDCProcessor extends DelegateProcessor {

    private final MDCService mdc;

    public MDCProcessor(MDCService mdc, Processor processor) {
        super(processor);
        this.mdc = mdc;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        mdc.setMDC(exchange);
        try {
            super.process(exchange);
        } finally {
//            mdc.unsetMDC(exchange);
        }
    }

}
