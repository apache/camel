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

/**
 * Indicates that an object is able to use the global {@link SSLContextParameters} if configured.
 */
public interface SSLContextParametersAware extends CamelContextAware {

    /**
     * Returns the global {@link SSLContextParameters} if enabled on the implementing object, null otherwise.
     */
    default SSLContextParameters retrieveGlobalSslContextParameters() {
        if (isUseGlobalSslContextParameters()) {
            return getCamelContext().getSSLContextParameters();
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
