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

package org.apache.camel.test.infra.elasticsearch.services;

import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.apache.camel.test.infra.elasticsearch.common.ElasticSearchProperties;

public class RemoteElasticSearchService implements ElasticSearchService {
    private static final int ELASTIC_SEARCH_PORT = 9200;

    @Override
    public int getPort() {
        String strPort = System.getProperty(ElasticSearchProperties.ELASTIC_SEARCH_PORT);

        if (strPort != null) {
            return Integer.parseInt(strPort);
        }

        return ELASTIC_SEARCH_PORT;
    }

    @Override
    public String getElasticSearchHost() {
        return System.getProperty(ElasticSearchProperties.ELASTIC_SEARCH_HOST);
    }

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    @Override
    public Optional<String> getCertificatePath() {
        return Optional.ofNullable(System.getProperty(ElasticSearchProperties.ELASTIC_SEARCH_CERTIFICATE_PATH));
    }

    @Override
    public Optional<SSLContext> getSslContext() {
        return Optional.empty();
    }

    @Override
    public String getUsername() {
        return System.getProperty(ElasticSearchProperties.ELASTIC_SEARCH_USERNAME);
    }

    @Override
    public String getPassword() {
        return System.getProperty(ElasticSearchProperties.ELASTIC_SEARCH_PASSWORD);
    }
}
