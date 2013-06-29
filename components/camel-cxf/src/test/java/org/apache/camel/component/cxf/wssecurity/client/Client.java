/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf.wssecurity.client;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.hello_world_soap_http.Greeter;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;


public final class Client {

    //private static final String WSU_NS
    //    = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    private JaxWsProxyFactoryBean bean;

    public Client(String address) throws Exception {
        bean = new JaxWsProxyFactoryBean();
        bean.setAddress(address);
        bean.getInInterceptors().add(getWSS4JInInterceptor());
        bean.getOutInterceptors().add(getWSS4JOutInterceptor());
        bean.setServiceClass(Greeter.class);
    }
    
    public Greeter getClient() {
        return bean.create(Greeter.class);
    }
    

    public static WSS4JOutInterceptor getWSS4JOutInterceptor() throws Exception {

        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Signature");
        // outProps.put("action", "UsernameToken Timestamp Signature Encrypt");

        outProps.put("passwordType", "PasswordDigest");
        outProps.put("user", "clientx509v1");

        // If you are using the patch WSS-194, then uncomment below two lines
        // and comment the above "user" prop line.
        // outProps.put("user", "abcd");
        // outProps.put("signatureUser", "clientx509v1");

        outProps.put("passwordCallbackClass",
                     "org.apache.camel.component.cxf.wssecurity.client.UTPasswordCallback");

        // outProps.put("encryptionUser", "serverx509v1");
        // outProps.put("encryptionPropFile",
        // "wssecurity/etc/Client_Encrypt.properties");
        // outProps.put("encryptionKeyIdentifier", "IssuerSerial");
        // outProps.put("encryptionParts",
        // "{Element}{" + WSU_NS + "}Timestamp;"
        // + "{Content}{http://schemas.xmlsoap.org/soap/envelope/}Body");

        outProps.put("signaturePropFile", "wssecurity/etc/Client_Sign.properties");
        outProps.put("signatureKeyIdentifier", "DirectReference");
        outProps.put("signatureParts",
        // "{Element}{" + WSU_NS + "}Timestamp;"
                     "{Element}{http://schemas.xmlsoap.org/soap/envelope/}Body");

        return new WSS4JOutInterceptor(outProps);
    }

    public static WSS4JInInterceptor getWSS4JInInterceptor() throws Exception {

        Map<String, Object> inProps = new HashMap<String, Object>();

        inProps.put("action", "Signature");
        // inProps.put("action", "UsernameToken Timestamp Signature Encrypt");
        // inProps.put("passwordType", "PasswordText");
        // inProps.put("passwordCallbackClass",
        // "org.apache.camel.component.cxf.wssecurity.client.UTPasswordCallback");

        // inProps.put("decryptionPropFile",
        // "wssecurity/etc/Client_Sign.properties");
        // inProps.put("encryptionKeyIdentifier", "IssuerSerial");

        inProps.put("signaturePropFile", "wssecurity/etc/Client_Encrypt.properties");
        inProps.put("signatureKeyIdentifier", "DirectReference");

        return new WSS4JInInterceptor(inProps);

    }
}
