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

import java.util.Collections;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.manager.bucket.BucketSettings;
import com.couchbase.client.java.manager.bucket.BucketType;
import com.couchbase.client.java.manager.view.DesignDocument;
import com.couchbase.client.java.manager.view.View;
import com.couchbase.client.java.view.DesignDocumentNamespace;
import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.couchbase.common.CouchbaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.utility.DockerImageName;

@InfraService(service = CouchbaseInfraService.class,
              description = "NoSQL database Couchbase",
              serviceAlias = { "couchbase" })
public class CouchbaseLocalContainerInfraService implements CouchbaseInfraService, ContainerService<CouchbaseContainer> {

    /*
     * Couchbase container uses a dynamic port for the KV service. The configuration
     * used in the Camel component tries to use that port by default, and it seems
     * we cannot configure it. Therefore, we override the default container and
     * force the default KV port to be used.
     */
    private class CustomCouchbaseContainer extends CouchbaseContainer {
        public CustomCouchbaseContainer(String imageName) {
            super(DockerImageName.parse(imageName).asCompatibleSubstituteFor("couchbase/server"));

            final int kvPort = 11210;
            addFixedExposedPort(kvPort, kvPort);

            final int managementPort = 8091;
            addFixedExposedPort(managementPort, managementPort);

            final int viewPort = 8092;
            addFixedExposedPort(viewPort, viewPort);

            final int queryPort = 8093;
            addFixedExposedPort(queryPort, queryPort);

            final int searchPort = 8094;
            addFixedExposedPort(searchPort, searchPort);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(CouchbaseLocalContainerInfraService.class);
    private final CouchbaseContainer container;

    public CouchbaseLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(
                CouchbaseLocalContainerInfraService.class,
                CouchbaseProperties.COUCHBASE_CONTAINER));
    }

    public CouchbaseLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
    }

    public CouchbaseLocalContainerInfraService(CouchbaseContainer container) {
        this.container = container;
    }

    protected CouchbaseContainer initContainer(String imageName) {
        return new CustomCouchbaseContainer(imageName);
    }

    @Override
    public String getConnectionString() {
        return container.getConnectionString();
    }

    @Override
    public String getUsername() {
        return username();
    }

    @Override
    public String getPassword() {
        return password();
    }

    @Override
    public String getHostname() {
        return hostname();
    }

    @Override
    public int getPort() {
        return port();
    }

    @Override
    public String protocol() {
        return "http";
    }

    @Override
    public String hostname() {
        return container.getHost();
    }

    @Override
    public int port() {
        return container.getBootstrapHttpDirectPort();
    }

    @Override
    public String username() {
        return container.getUsername();
    }

    @Override
    public String password() {
        return container.getPassword();
    }

    @Override
    public String bucket() {
        String bucketName = "myBucket";

        Cluster cluster = Cluster.connect(getConnectionString(), username(), password());
        cluster.buckets().createBucket(
                BucketSettings.create(bucketName).bucketType(BucketType.COUCHBASE));

        DesignDocument designDoc = new DesignDocument(
                designDocumentName(),
                Collections.singletonMap(
                        viewName(),
                        new View("function (doc, meta) {  emit(meta.id, doc);}")));
        cluster.bucket(bucketName).viewIndexes().upsertDesignDocument(designDoc, DesignDocumentNamespace.PRODUCTION);

        return bucketName;
    }

    @Override
    public String viewName() {
        return "myView";
    }

    @Override
    public String designDocumentName() {
        return "myDesignDocument";
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

    @Override
    public CouchbaseContainer getContainer() {
        return container;
    }
}
