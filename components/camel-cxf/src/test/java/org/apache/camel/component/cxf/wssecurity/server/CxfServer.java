/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.camel.component.cxf.wssecurity.server;

import java.util.HashMap;
import java.util.Map;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;

public class CxfServer {
    
    //private static final String WSU_NS
    //     = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    
    private String address;
    
    private Server server;

    public CxfServer(int port) throws Exception {
        Object implementor = new GreeterImpl();
        address = "http://localhost:" + port + "/WSSecurityRouteTest/GreeterPort";
        JaxWsServerFactoryBean bean = new JaxWsServerFactoryBean();
        bean.setAddress(address);
        bean.setServiceBean(implementor);
        bean.getInInterceptors().add(getWSS4JInInterceptor());
        bean.getOutInterceptors().add(getWSS4JOutInterceptor());
        server = bean.create();
    }
    
    public void stop() {
        if (server != null) {
            server.start();
        }
    }

    public static WSS4JOutInterceptor getWSS4JOutInterceptor() throws Exception {

        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Signature");
        //outProps.put("action", "UsernameToken Timestamp Signature Encrypt");

        outProps.put("passwordType", "PasswordText");
        outProps.put("user", "serverx509v1");
        outProps.put("passwordCallbackClass", "org.apache.camel.component.cxf.wssecurity.server.UTPasswordCallback");

        //If you are using the patch WSS-194, then uncomment below two lines and 
        //comment the above "user" prop line.
        //outProps.put("user", "Alice");
        //outProps.put("signatureUser", "serverx509v1");

        //outProps.put("encryptionUser", "clientx509v1");
        //outProps.put("encryptionPropFile", "wssecurity/etc/Server_SignVerf.properties");
        //outProps.put("encryptionKeyIdentifier", "IssuerSerial");
        //outProps.put("encryptionParts", "{Element}{" + WSU_NS + "}Timestamp;"
        //                 + "{Content}{http://schemas.xmlsoap.org/soap/envelope/}Body");

        outProps.put("signaturePropFile", "wssecurity/etc/Server_Decrypt.properties");
        outProps.put("signatureKeyIdentifier", "DirectReference");
        outProps.put("signatureParts", //"{Element}{" + WSU_NS + "}Timestamp;"
                         "{Element}{http://schemas.xmlsoap.org/soap/envelope/}Body");

        return new WSS4JOutInterceptor(outProps);
    }  
    
    public static WSS4JInInterceptor getWSS4JInInterceptor() throws Exception {

        Map<String, Object> inProps = new HashMap<String, Object>();

        //inProps.put("action", "UsernameToken Timestamp Signature Encrypt");
        inProps.put("action", "Signature");
        inProps.put("passwordType", "PasswordDigest");
        inProps.put("passwordCallbackClass", "org.apache.camel.component.cxf.wssecurity.server.UTPasswordCallback");

        //inProps.put("decryptionPropFile", "wssecurity/etc/Server_Decrypt.properties");
        //inProps.put("encryptionKeyIdentifier", "IssuerSerial");

        inProps.put("signaturePropFile", "wssecurity/etc/Server_SignVerf.properties");
        inProps.put("signatureKeyIdentifier", "DirectReference");

        return new WSS4JInInterceptor(inProps);

    }  
       
    
}
