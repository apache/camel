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
package org.apache.camel.component.crypto.cms.common;

import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Collection;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.util.IOHelper;
import org.apache.commons.codec.binary.Base64InputStream;
import org.bouncycastle.cert.X509CertificateHolder;

public abstract class CryptoCmsUnmarshaller implements Processor {

    private final CryptoCmsUnMarshallerConfiguration config;

    public CryptoCmsUnmarshaller(CryptoCmsUnMarshallerConfiguration config) {
        this.config = config;
    }

    // @Override
    public CryptoCmsUnMarshallerConfiguration getConfiguration() {
        return config;
    }

    @Override
    public void process(Exchange exchange) throws Exception { // NOPMD all
                                                              // exceptions must
                                                              // be caught to
                                                              // react on
                                                              // exception case
                                                              // and re-thrown,
                                                              // see code below

        InputStream stream = exchange.getIn().getMandatoryBody(InputStream.class);
        try {
            // lets setup the out message before we invoke the dataFormat
            // so that it can mutate it if necessary
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());

            if (config.isFromBase64(exchange)) {
                stream = new Base64InputStream(stream);
            }
            Object result = unmarshalInternal(stream, exchange);
            out.setBody(result);
        } catch (Throwable e) {
            // remove OUT message, as an exception occurred
            exchange.setOut(null);
            throw e;
        } finally {
            IOHelper.close(stream, "input stream");
        }
    }

    protected abstract Object unmarshalInternal(InputStream is, Exchange exchange) throws Exception;

    protected String certsToString(Collection<X509Certificate> certs) {
        if (certs == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int size = certs.size();
        int counter = 0;
        for (X509Certificate cert : certs) {
            counter++;
            sb.append('[');
            certToString(sb, cert);
            sb.append("]");
            if (counter < size) {
                sb.append("; ");
            }
        }

        return sb.toString();
    }

    protected String issuerSerialNumberSubject(X509CertificateHolder cert) {
        StringBuilder sb = new StringBuilder();
        sb.append("Issuer=(");
        sb.append(cert.getIssuer());
        sb.append("), SerialNumber=");
        sb.append(cert.getSerialNumber());
        sb.append(", Subject=(");
        sb.append(cert.getSubject());
        sb.append(')');
        return sb.toString();
    }

    protected void certToString(StringBuilder sb, X509Certificate cert) {
        sb.append("Issuer=(");
        sb.append(cert.getIssuerX500Principal().getName());
        sb.append("), SerialNumber=");
        sb.append(cert.getSerialNumber());
        sb.append(", Subject=(");
        sb.append(cert.getSubjectX500Principal().getName());
        sb.append(')');
    }

}
