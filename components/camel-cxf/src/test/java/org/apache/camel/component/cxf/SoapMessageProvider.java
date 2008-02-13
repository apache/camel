package org.apache.camel.component.cxf;

import javax.xml.soap.SOAPMessage;
import javax.xml.ws.Provider;
import javax.xml.ws.Service;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.WebServiceProvider;

@WebServiceProvider(portName = "SoapProviderPort", serviceName = "SOAPProviderService",
                    targetNamespace = "http://apache.org/hello_world_soap_http",
 wsdlLocation = "/wsdl/hello_world.wsdl")

@ServiceMode(value = Service.Mode.MESSAGE)
public class SoapMessageProvider implements Provider<SOAPMessage> {

    public SOAPMessage invoke(SOAPMessage request) {
        //request should not come here as camel route would intercept the call before this is invoked.
        throw new UnsupportedOperationException("Placeholder method");
    }

}
