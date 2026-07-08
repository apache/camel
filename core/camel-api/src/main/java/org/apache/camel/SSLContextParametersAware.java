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
package org.apache.camel;

import org.apache.camel.support.jsse.SSLContextParameters;
import org.jspecify.annotations.Nullable;

/**
 * Marker interface for Camel components and endpoints that can consume the context-wide
 * {@link org.apache.camel.support.jsse.SSLContextParameters} configured on the {@link CamelContext}.
 * <p/>
 * Camel supports a single global SSL/TLS configuration via
 * {@link CamelContext#setSSLContextParameters(org.apache.camel.support.jsse.SSLContextParameters)}. Components that
 * implement this interface expose the {@link #isUseGlobalSslContextParameters()} /
 * {@link #setUseGlobalSslContextParameters(boolean)} pair; when enabled, {@link #retrieveGlobalSslContextParameters()}
 * fetches the context-level instance so the component does not need its own per-endpoint TLS configuration. This
 * pattern avoids duplicating keystore and truststore settings across many endpoints while still allowing per-endpoint
 * overrides when the component has its own {@code SSLContextParameters} property.
 * <p/>
 * See <a href="https://camel.apache.org/manual/camel-configuration-utilities.html">JSSE Utility</a> in the Camel user
 * manual.
 *
 * @see org.apache.camel.support.jsse.SSLContextParameters
 */
public interface SSLContextParametersAware extends CamelContextAware {

    /**
     * Returns the global {@link SSLContextParameters} if enabled on the implementing object, null otherwise.
     */
    default @Nullable SSLContextParameters retrieveGlobalSslContextParameters() {
        CamelContext ctx = getCamelContext();
        if (ctx != null && isUseGlobalSslContextParameters()) {
            return ctx.getSSLContextParameters();
        }
        return null;
    }

    /**
     * Determine if the implementing object is using global SSL context parameters.
     */
    boolean isUseGlobalSslContextParameters();

    /**
     * Enable usage of global SSL context parameters.
     */
    void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters);

}
