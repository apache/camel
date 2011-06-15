package org.apache.camel.component.cxf.transport;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.component.cxf.common.header.CxfHeaderHelper;
import org.apache.camel.component.cxf.common.message.CxfMessageHelper;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.MessageObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CamelOutputStream extends CachedOutputStream {
    private static final Logger LOG = LoggerFactory.getLogger(CamelOutputStream.class);
    
    /**
     * 
     */
    private Message outMessage;
    private boolean isOneWay;
    private String targetCamelEndpointUri;
    private Producer producer;
    private HeaderFilterStrategy headerFilterStrategy;
    private MessageObserver observer;

    public CamelOutputStream(String targetCamelEndpointUri, Producer producer, 
                             HeaderFilterStrategy headerFilterStrategy, MessageObserver observer, 
                             Message m) {
        this.targetCamelEndpointUri = targetCamelEndpointUri;
        this.producer = producer;
        this.headerFilterStrategy = headerFilterStrategy;
        this.observer = observer;
        outMessage = m;
    }

    protected void doFlush() throws IOException {
        // do nothing here
    }

    protected void doClose() throws IOException {
        isOneWay = outMessage.getExchange().isOneWay();
        commitOutputMessage();
    }

    protected void onWrite() throws IOException {
        // do nothing here
    }


    private void commitOutputMessage() throws IOException {
        ExchangePattern pattern;
        if (isOneWay) {
            pattern = ExchangePattern.InOnly;
        } else {
            pattern = ExchangePattern.InOut;
        }
        LOG.debug("send the message to endpoint {}", this.targetCamelEndpointUri);
        org.apache.camel.Exchange exchange = this.producer.createExchange(pattern);

        exchange.setProperty(Exchange.TO_ENDPOINT, this.targetCamelEndpointUri);
        CachedOutputStream outputStream = (CachedOutputStream) outMessage.getContent(OutputStream.class);
        // Send out the request message here, copy the protocolHeader back
        CxfHeaderHelper.propagateCxfToCamel(this.headerFilterStrategy, outMessage, exchange.getIn().getHeaders(), exchange);

        // TODO support different encoding
        exchange.getIn().setBody(outputStream.getInputStream());
        LOG.debug("template sending request: ", exchange.getIn());
        Exception exception;
        try {
            this.producer.process(exchange);
        } catch (Exception ex) {
            exception = ex;
        }
        // Throw the exception that the template get
        exception = exchange.getException();
        if (exception != null) {
            throw new IOException("Cannot send the request message.", exchange.getException());
        }
        exchange.setProperty(CamelTransportConstants.CXF_EXCHANGE, outMessage.getExchange());
        if (!isOneWay) {
            handleResponse(exchange);
        }

    }

    private void handleResponse(org.apache.camel.Exchange exchange) throws IOException {
        org.apache.cxf.message.Message inMessage = null;
        try {
            inMessage = CxfMessageHelper.getCxfInMessage(this.headerFilterStrategy, exchange, true);
        } catch (Exception ex) {
            throw new IOException("Cannot get the response message. ", ex);
        }
        this.observer.onMessage(inMessage);
    }
}