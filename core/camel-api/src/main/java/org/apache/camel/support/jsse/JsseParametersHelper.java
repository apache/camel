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
package org.apache.camel.support.jsse;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.camel.CamelContext;
import org.jspecify.annotations.Nullable;

/**
 * Helps the components that map {@link SSLContextParameters} onto a TLS stack of their own, such as the {@code ssl.*}
 * properties of the Kafka client, instead of using the {@link SSLContext} of
 * {@link SSLContextParameters#createSSLContext(CamelContext)}.
 * <p>
 * Only that {@code SSLContext} applies the options that live on the {@link SSLEngine} and the {@code SSLSocket}: the
 * named groups, the signature schemes, and the post-quantum preference of the named groups that it applies when none
 * are configured. A component that cannot carry them uses this helper to say so, instead of dropping them in silence.
 *
 * @since 4.24
 */
public final class JsseParametersHelper {

    private JsseParametersHelper() {
    }

    /**
     * The options configured on the given parameters that only the {@code SSLContext} of
     * {@link SSLContextParameters#createSSLContext(CamelContext)} applies, so a component that hands the key and trust
     * material to a TLS stack of its own loses them: {@code namedGroups}, {@code namedGroupsFilter},
     * {@code signatureSchemes} and {@code signatureSchemesFilter}.
     * <p>
     * The post-quantum preference that {@code createSSLContext} applies when no named groups are configured is not an
     * option of the user and is not listed; see {@link #postQuantumNamedGroups(SSLContextParameters)}.
     *
     * @param  parameters the SSL configuration
     * @return            the names of the configured options, in that order, or an empty list when none is configured
     */
    public static List<String> engineOnlyParameters(SSLContextParameters parameters) {
        List<String> answer = new ArrayList<>(4);
        if (parameters.getNamedGroups() != null) {
            answer.add("namedGroups");
        }
        if (parameters.getNamedGroupsFilter() != null) {
            answer.add("namedGroupsFilter");
        }
        if (parameters.getSignatureSchemes() != null) {
            answer.add("signatureSchemes");
        }
        if (parameters.getSignatureSchemesFilter() != null) {
            answer.add("signatureSchemesFilter");
        }
        return answer;
    }

    /**
     * The named group order that {@link SSLContextParameters#createSSLContext(CamelContext)} would apply on this JVM to
     * prefer the post-quantum hybrid groups, as the named groups of the given parameters are not configured.
     * <p>
     * The named groups of the JVM are read from its default {@code SSLContext}.
     *
     * @param  parameters the SSL configuration
     * @return            the order, or {@code null} when the parameters configure the named groups, or when the JVM
     *                    offers no post-quantum group or already leads with one, in which case there is no preference
     *                    to lose
     */
    public static @Nullable List<String> postQuantumNamedGroups(SSLContextParameters parameters) {
        if (parameters.getNamedGroups() != null || parameters.getNamedGroupsFilter() != null) {
            return null;
        }
        String[] available;
        try {
            available = SSLContext.getDefault().getDefaultSSLParameters().getNamedGroups();
        } catch (GeneralSecurityException e) {
            return null;
        }
        return postQuantumNamedGroups(available);
    }

    /**
     * Orders the given named groups so that the post-quantum hybrids come first, followed by the classical fallbacks
     * and then everything else, preserving the spelling of the JVM. This is the order that
     * {@link SSLContextParameters#createSSLContext(CamelContext)} applies when no named groups are configured, for a
     * component that creates its {@link SSLEngine} without {@link SSLContextParameters}.
     * <p>
     * An order that already leads with a hybrid is left alone: it is either the default of the JVM or one an operator
     * chose through {@code jdk.tls.namedGroups}, which must not be rewritten.
     *
     * @param  availableGroups the named groups the JVM offers, in its order, such as from
     *                         {@code SSLParameters.getNamedGroups()}
     * @return                 the preferred order, or {@code null} when the JVM offers no post-quantum group or already
     *                         leads with one, in which case the order is to be left alone
     */
    public static @Nullable List<String> postQuantumNamedGroups(String @Nullable [] availableGroups) {
        return SSLContextParameters.resolvePqcNamedGroups(availableGroups);
    }
}
