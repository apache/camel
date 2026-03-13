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
package org.apache.camel.component.iggy;

import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IggyConfigurationTest {

    @Test
    public void testTlsDefaults() {
        IggyConfiguration configuration = new IggyConfiguration();
        assertFalse(configuration.isTlsEnabled());
        assertNull(configuration.getTlsCertificatePath());
    }

    @Test
    public void testTlsEnabled() {
        IggyConfiguration configuration = new IggyConfiguration();
        configuration.setTlsEnabled(true);
        assertTrue(configuration.isTlsEnabled());
    }

    @Test
    public void testTlsCertificatePath() {
        IggyConfiguration configuration = new IggyConfiguration();
        configuration.setTlsCertificatePath("/path/to/cert.pem");
        assertEquals("/path/to/cert.pem", configuration.getTlsCertificatePath());
    }

    @Test
    public void testTlsConfigurationCopy() {
        IggyConfiguration configuration = new IggyConfiguration();
        configuration.setTlsEnabled(true);
        configuration.setTlsCertificatePath("/path/to/cert.pem");

        IggyConfiguration copy = configuration.copy();
        assertTrue(copy.isTlsEnabled());
        assertEquals("/path/to/cert.pem", copy.getTlsCertificatePath());
    }

    @Test
    public void testTlsEndpointUri() throws Exception {
        org.apache.camel.impl.DefaultCamelContext context = new org.apache.camel.impl.DefaultCamelContext();
        context.start();
        try {
            IggyEndpoint endpoint = context.getEndpoint(
                    "iggy:myTopic?tlsEnabled=true&tlsCertificatePath=/path/to/cert.pem&streamName=myStream&username=user&password=pass",
                    IggyEndpoint.class);
            assertTrue(endpoint.getConfiguration().isTlsEnabled());
            assertEquals("/path/to/cert.pem", endpoint.getConfiguration().getTlsCertificatePath());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testSslContextParametersDefault() {
        IggyConfiguration configuration = new IggyConfiguration();
        assertNull(configuration.getSslContextParameters());
    }

    @Test
    public void testSslContextParameters() {
        IggyConfiguration configuration = new IggyConfiguration();
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        configuration.setSslContextParameters(sslContextParameters);
        assertNotNull(configuration.getSslContextParameters());
        assertEquals(sslContextParameters, configuration.getSslContextParameters());
    }

    @Test
    public void testSslContextParametersCopy() {
        IggyConfiguration configuration = new IggyConfiguration();
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        configuration.setSslContextParameters(sslContextParameters);

        IggyConfiguration copy = configuration.copy();
        assertNotNull(copy.getSslContextParameters());
        assertEquals(sslContextParameters, copy.getSslContextParameters());
    }

    @Test
    public void testSslContextParametersEndpointUri() throws Exception {
        org.apache.camel.impl.DefaultCamelContext context = new org.apache.camel.impl.DefaultCamelContext();
        context.start();
        try {
            SSLContextParameters sslContextParameters = new SSLContextParameters();
            context.getRegistry().bind("sslContextParameters", sslContextParameters);

            IggyEndpoint endpoint = context.getEndpoint(
                    "iggy:myTopic?sslContextParameters=#sslContextParameters&streamName=myStream&username=user&password=pass",
                    IggyEndpoint.class);
            assertNotNull(endpoint.getConfiguration().getSslContextParameters());
            assertEquals(sslContextParameters, endpoint.getConfiguration().getSslContextParameters());
        } finally {
            context.stop();
        }
    }
}
