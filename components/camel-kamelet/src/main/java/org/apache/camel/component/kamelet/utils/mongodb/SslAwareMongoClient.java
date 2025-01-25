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
package org.apache.camel.component.kamelet.utils.mongodb;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.bulk.ClientBulkWriteOptions;
import com.mongodb.client.model.bulk.ClientBulkWriteResult;
import com.mongodb.client.model.bulk.ClientNamespacedWriteModel;
import com.mongodb.connection.ClusterDescription;
import org.apache.camel.util.function.Suppliers;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SslAwareMongoClient implements MongoClient {
    private static final Logger LOG = LoggerFactory.getLogger(SslAwareMongoClient.class);
    private static final TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkClientTrusted(X509Certificate[] arg0, String arg1)
                        throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] arg0, String arg1)
                        throws CertificateException {
                }
            }
    };
    private final Supplier<MongoClient> wrappedMongoClient = Suppliers.memorize(new Supplier<MongoClient>() {
        @Override
        public MongoClient get() {
            String credentials = username == null ? "" : username;

            if (!credentials.isEmpty()) {
                credentials += password == null ? "@" : ":" + password + "@";
            }

            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyToSslSettings(builder -> {
                        builder.enabled(ssl);
                        if (!sslValidationEnabled) {
                            builder.invalidHostNameAllowed(true);
                            SSLContext sc = null;
                            try {
                                sc = SSLContext.getInstance("TLSv1.2");
                            } catch (NoSuchAlgorithmException e) {
                                throw new RuntimeException("Error instantiating trust all SSL context.", e);
                            }
                            try {
                                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                            } catch (KeyManagementException e) {
                                throw new RuntimeException("Error instantiating trust all SSL context.", e);
                            }
                            builder.context(sc);
                        }
                    })
                    .applyConnectionString(new ConnectionString(String.format("mongodb://%s%s", credentials, hosts)))
                    .build();
            LOG.info("Connection created using provided credentials");
            return MongoClients.create(settings);
        }
    });
    private String hosts = null;
    private String username = null;
    private String password = null;
    private boolean ssl = true;

    private boolean sslValidationEnabled = true;

    public MongoClient getWrappedMongoClient() {
        return wrappedMongoClient.get();
    }

    @Override
    public MongoDatabase getDatabase(String s) {
        return getWrappedMongoClient().getDatabase(s);
    }

    @Override
    public ClientSession startSession() {
        return getWrappedMongoClient().startSession();
    }

    @Override
    public ClientSession startSession(ClientSessionOptions clientSessionOptions) {
        return getWrappedMongoClient().startSession(clientSessionOptions);
    }

    @Override
    public void close() {
        getWrappedMongoClient().close();
    }

    @Override
    public MongoIterable<String> listDatabaseNames() {
        return getWrappedMongoClient().listDatabaseNames();
    }

    @Override
    public MongoIterable<String> listDatabaseNames(ClientSession clientSession) {
        return getWrappedMongoClient().listDatabaseNames(clientSession);
    }

    @Override
    public ListDatabasesIterable<Document> listDatabases() {
        return getWrappedMongoClient().listDatabases();
    }

    @Override
    public ListDatabasesIterable<Document> listDatabases(ClientSession clientSession) {
        return getWrappedMongoClient().listDatabases(clientSession);
    }

    @Override
    public <TResult> ListDatabasesIterable<TResult> listDatabases(Class<TResult> aClass) {
        return getWrappedMongoClient().listDatabases(aClass);
    }

    @Override
    public <TResult> ListDatabasesIterable<TResult> listDatabases(ClientSession clientSession, Class<TResult> aClass) {
        return getWrappedMongoClient().listDatabases(clientSession, aClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch() {
        return getWrappedMongoClient().watch();
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(Class<TResult> aClass) {
        return getWrappedMongoClient().watch(aClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch(List<? extends Bson> list) {
        return getWrappedMongoClient().watch(list);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(List<? extends Bson> list, Class<TResult> aClass) {
        return getWrappedMongoClient().watch(list, aClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch(ClientSession clientSession) {
        return getWrappedMongoClient().watch(clientSession);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(ClientSession clientSession, Class<TResult> aClass) {
        return getWrappedMongoClient().watch(clientSession, aClass);
    }

    @Override
    public ChangeStreamIterable<Document> watch(ClientSession clientSession, List<? extends Bson> list) {
        return getWrappedMongoClient().watch(clientSession, list);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(
            ClientSession clientSession, List<? extends Bson> list,
            Class<TResult> aClass) {
        return getWrappedMongoClient().watch(clientSession, list, aClass);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(List<? extends ClientNamespacedWriteModel> list) throws ClientBulkWriteException {
        return getWrappedMongoClient().bulkWrite(list);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(
            List<? extends ClientNamespacedWriteModel> list, ClientBulkWriteOptions clientBulkWriteOptions)
            throws ClientBulkWriteException {
        return getWrappedMongoClient().bulkWrite(list, clientBulkWriteOptions);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(ClientSession clientSession, List<? extends ClientNamespacedWriteModel> list)
            throws ClientBulkWriteException {
        return getWrappedMongoClient().bulkWrite(clientSession, list);
    }

    @Override
    public ClientBulkWriteResult bulkWrite(
            ClientSession clientSession, List<? extends ClientNamespacedWriteModel> list,
            ClientBulkWriteOptions clientBulkWriteOptions)
            throws ClientBulkWriteException {
        return getWrappedMongoClient().bulkWrite(clientSession, list, clientBulkWriteOptions);
    }

    @Override
    public ClusterDescription getClusterDescription() {
        return getWrappedMongoClient().getClusterDescription();
    }

    @Override
    public CodecRegistry getCodecRegistry() {
        return getWrappedMongoClient().getCodecRegistry();
    }

    @Override
    public ReadPreference getReadPreference() {
        return getWrappedMongoClient().getReadPreference();
    }

    @Override
    public WriteConcern getWriteConcern() {
        return getWrappedMongoClient().getWriteConcern();
    }

    @Override
    public ReadConcern getReadConcern() {
        return getWrappedMongoClient().getReadConcern();
    }

    @Override
    public Long getTimeout(TimeUnit timeUnit) {
        return getWrappedMongoClient().getTimeout(timeUnit);
    }

    @Override
    public MongoCluster withCodecRegistry(CodecRegistry codecRegistry) {
        return getWrappedMongoClient().withCodecRegistry(codecRegistry);
    }

    @Override
    public MongoCluster withReadPreference(ReadPreference readPreference) {
        return getWrappedMongoClient().withReadPreference(readPreference);
    }

    @Override
    public MongoCluster withWriteConcern(WriteConcern writeConcern) {
        return getWrappedMongoClient().withWriteConcern(writeConcern);
    }

    @Override
    public MongoCluster withReadConcern(ReadConcern readConcern) {
        return getWrappedMongoClient().withReadConcern(readConcern);
    }

    @Override
    public MongoCluster withTimeout(long l, TimeUnit timeUnit) {
        return getWrappedMongoClient().withTimeout(l, timeUnit);
    }

    public String getHosts() {
        return hosts;
    }

    public void setHosts(String hosts) {
        this.hosts = hosts;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public boolean isSslValidationEnabled() {
        return sslValidationEnabled;
    }

    public void setSslValidationEnabled(boolean sslValidationEnabled) {
        this.sslValidationEnabled = sslValidationEnabled;
    }
}
