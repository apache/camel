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

package org.apache.camel.test.infra.couchbase.services;

import org.apache.camel.test.infra.couchbase.common.CouchbaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;

public class CouchbaseLocalContainerService implements CouchbaseService {

    /*
     * Couchbase container uses a dynamic port for the KV service. The configuration
     * used in the Camel component tries to use that port by default and it seems
     * we cannot configure it. Therefore, we override the default container and
     * force the default KV port to be used.
     */
    private class CustomCouchbaseContainer extends CouchbaseContainer {
        public CustomCouchbaseContainer() {
            final int kvPort = 11210;
            addFixedExposedPort(kvPort, kvPort);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(CouchbaseLocalContainerService.class);
    private CouchbaseContainer container;

    public CouchbaseLocalContainerService() {
        container = new CustomCouchbaseContainer();
    }

    public CouchbaseLocalContainerService(String bucketName) {
        BucketDefinition bucketDefinition = new BucketDefinition(bucketName);

        container = new CustomCouchbaseContainer()
                .withBucket(bucketDefinition);
    }

    @Override
    public String getConnectionString() {
        return container.getConnectionString();
    }

    @Override
    public String getUsername() {
        return container.getUsername();
    }

    @Override
    public String getPassword() {
        return container.getPassword();
    }

    @Override
    public String getHostname() {
        return container.getHost();
    }

    @Override
    public int getPort() {
        return container.getBootstrapHttpDirectPort();
    }

    @Override
    public void registerProperties() {
        System.setProperty(CouchbaseProperties.COUCHBASE_HOSTNAME, getHostname());
        System.setProperty(CouchbaseProperties.COUCHBASE_PORT, String.valueOf(getPort()));
        System.setProperty(CouchbaseProperties.COUCHBASE_USERNAME, getUsername());
        System.setProperty(CouchbaseProperties.COUCHBASE_PASSWORD, getPassword());
    }

    @Override
    public void initialize() {
        container.start();
        registerProperties();

        LOG.debug("Couchbase container running at {}", getConnectionString());
    }

    @Override
    public void shutdown() {
        container.stop();
    }
}
