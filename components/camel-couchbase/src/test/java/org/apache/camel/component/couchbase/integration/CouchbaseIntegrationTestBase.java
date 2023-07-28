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

package org.apache.camel.component.couchbase.integration;

import java.time.Duration;
import java.util.Collections;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.manager.bucket.BucketSettings;
import com.couchbase.client.java.manager.bucket.BucketType;
import com.couchbase.client.java.manager.view.DesignDocument;
import com.couchbase.client.java.manager.view.View;
import com.couchbase.client.java.view.DesignDocumentNamespace;
import org.apache.camel.test.infra.common.TestUtils;
import org.apache.camel.test.infra.couchbase.services.CouchbaseService;
import org.apache.camel.test.infra.couchbase.services.CouchbaseServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CouchbaseIntegrationTestBase extends CamelTestSupport {
    @RegisterExtension
    public static CouchbaseService service = CouchbaseServiceFactory.createService();

    protected static String bucketName;
    protected static Cluster cluster;

    @BeforeAll
    static void setUpCouchbase() {
        bucketName = "testBucket" + TestUtils.randomWithRange(0, 100);
        cluster = Cluster.connect(service.getConnectionString(), service.getUsername(), service.getPassword());

        cluster.buckets().createBucket(
                BucketSettings.create(bucketName).bucketType(BucketType.COUCHBASE).flushEnabled(true));

        Bucket bucket = cluster.bucket(bucketName);
        DesignDocument designDoc = new DesignDocument(
                bucketName,
                Collections.singletonMap(bucketName, new View("function (doc, meta) {  emit(meta.id, doc);}")));
        cluster.bucket(bucketName).viewIndexes().upsertDesignDocument(designDoc, DesignDocumentNamespace.PRODUCTION);
    }

    @BeforeEach
    public void waitForStarted() {
        cluster.bucket(bucketName).waitUntilReady(Duration.ofSeconds(30));
    }

    @AfterAll
    public static void tearDownCouchbase() {
        cluster.buckets().dropBucket(bucketName);
        cluster.disconnect();
    }

    public String getConnectionUri() {
        return String.format("couchbase:http://%s:%d?bucket=%s&username=%s&password=%s", service.getHostname(),
                service.getPort(), bucketName, service.getUsername(), service.getPassword());
    }

}
