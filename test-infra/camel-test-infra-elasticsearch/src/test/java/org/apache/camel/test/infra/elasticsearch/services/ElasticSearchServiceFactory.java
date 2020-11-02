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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElasticSearchServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchServiceFactory.class);

    private ElasticSearchServiceFactory() {

    }

    public static ElasticSearchService createService() {
        String instanceType = System.getProperty("elasticsearch.instance.type");

        if (instanceType == null || instanceType.equals("local-elasticsearch-container")) {
            return new ElasticSearchLocalContainerService();
        }

        if (instanceType.equals("remote")) {
            return new RemoteElasticSearchService();
        }

        LOG.error("ElasticSearch instance must be one of 'local-elasticsearch-container' or 'remote");
        throw new UnsupportedOperationException("Invalid ElasticSearch instance type");
    }
}
