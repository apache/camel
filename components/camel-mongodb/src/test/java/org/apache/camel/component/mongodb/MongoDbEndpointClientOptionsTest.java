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
package org.apache.camel.component.mongodb;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCompressor;
import com.mongodb.ReadPreference;
import com.mongodb.Tag;
import com.mongodb.TagSet;
import com.mongodb.TaggableReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.internal.MongoClientImpl;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The client options of a mongodb endpoint (readPreference, replicaSet, the timeouts, the pool sizes and so on) are
 * applied to the client it creates from hosts or connectionUriString, on top of the connection string.
 */
class MongoDbEndpointClientOptionsTest extends CamelTestSupport {

    private static final String HOSTS = "mongodb:dummy?hosts=localhost:27017&database=test&collection=test&operation=findAll";
    private static final String CONNECTION_URI_STRING_OPTIONS
            = "mongodb:dummy?connectionUriString=RAW(mongodb://localhost:27017/?readPreference=nearest&maxPoolSize=7"
              + "&appName=fromUri&compressors=zlib,snappy&retryWrites=false)"
              + "&database=test&collection=test&operation=findAll";

    private MongoDbEndpoint endpoint(String uri) {
        return context.getEndpoint(uri, MongoDbEndpoint.class);
    }

    private MongoClientSettings settings(String uri) {
        return endpoint(uri).createMongoClientSettings();
    }

    @Test
    void defaultsAreTheDriverDefaults() {
        MongoDbEndpoint endpoint = endpoint(HOSTS);
        MongoClientSettings settings = endpoint.createMongoClientSettings();
        MongoClientSettings driver = MongoClientSettings.builder().build();

        assertThat(endpoint.getClientOptionsSet()).isEmpty();
        assertThat(settings.getReadPreference()).isEqualTo(ReadPreference.primary());
        assertThat(settings.getConnectionPoolSettings()).isEqualTo(driver.getConnectionPoolSettings());
        assertThat(settings.getSocketSettings()).isEqualTo(driver.getSocketSettings());
        assertThat(settings.getServerSettings()).isEqualTo(driver.getServerSettings());
        assertThat(settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS)).isEqualTo(30000);
        assertThat(settings.getClusterSettings().getLocalThreshold(TimeUnit.MILLISECONDS)).isEqualTo(15);
        assertThat(settings.getClusterSettings().getMode()).isEqualTo(ClusterConnectionMode.SINGLE);
        assertThat(settings.getApplicationName()).isNull();
        assertThat(settings.getCompressorList()).isEmpty();
        assertThat(settings.getRetryWrites()).isTrue();
        assertThat(settings.getRetryReads()).isTrue();
    }

    @Test
    void readPreferenceIsApplied() {
        assertThat(settings(HOSTS + "&readPreference=SECONDARY").getReadPreference()).isEqualTo(ReadPreference.secondary());
    }

    @Test
    void readPreferenceWithUnderscoreIsApplied() {
        assertThat(settings(HOSTS + "&readPreference=SECONDARY_PREFERRED").getReadPreference())
                .isEqualTo(ReadPreference.secondaryPreferred());
        assertThat(settings(HOSTS + "&readPreference=PRIMARY_PREFERRED").getReadPreference())
                .isEqualTo(ReadPreference.primaryPreferred());
        assertThat(settings(HOSTS + "&readPreference=secondaryPreferred").getReadPreference())
                .isEqualTo(ReadPreference.secondaryPreferred());
    }

    @Test
    void readPreferenceTagsAndMaxStalenessAreApplied() {
        ReadPreference readPreference = settings(
                HOSTS + "&readPreference=SECONDARY&readPreferenceTags=dc:ny,rack:1;dc:ny;&maxStalenessSeconds=120")
                .getReadPreference();

        TaggableReadPreference taggable = (TaggableReadPreference) readPreference;
        assertThat(taggable.getName()).isEqualTo("secondary");
        assertThat(taggable.getTagSetList()).containsExactly(
                new TagSet(List.of(new Tag("dc", "ny"), new Tag("rack", "1"))),
                new TagSet(new Tag("dc", "ny")),
                new TagSet());
        assertThat(taggable.getMaxStaleness(TimeUnit.SECONDS)).isEqualTo(120);
    }

    @Test
    void readPreferenceTagsInConnectionStringForm() {
        assertThat(MongoDbEndpoint.parseReadPreferenceTags("dc:ny,rack:1;readPreferenceTags=dc:ny;readPreferenceTags="))
                .containsExactly(
                        new TagSet(List.of(new Tag("dc", "ny"), new Tag("rack", "1"))),
                        new TagSet(new Tag("dc", "ny")),
                        new TagSet());
        assertThat(MongoDbEndpoint.parseReadPreferenceTags(" dc : ny ")).containsExactly(new TagSet(new Tag("dc", "ny")));
    }

    @Test
    void invalidReadPreferenceTagFails() {
        assertThatThrownBy(() -> MongoDbEndpoint.parseReadPreferenceTags("dc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key:value");
    }

    @Test
    void readPreferenceTagsWithPrimaryFails() {
        // the endpoint creates its client when it starts, so it fails to start
        assertThatThrownBy(() -> endpoint(HOSTS + "&readPreferenceTags=dc:ny"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Primary read preference can not also specify tag sets");
    }

    @Test
    void readPreferenceOfConnectionUriStringIsKept() {
        assertThat(settings(CONNECTION_URI_STRING_OPTIONS).getReadPreference()).isEqualTo(ReadPreference.nearest());
    }

    @Test
    void readPreferenceOfEndpointOverridesConnectionUriString() {
        assertThat(settings(CONNECTION_URI_STRING_OPTIONS + "&readPreference=SECONDARY").getReadPreference())
                .isEqualTo(ReadPreference.secondary());
    }

    @Test
    void optionsOfConnectionUriStringAreKept() {
        MongoClientSettings settings = settings(CONNECTION_URI_STRING_OPTIONS);

        assertThat(settings.getConnectionPoolSettings().getMaxSize()).isEqualTo(7);
        assertThat(settings.getApplicationName()).isEqualTo("fromUri");
        assertThat(settings.getCompressorList()).extracting(MongoCompressor::getName).containsExactly("zlib", "snappy");
        assertThat(settings.getRetryWrites()).isFalse();
    }

    @Test
    void optionsOfEndpointOverrideConnectionUriString() {
        MongoClientSettings settings = settings(
                CONNECTION_URI_STRING_OPTIONS + "&maxPoolSize=20&appName=fromEndpoint&compressors=zstd");

        assertThat(settings.getConnectionPoolSettings().getMaxSize()).isEqualTo(20);
        assertThat(settings.getApplicationName()).isEqualTo("fromEndpoint");
        assertThat(settings.getCompressorList()).extracting(MongoCompressor::getName).containsExactly("zstd");
    }

    @Test
    void timeoutAndPoolOptionsAreApplied() {
        MongoClientSettings settings = settings(HOSTS + "&serverSelectionTimeoutMS=1500&localThresholdMS=30"
                                                + "&heartbeatFrequencyMS=2500&connectTimeoutMS=3000&socketTimeoutMS=4000"
                                                + "&maxIdleTimeMS=5000&maxLifeTimeMS=6000&minPoolSize=3&maxPoolSize=9"
                                                + "&maxConnecting=4&waitQueueTimeoutMS=7000");

        ClusterSettings cluster = settings.getClusterSettings();
        assertThat(cluster.getServerSelectionTimeout(TimeUnit.MILLISECONDS)).isEqualTo(1500);
        assertThat(cluster.getLocalThreshold(TimeUnit.MILLISECONDS)).isEqualTo(30);
        assertThat(settings.getServerSettings().getHeartbeatFrequency(TimeUnit.MILLISECONDS)).isEqualTo(2500);
        assertThat(settings.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS)).isEqualTo(3000);
        assertThat(settings.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS)).isEqualTo(4000);
        ConnectionPoolSettings pool = settings.getConnectionPoolSettings();
        assertThat(pool.getMaxConnectionIdleTime(TimeUnit.MILLISECONDS)).isEqualTo(5000);
        assertThat(pool.getMaxConnectionLifeTime(TimeUnit.MILLISECONDS)).isEqualTo(6000);
        assertThat(pool.getMinSize()).isEqualTo(3);
        assertThat(pool.getMaxSize()).isEqualTo(9);
        assertThat(pool.getMaxConnecting()).isEqualTo(4);
        assertThat(pool.getMaxWaitTime(TimeUnit.MILLISECONDS)).isEqualTo(7000);
    }

    @Test
    void replicaSetDiscoversTheMembers() {
        ClusterSettings cluster = settings(HOSTS + "&replicaSet=rs0").getClusterSettings();

        assertThat(cluster.getRequiredReplicaSetName()).isEqualTo("rs0");
        assertThat(cluster.getMode()).isEqualTo(ClusterConnectionMode.MULTIPLE);
    }

    @Test
    void directConnectionUsesTheHostOnly() {
        ClusterSettings cluster = settings(HOSTS + "&replicaSet=rs0&directConnection=true").getClusterSettings();

        assertThat(cluster.getMode()).isEqualTo(ClusterConnectionMode.SINGLE);
    }

    @Test
    void loadBalancedIsApplied() {
        assertThat(settings(HOSTS + "&loadBalanced=true").getClusterSettings().getMode())
                .isEqualTo(ClusterConnectionMode.LOAD_BALANCED);
    }

    @Test
    void srvOptionsAreApplied() {
        ClusterSettings cluster = settings(
                "mongodb:dummy?connectionUriString=RAW(mongodb+srv://cluster.example.com/)"
                                           + "&srvServiceName=custom&srvMaxHosts=2&database=test&collection=test&operation=findAll")
                .getClusterSettings();

        assertThat(cluster.getSrvServiceName()).isEqualTo("custom");
        assertThat(cluster.getSrvMaxHosts()).isEqualTo(2);
    }

    @Test
    void compressorsAreApplied() {
        MongoClientSettings settings = settings(HOSTS + "&compressors=zlib,snappy,zstd&zlibCompressionLevel=6");

        assertThat(settings.getCompressorList()).extracting(MongoCompressor::getName).containsExactly("zlib", "snappy",
                "zstd");
        Integer level = settings.getCompressorList().get(0).getProperty(MongoCompressor.LEVEL, null);
        assertThat(level).isEqualTo(6);
    }

    @Test
    void zlibCompressionLevelAppliesToZlibOfConnectionUriString() {
        MongoClientSettings settings = settings(CONNECTION_URI_STRING_OPTIONS + "&zlibCompressionLevel=3");

        assertThat(settings.getCompressorList()).extracting(MongoCompressor::getName).containsExactly("zlib", "snappy");
        Integer level = settings.getCompressorList().get(0).getProperty(MongoCompressor.LEVEL, null);
        assertThat(level).isEqualTo(3);
    }

    @Test
    void unknownCompressorFails() {
        assertThatThrownBy(() -> endpoint(HOSTS + "&compressors=lz4"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported compressor lz4");
    }

    @Test
    void appNameAndRetriesAreApplied() {
        MongoClientSettings settings = settings(HOSTS + "&appName=myApp&retryWrites=false&retryReads=false");

        assertThat(settings.getApplicationName()).isEqualTo("myApp");
        assertThat(settings.getRetryWrites()).isFalse();
        assertThat(settings.getRetryReads()).isFalse();
    }

    @Test
    void clientCreatedFromHostsUsesTheOptions() throws Exception {
        MongoDbEndpoint endpoint = endpoint(HOSTS + "&readPreference=SECONDARY&maxPoolSize=5");
        endpoint.initializeConnection();

        MongoClientSettings settings
                = ((MongoClientImpl) endpoint.getMongoConnection()).getSettings();
        assertThat(settings.getReadPreference()).isEqualTo(ReadPreference.secondary());
        assertThat(settings.getConnectionPoolSettings().getMaxSize()).isEqualTo(5);
        // the tailable consumer and the producer read with the read preference of the collection
        assertThat(endpoint.getMongoCollection().getReadPreference()).isEqualTo(ReadPreference.secondary());
    }

    @Test
    void optionsAreReportedForAClientFromTheRegistry() {
        try (MongoClient client = MongoClients.create("mongodb://localhost:27017")) {
            context.getRegistry().bind("myDb", client);

            MongoDbEndpoint endpoint = endpoint(
                    "mongodb:myDb?database=test&collection=test&operation=findAll&readPreference=SECONDARY&maxPoolSize=5");

            // the client is not created by the endpoint, so the options cannot be applied to it
            assertThat(endpoint.getMongoConnection()).isSameAs(client);
            assertThat(endpoint.getClientOptionsSet()).containsExactly("maxPoolSize", "readPreference");
        }
    }
}
