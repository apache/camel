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
package org.apache.camel.component.stitch.client;

import org.apache.camel.util.ObjectHelper;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

public final class StitchClientBuilder {
    private HttpClient httpClient;
    private String token;
    private ConnectionProvider connectionProvider;
    private StitchRegion region;

    private StitchClientBuilder() {
    }

    public static StitchClientBuilder builder() {
        return new StitchClientBuilder();
    }

    public StitchClientBuilder withHttpClient(HttpClient httpClient) {
        if (ObjectHelper.isNotEmpty(httpClient)) {
            this.httpClient = httpClient;
        }
        return this;
    }

    public StitchClientBuilder withToken(String token) {
        if (ObjectHelper.isNotEmpty(token)) {
            this.token = token;
        }
        return this;
    }

    public StitchClientBuilder withConnectionProvider(ConnectionProvider connectionProvider) {
        if (ObjectHelper.isNotEmpty(connectionProvider)) {
            this.connectionProvider = connectionProvider;
        }
        return this;
    }

    public StitchClientBuilder withRegion(StitchRegion region) {
        if (ObjectHelper.isNotEmpty(region)) {
            this.region = region;
        }
        return this;
    }

    public StitchClientImpl build() {
        // let's check if we have all the required properties
        if (ObjectHelper.isEmpty(token) || ObjectHelper.isEmpty(region)) {
            throw new IllegalArgumentException("Token or Region cannot be empty!");
        }

        // if we supplied the HttpClient
        if (ObjectHelper.isNotEmpty(httpClient)) {
            return new StitchClientImpl(httpClient, getBaseUrl(region), token);
        }

        // if we supplied the ConnectionProvider
        if (ObjectHelper.isNotEmpty(connectionProvider)) {
            return new StitchClientImpl(HttpClient.create(connectionProvider), getBaseUrl(region), token);
        }

        // otherwise create using the default options
        return new StitchClientImpl(HttpClient.create(), getBaseUrl(region), token);
    }

    private String getBaseUrl(final StitchRegion stitchRegion) {
        return "https://" + stitchRegion.getUrl();
    }
}
