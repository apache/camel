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
package org.apache.camel.component.mongodb.gridfs;

import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interact with MongoDB GridFS.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "mongodb-gridfs", title = "MongoDB GridFS",
             syntax = "mongodb-gridfs:connectionBean", category = { Category.DATABASE, Category.FILE },
             headersClass = GridFsConstants.class)
public class GridFsEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(GridFsEndpoint.class);

    @UriPath
    @Metadata(required = true)
    private String connectionBean;
    @UriParam
    @Metadata(required = true)
    private String database;
    @UriParam(defaultValue = "fs")
    private String bucket;
    @UriParam(enums = "ACKNOWLEDGED,W1,W2,W3,UNACKNOWLEDGED,JOURNALED,MAJORITY")
    private WriteConcern writeConcern;
    @UriParam
    private ReadPreference readPreference;

    @UriParam(label = "producer")
    private String operation;

    @UriParam(label = "consumer")
    private String query;
    @UriParam(label = "consumer", defaultValue = "1000", javaType = "java.time.Duration")
    private long initialDelay = 1000;
    @UriParam(label = "consumer", defaultValue = "500", javaType = "java.time.Duration")
    private long delay = 500;
    @UriParam(label = "consumer", defaultValue = "TimeStamp")
    private QueryStrategy queryStrategy = QueryStrategy.TimeStamp;
    @UriParam(label = "consumer", defaultValue = "camel-timestamps")
    private String persistentTSCollection = "camel-timestamps";
    @UriParam(label = "consumer", defaultValue = "camel-timestamp")
    private String persistentTSObject = "camel-timestamp";
    @UriParam(label = "consumer", defaultValue = "camel-processed")
    private String fileAttributeName = "camel-processed";

    private MongoClient mongoConnection;
    private MongoDatabase db;
    private GridFSBucket gridFSBucket;
    private MongoCollection<GridFSFile> filesCollection;

    public GridFsEndpoint(String uri, GridFsComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        initializeConnection();
        return new GridFsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        initializeConnection();
        return new GridFsConsumer(this, processor);
    }

    public void initializeConnection() throws Exception {
        LOG.info("Initialize GridFS endpoint: {}", this);
        if (database == null) {
            throw new IllegalStateException("Missing required endpoint configuration: database");
        }
        db = mongoConnection.getDatabase(database);
        if (db == null) {
            throw new IllegalStateException("Could not initialize GridFsComponent. Database " + database + " does not exist.");
        }

        if (bucket != null) {
            gridFSBucket = GridFSBuckets.create(db, bucket);
        } else {
            gridFSBucket = GridFSBuckets.create(db);
        }

        this.filesCollection = db.getCollection(gridFSBucket.getBucketName() + ".files", GridFSFile.class);
    }

    @Override
    protected void doInit() throws Exception {
        mongoConnection = CamelContextHelper.mandatoryLookup(getCamelContext(), connectionBean, MongoClient.class);
        LOG.debug("Resolved the connection with the name {} as {}", connectionBean, mongoConnection);
        setWriteReadOptionsOnConnection();
        super.doInit();
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        if (mongoConnection != null) {
            LOG.debug("Closing connection");
            mongoConnection.close();
        }
    }

    private void setWriteReadOptionsOnConnection() {
        // Set the WriteConcern
        if (writeConcern != null) {
            db = db.withWriteConcern(writeConcern);
        }

        // Set the ReadPreference
        if (readPreference != null) {
            db = db.withReadPreference(readPreference);
        }
    }

    // ======= Getters and setters ===============================================

    public String getConnectionBean() {
        return connectionBean;
    }

    /**
     * Name of {@link com.mongodb.client.MongoClient} to use.
     */
    public void setConnectionBean(String connectionBean) {
        this.connectionBean = connectionBean;
    }

    public MongoClient getMongoConnection() {
        return mongoConnection;
    }

    /**
     * Sets the Mongo instance that represents the backing connection
     *
     * @param mongoConnection the connection to the database
     */
    public void setMongoConnection(MongoClient mongoConnection) {
        this.mongoConnection = mongoConnection;
    }

    public MongoDatabase getDB() {
        return db;
    }

    public String getDatabase() {
        return database;
    }

    /**
     * Sets the name of the MongoDB database to target
     *
     * @param database name of the MongoDB database
     */
    public void setDatabase(String database) {
        this.database = database;
    }

    /**
     * Sets the name of the GridFS bucket within the database. Default is fs.
     */
    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getQuery() {
        return query;
    }

    /**
     * Additional query parameters (in JSON) that are used to configure the query used for finding files in the
     * GridFsConsumer
     */
    public void setQuery(String query) {
        this.query = query;
    }

    public long getDelay() {
        return delay;
    }

    /**
     * Sets the delay between polls within the Consumer. Default is 500ms
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    /**
     * Sets the initialDelay before the consumer will start polling. Default is 1000ms
     */
    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    /**
     * Sets the QueryStrategy that is used for polling for new files. Default is Timestamp
     */
    public void setQueryStrategy(String s) {
        queryStrategy = QueryStrategy.valueOf(s);
    }

    /**
     * Sets the QueryStrategy that is used for polling for new files. Default is Timestamp
     */
    public void setQueryStrategy(QueryStrategy queryStrategy) {
        this.queryStrategy = queryStrategy;
    }

    public QueryStrategy getQueryStrategy() {
        return queryStrategy;
    }

    /**
     * If the QueryType uses a persistent timestamp, this sets the name of the collection within the DB to store the
     * timestamp.
     */
    public void setPersistentTSCollection(String s) {
        persistentTSCollection = s;
    }

    public String getPersistentTSCollection() {
        return persistentTSCollection;
    }

    /**
     * If the QueryType uses a persistent timestamp, this is the ID of the object in the collection to store the
     * timestamp.
     */
    public void setPersistentTSObject(String id) {
        persistentTSObject = id;
    }

    public String getPersistentTSObject() {
        return persistentTSObject;
    }

    /**
     * If the QueryType uses a FileAttribute, this sets the name of the attribute that is used. Default is
     * "camel-processed".
     */
    public void setFileAttributeName(String f) {
        fileAttributeName = f;
    }

    public String getFileAttributeName() {
        return fileAttributeName;
    }

    /**
     * Set the {@link WriteConcern} for write operations on MongoDB using the standard ones. Resolved from the fields of
     * the WriteConcern class by calling the {@link WriteConcern#valueOf(String)} method.
     *
     * @param writeConcern the standard name of the WriteConcern
     * @see                <a href=
     *                     "http://api.mongodb.org/java/current/com/mongodb/WriteConcern.html#valueOf(java.lang.String)">possible
     *                     options</a>
     */
    public void setWriteConcern(String writeConcern) {
        this.writeConcern = WriteConcern.valueOf(writeConcern);
    }

    /**
     * Set the {@link WriteConcern} for write operations on MongoDB using the standard ones. Resolved from the fields of
     * the WriteConcern class by calling the {@link WriteConcern#valueOf(String)} method.
     *
     * @param writeConcern the standard name of the WriteConcern
     * @see                <a href=
     *                     "http://api.mongodb.org/java/current/com/mongodb/WriteConcern.html#valueOf(java.lang.String)">possible
     *                     options</a>
     */
    public void setWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
    }

    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    /**
     * Sets a MongoDB {@link ReadPreference} on the Mongo connection. Read preferences set directly on the connection
     * will be overridden by this setting.
     * <p/>
     * The {@link com.mongodb.ReadPreference#valueOf(String)} utility method is used to resolve the passed
     * {@code readPreference} value. Some examples for the possible values are {@code nearest}, {@code primary} or
     * {@code secondary} etc.
     *
     * @param readPreference the name of the read preference to set
     */
    public void setReadPreference(String readPreference) {
        this.readPreference = ReadPreference.valueOf(readPreference);
    }

    /**
     * Sets a MongoDB {@link ReadPreference} on the Mongo connection. Read preferences set directly on the connection
     * will be overridden by this setting.
     * <p/>
     * The {@link com.mongodb.ReadPreference#valueOf(String)} utility method is used to resolve the passed
     * {@code readPreference} value. Some examples for the possible values are {@code nearest}, {@code primary} or
     * {@code secondary} etc.
     *
     * @param readPreference the name of the read preference to set
     */
    public void setReadPreference(ReadPreference readPreference) {
        this.readPreference = readPreference;
    }

    public ReadPreference getReadPreference() {
        return readPreference;
    }

    /**
     * Sets the operation this endpoint will execute against GridFs.
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }

    public GridFSBucket getGridFsBucket() {
        return gridFSBucket;
    }

    public void setGridFsBucket(GridFSBucket gridFs) {
        this.gridFSBucket = gridFs;
    }

    public MongoCollection<GridFSFile> getFilesCollection() {
        return filesCollection;
    }
}
