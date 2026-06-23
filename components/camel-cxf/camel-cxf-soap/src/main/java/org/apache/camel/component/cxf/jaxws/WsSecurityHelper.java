/*
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
package org.apache.camel.component.cxf.jaxws;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.cxf.message.Message;
import org.apache.wss4j.dom.engine.WSSecurityEngineResult;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.apache.wss4j.dom.handler.WSHandlerResult;

/**
 * Extracts X.509 certificates from WS-Security processing results. This class is isolated so that
 * {@link DefaultCxfBinding} does not require wss4j on the classpath — it is only loaded when wss4j is present.
 */
final class WsSecurityHelper {

    private WsSecurityHelper() {
    }

    static Collection<X509Certificate> extractCertificates(Message cxfMessage) {
        final Object recv = cxfMessage.get(WSHandlerConstants.RECV_RESULTS);
        if (recv == null) {
            return null;
        }

        Collection<X509Certificate> certs = null;

        if (recv instanceof Map<?, ?> map) {
            certs = extractFromMap(map);
        } else if (recv instanceof List<?> list) {
            certs = extractFromHandlerResults(list);
        }

        return certs;
    }

    private static Collection<X509Certificate> extractFromMap(Map<?, ?> map) {
        Object v = map.get(WSSecurityEngineResult.TAG_X509_CERTIFICATES);

        Collection<X509Certificate> certs = null;
        if (v instanceof Collection<?> coll) {
            certs = new ArrayList<>();
            for (Object o : coll) {
                if (o instanceof X509Certificate cert) {
                    certs.add(cert);
                } else if (o instanceof X509Certificate[] arr) {
                    for (X509Certificate c : arr) {
                        if (c != null) {
                            certs.add(c);
                        }
                    }
                }
            }
        } else if (v instanceof X509Certificate[] arr) {
            certs = new ArrayList<>();
            for (X509Certificate c : arr) {
                if (c != null) {
                    certs.add(c);
                }
            }
        }
        return certs;
    }

    private static Collection<X509Certificate> extractFromHandlerResults(List<?> list) {
        if (list.isEmpty() || !(list.get(0) instanceof WSHandlerResult)) {
            return null;
        }

        Collection<X509Certificate> certs = new ArrayList<>();
        for (Object hrObj : list) {
            if (!(hrObj instanceof WSHandlerResult hr)) {
                continue;
            }
            for (WSSecurityEngineResult r : hr.getResults()) {
                Object v = r.get(WSSecurityEngineResult.TAG_X509_CERTIFICATES);

                if (v instanceof X509Certificate[] arr) {
                    for (X509Certificate c : arr) {
                        if (c != null) {
                            certs.add(c);
                        }
                    }
                } else if (v instanceof X509Certificate cert) {
                    certs.add(cert);
                } else {
                    Object leaf = r.get(WSSecurityEngineResult.TAG_X509_CERTIFICATE);
                    if (leaf instanceof X509Certificate cert) {
                        certs.add(cert);
                    }
                }
            }
        }
        return certs.isEmpty() ? null : certs;
    }
}
