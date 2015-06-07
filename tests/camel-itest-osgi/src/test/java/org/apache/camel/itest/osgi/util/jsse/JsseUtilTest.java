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
package org.apache.camel.itest.osgi.util.jsse;

import java.io.File;
import java.net.URL;
import java.security.KeyStore;

import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

@RunWith(PaxExam.class)
public class JsseUtilTest extends OSGiIntegrationSpringTestSupport {
    
    @Test
    public void testSslContextParameters() throws Exception {
        SSLContextParameters scp = this.applicationContext.getBean(SSLContextParameters.class);
        
        assertEquals("TLS", scp.getSecureSocketProtocol());
        
        assertNotNull(scp.getKeyManagers());
        assertEquals("changeit", scp.getKeyManagers().getKeyPassword());
        assertNull(scp.getKeyManagers().getProvider());
        assertNotNull(scp.getKeyManagers().getKeyStore());
        assertNull(scp.getKeyManagers().getKeyStore().getType());
        
        assertNotNull(scp.getTrustManagers());
        assertNull(scp.getTrustManagers().getProvider());
        assertNotNull(scp.getTrustManagers().getKeyStore());
        assertNull(scp.getTrustManagers().getKeyStore().getType());
        
        assertNull(scp.getSecureRandom());
        
        assertNull(scp.getClientParameters());
        
        assertNull(scp.getServerParameters());
        
        // Test that the instantiation will work when running in OSGi and using
        // class path resources.
        scp.createSSLContext();
    }
    
    @Test
    public void testKeyStoreParametersResourceLoading() throws Exception {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setPassword("changeit");
        ksp.setResource("org/apache/camel/itest/osgi/util/jsse/localhost.ks");

        KeyStore ks = ksp.createKeyStore();
        assertNotNull(ks.getCertificate("server"));

        URL resourceUrl = this.getClass().getResource("/org/apache/camel/itest/osgi/util/jsse/localhost.ks");
        ksp.setResource(resourceUrl.toExternalForm());
        ks = ksp.createKeyStore();
        assertNotNull(ks.getCertificate("server"));

        File file = new File("../../../test-classes/org/apache/camel/itest/osgi/util/jsse/localhost.ks");
        LOG.info("the file is {}", file.getAbsolutePath());
        ksp.setResource(file.getAbsolutePath());
        ks = ksp.createKeyStore();
        assertNotNull(ks.getCertificate("server"));
    }

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/util/jsse/JsseUtilTest-context.xml"});
    }

}
