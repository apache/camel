package org.apache.camel.component.cxf;

import java.io.InputStream;

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;

import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPMessage;

import org.w3c.dom.Node;

public class SoapTargetBean {
    private static QName sayHi = new QName("http://apache.org/hello_world_soap_http", "sayHi");
    private static QName greetMe = new QName("http://apache.org/hello_world_soap_http", "greetMe");
    private SOAPMessage sayHiResponse;
    private SOAPMessage greetMeResponse;

    public SoapTargetBean() {

        try {
            MessageFactory factory = MessageFactory.newInstance();
            InputStream is = getClass().getResourceAsStream("sayHiDocLiteralResp.xml");
            sayHiResponse =  factory.createMessage(null, is);
            is.close();
            is = getClass().getResourceAsStream("GreetMeDocLiteralResp.xml");
            greetMeResponse =  factory.createMessage(null, is);
            is.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public SOAPMessage invoke(SOAPMessage request) {
        SOAPMessage response = null;
        try {
            SOAPBody body = request.getSOAPBody();
            Node n = body.getFirstChild();

            while (n.getNodeType() != Node.ELEMENT_NODE) {
                n = n.getNextSibling();
            }
            if (n.getLocalName().equals(sayHi.getLocalPart())) {
                response = sayHiResponse;
            } else if (n.getLocalName().equals(greetMe.getLocalPart())) {
                response = greetMeResponse;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

}
