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
package org.apache.camel.component.github2;

import java.io.IOException;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

/**
 * Factory for creating GitHub client instances.
 */
public final class GitHubClientFactory {

    private GitHubClientFactory() {
    }

    /**
     * Creates a GitHub client with the specified OAuth token and optional API URL.
     *
     * @param  oauthToken  the OAuth token for authentication
     * @param  apiUrl      the API URL for GitHub Enterprise (null or empty for github.com)
     * @return             a configured GitHub client
     * @throws IOException if the client cannot be created
     */
    public static GitHub createClient(String oauthToken, String apiUrl) throws IOException {
        GitHubBuilder builder = new GitHubBuilder()
                .withOAuthToken(oauthToken);

        if (apiUrl != null && !apiUrl.isEmpty()) {
            // GitHub Enterprise support
            builder.withEndpoint(apiUrl);
        }

        return builder.build();
    }
}
