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
package org.apache.camel.component.netty.ssl;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SSLEngineFactory {

    private static final Logger LOG = LoggerFactory.getLogger(SSLEngineFactory.class);

    private static final String SSL_PROTOCOL = "TLSv1.3";

    // PQC named group constants — keep in sync with SSLContextParameters.PQC_NAMED_GROUP
    // and SSLContextParameters.PQC_PREFERRED_NAMED_GROUPS
    private static final String PQC_NAMED_GROUP = "X25519MLKEM768";
    private static final List<String> PQC_PREFERRED_NAMED_GROUPS
            = List.of("X25519MLKEM768", "x25519", "secp256r1", "secp384r1");

    // Reflection handles for JDK 20+ SSLParameters named groups API
    private static final Method GET_NAMED_GROUPS;
    private static final Method SET_NAMED_GROUPS;

    static {
        Method gng = null, sng = null;
        try {
            gng = SSLParameters.class.getMethod("getNamedGroups");
            sng = SSLParameters.class.getMethod("setNamedGroups", String[].class);
        } catch (NoSuchMethodException e) {
            // JDK < 20 — named groups API not available
        }
        GET_NAMED_GROUPS = gng;
        SET_NAMED_GROUPS = sng;
    }

    public SSLEngineFactory() {
    }

    public SSLContext createSSLContext(
            CamelContext camelContext, String keyStoreFormat, String securityProvider,
            String keyStoreResource, String trustStoreResource, char[] passphrase)
            throws Exception {
        SSLContext answer;
        KeyStore ks = KeyStore.getInstance(keyStoreFormat);

        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, keyStoreResource);
        try {
            ks.load(is, passphrase);
        } finally {
            IOHelper.close(is);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(securityProvider);
        kmf.init(ks, passphrase);

        answer = SSLContext.getInstance(SSL_PROTOCOL);

        if (trustStoreResource != null) {
            KeyStore ts = KeyStore.getInstance(keyStoreFormat);
            is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, trustStoreResource);
            try {
                ts.load(is, passphrase);
            } finally {
                IOHelper.close(is);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(securityProvider);
            tmf.init(ts);
            answer.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } else {
            answer.init(kmf.getKeyManagers(), null, null);
        }

        return answer;
    }

    /**
     * Applies post-quantum named group defaults to an {@link SSLEngine} when the JVM supports them (typically JDK 25+).
     * <p>
     * When {@code X25519MLKEM768} is available, this method reorders the named groups to prioritize post-quantum key
     * exchange, matching the auto-configuration behavior of
     * {@link org.apache.camel.support.jsse.SSLContextParameters#createSSLContext}. This should be called on SSLEngines
     * created via the SSLEngineFactory fallback path (without SSLContextParameters).
     */
    public static void applyPqcNamedGroups(SSLEngine engine) {
        if (GET_NAMED_GROUPS == null || SET_NAMED_GROUPS == null) {
            return;
        }

        try {
            SSLParameters params = engine.getSSLParameters();
            String[] availableGroups = (String[]) GET_NAMED_GROUPS.invoke(params);

            if (availableGroups == null) {
                return;
            }

            List<String> available = Arrays.asList(availableGroups);
            if (!available.contains(PQC_NAMED_GROUP)) {
                return;
            }

            // Build preferred ordering: PQC preferred groups first, then remaining
            List<String> ordered = new ArrayList<>();
            for (String preferred : PQC_PREFERRED_NAMED_GROUPS) {
                if (available.contains(preferred)) {
                    ordered.add(preferred);
                }
            }
            for (String group : availableGroups) {
                if (!ordered.contains(group)) {
                    ordered.add(group);
                }
            }

            SET_NAMED_GROUPS.invoke(params, (Object) ordered.toArray(new String[0]));
            engine.setSSLParameters(params);

            LOG.debug("Applied PQC named groups to SSLEngine: {}", ordered);
        } catch (Exception e) {
            LOG.debug("Could not apply PQC named groups to SSLEngine: {}", e.getMessage());
        }
    }

    /**
     * @deprecated Unused — all initializer factories create SSLEngines directly via
     *             {@code sslContext.createSSLEngine()}. Use {@link #applyPqcNamedGroups(SSLEngine)} on engines created
     *             directly from the SSLContext instead.
     */
    @Deprecated(since = "4.19.0")
    public SSLEngine createServerSSLEngine(SSLContext sslContext) {
        SSLEngine serverEngine = sslContext.createSSLEngine();
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);
        applyPqcNamedGroups(serverEngine);
        return serverEngine;
    }

    /**
     * @deprecated Unused — all initializer factories create SSLEngines directly via
     *             {@code sslContext.createSSLEngine()}. Use {@link #applyPqcNamedGroups(SSLEngine)} on engines created
     *             directly from the SSLContext instead.
     */
    @Deprecated(since = "4.19.0")
    public SSLEngine createClientSSLEngine(SSLContext sslContext) {
        SSLEngine clientEngine = sslContext.createSSLEngine();
        clientEngine.setUseClientMode(true);
        applyPqcNamedGroups(clientEngine);
        return clientEngine;
    }

}
