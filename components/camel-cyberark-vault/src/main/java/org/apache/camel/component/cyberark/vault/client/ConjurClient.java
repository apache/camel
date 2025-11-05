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
package org.apache.camel.component.cyberark.vault.client;

/**
 * Interface for CyberArk Conjur client operations
 */
public interface ConjurClient extends AutoCloseable {

    /**
     * Retrieve a secret from Conjur
     *
     * @param  secretId the ID of the secret to retrieve
     * @return          the secret value
     */
    String retrieveSecret(String secretId);

    /**
     * Retrieve a specific version of a secret from Conjur
     *
     * @param  secretId the ID of the secret to retrieve
     * @param  version  the version of the secret (can be null for latest)
     * @return          the secret value
     */
    String retrieveSecret(String secretId, String version);

    /**
     * Create or update a secret in Conjur
     *
     * @param secretId    the ID of the secret to create/update
     * @param secretValue the value to store
     */
    void createSecret(String secretId, String secretValue);

    /**
     * Authenticate with Conjur and obtain an access token
     *
     * @return the access token
     */
    String authenticate();

    /**
     * Close the client and release resources
     */
    @Override
    void close() throws Exception;
}
