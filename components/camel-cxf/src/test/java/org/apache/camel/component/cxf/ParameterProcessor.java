package org.apache.camel.component.cxf;

import java.util.List;

import javax.xml.soap.SOAPMessage;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class ParameterProcessor implements Processor {

    public void process(Exchange exchange) throws Exception {
        SOAPMessage soapMessage = (SOAPMessage)exchange.getIn().getBody(List.class).get(0);
        exchange.getIn().setBody(soapMessage);
    }

}
