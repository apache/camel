package org.apache.camel.component.cxf.wssecurity.camel;

import java.io.InputStream;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPMessage;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * This processor is used to create a new SOAPMessage from the in message stream
 * which will be used as output message.
 */
public class MyProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // take out the soap message as an inputStream
        InputStream is = exchange.getIn().getBody(InputStream.class);
        // put it as an soap message
        SOAPMessage message = MessageFactory.newInstance().createMessage(null, is);
        exchange.getOut().setBody(message);
    }

}
