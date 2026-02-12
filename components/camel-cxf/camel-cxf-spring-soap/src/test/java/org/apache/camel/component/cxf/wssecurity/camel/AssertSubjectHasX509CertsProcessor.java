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
package org.apache.camel.component.cxf.wssecurity.camel;

import java.security.cert.X509Certificate;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.message.CxfConstants;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssertSubjectHasX509CertsProcessor implements Processor {
    @Override
    public void process(Exchange exchange) {
        Object auth = exchange.getIn().getHeader(CxfConstants.AUTHENTICATION);
        assertNotNull(auth, "Expected " + CxfConstants.AUTHENTICATION + " header");
        assertTrue(auth instanceof Subject, "Expected AUTHENTICATION to be a Subject but was: "
                                            + auth.getClass());

        Subject subject = (Subject) auth;
        Set<X509Certificate> certs = subject.getPublicCredentials(X509Certificate.class);

        assertNotNull(certs, "Subject public credentials should not be null");
        assertFalse(certs.isEmpty(), "Expected at least one X509Certificate in Subject public credentials");

        for (X509Certificate c : certs) {
            assertNotNull(c);
            assertNotNull(c.getSubjectX500Principal());
        }
    }
}
