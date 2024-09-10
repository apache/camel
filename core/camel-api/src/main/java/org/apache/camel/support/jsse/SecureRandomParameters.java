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
import java.security.SecureRandom;
import java.security.Security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecureRandomParameters extends JsseParameters {

    private static final Logger LOG = LoggerFactory.getLogger(SecureRandomParameters.class);

    protected String algorithm;
    protected String provider;

    /**
     * Returns a {@code SecureRandom} instance initialized using the configured algorithm and provider, if specified.
     *
     * @return                          the configured instance
     *
     * @throws GeneralSecurityException if the algorithm is not implemented by any registered provider or if the
     *                                  identified provider does not exist.
     */
    public SecureRandom createSecureRandom() throws GeneralSecurityException {
        LOG.debug("Creating SecureRandom from SecureRandomParameters: {}", this);

        SecureRandom secureRandom;
        if (this.getProvider() != null) {
            secureRandom = SecureRandom.getInstance(this.parsePropertyValue(this.getAlgorithm()),
                    this.parsePropertyValue(this.getProvider()));
        } else {
            secureRandom = SecureRandom.getInstance(this.parsePropertyValue(this.getAlgorithm()));
        }

        LOG.debug("SecureRandom [{}] is using provider [{}] and algorithm [{}].",
                secureRandom, secureRandom.getProvider(), secureRandom.getAlgorithm());

        return secureRandom;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Sets the Random Number Generator (RNG) algorithm identifier for the {@link SecureRandom} factory method used to
     * create the {@link SecureRandom} represented by this object's configuration.
     *
     * See https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html
     *
     * @param value the algorithm identifier
     */
    public void setAlgorithm(String value) {
        this.algorithm = value;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * Sets the optional provider identifier for the {@link SecureRandom} factory method used to create the
     * {@link SecureRandom} represented by this object's configuration.
     *
     * @param value the provider identifier or {@code null} to use the highest priority provider implementing the
     *              desired algorithm
     *
     * @see         Security#getProviders()
     */
    public void setProvider(String value) {
        this.provider = value;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SecureRandomParameters[algorithm=");
        builder.append(algorithm);
        builder.append(", provider=");
        builder.append(provider);
        builder.append("]");
        return builder.toString();
    }
}
