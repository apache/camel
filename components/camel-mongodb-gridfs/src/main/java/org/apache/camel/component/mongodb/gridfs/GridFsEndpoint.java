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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Store, read and remove files in a MongoDB GridFS bucket, and receive the files that are uploaded to it.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "mongodb-gridfs", title = "MongoDB GridFS",
             syntax = "mongodb-gridfs:connectionBean", category = { Category.DATABASE, Category.FILE },
             headersClass = GridFsConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.STREAM,
                                   description = "One message per new file: a stream of the content of the file (the"
                                                 + " stream cache when stream caching is on), closed when the exchange is"
                                                 + " done, with its name, length, upload date, content type and metadata in"
                                                 + " headers"),
           producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE },
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "findOne", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "listAll", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "count", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "remove", carriers = Carrier.ANY) },
                                  description = "The content of the file that create, the default operation, uploads under"
                                                + " the name in the CamelFileName header: text, bytes, a stream or a file. A"
                                                + " Map, a POJO or no body fail. The other operations do not use the body;"
                                                + " the CamelGridFsOperation header sets the operation when the option is not"
                                                + " set"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "findOne",
                                                          carriers = Carrier.STREAM),
                                           @BodyType.When(option = "operation", value = "listAll",
                                                          carriers = Carrier.STREAM),
                                           @BodyType.When(option = "operation", value = "count", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Long"),
                                           @BodyType.When(option = "operation", value = "remove",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "create, the default, leaves the body unchanged, with the id of the file in"
                                                 + " the CamelGridFsFileId header. findOne gives a stream of the content of"
                                                 + " the file named by CamelFileName; listAll a Reader of text, one line per"
                                                 + " file with its name and id (the hex string of an ObjectId) separated by a"
                                                 + " tab; count the number of files as a Long; remove leaves the body"
                                                 + " unchanged"))
public class GridFsEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(GridFsEndpoint.class);

    @UriPath(description = "The name of the com.mongodb.client.MongoClient bean in the registry that connects to MongoDB."
                           + " The endpoint closes this client when it shuts down.")
    @Metadata(required = true)
    private String connectionBean;
    @UriParam(endpointIdentity = true,
              description = "The name of the MongoDB database that holds the GridFS bucket, and the collection of the"
                            + " persistent timestamp of the consumer.")
    @Metadata(required = true)
    private String database;
    @UriParam(defaultValue = "fs",
              description = "The name of the GridFS bucket. GridFS stores the files of the bucket in the two collections"
                            + " named after it with the suffixes .files and .chunks, such as fs.files and fs.chunks.")
    private String bucket;
    @UriParam(enums = "ACKNOWLEDGED,W1,W2,W3,UNACKNOWLEDGED,JOURNALED,MAJORITY",
              description = "The write concern for the writes of the endpoint, as the name of a constant of"
                            + " com.mongodb.WriteConcern. If not set, the write concern of the MongoClient is used.")
    private WriteConcern writeConcern;
    @UriParam(description = "The read preference for the reads of the endpoint, as a name that"
                            + " com.mongodb.ReadPreference.valueOf(String) accepts, such as primary, secondary or nearest. If"
                            + " not set, the read preference of the MongoClient is used.")
    private ReadPreference readPreference;

    @UriParam(label = "producer",
              description = "The operation that the producer performs: create (the default), remove, findOne, listAll or"
                            + " count. If this option is not set, the CamelGridFsOperation header chooses the operation; the"
                            + " header does not replace this option.")
    private String operation;

    @UriParam(label = "consumer",
              description = "A query document in JSON that the consumer combines with the query of the queryStrategy"
                            + " option, to route only the matching files. Its fields are those of the document of a file in"
                            + " the bucket, such as filename or metadata.type.")
    private String query;
    @UriParam(label = "consumer", defaultValue = "1000", javaType = "java.time.Duration",
              description = "The time in milliseconds from the start of the consumer to its first poll.")
    private long initialDelay = 1000;
    @UriParam(label = "consumer", defaultValue = "500", javaType = "java.time.Duration",
              description = "The time in milliseconds from the end of one poll to the start of the next poll.")
    private long delay = 500;
    @UriParam(label = "consumer", defaultValue = "TimeStamp",
              description = "How the consumer finds the new files. TimeStamp routes the files uploaded after the consumer"
                            + " started or after the last file it routed, and PersistentTimestamp stores that timestamp in"
                            + " MongoDB, so that it survives a restart. FileAttribute routes the files that do not have the"
                            + " field of the fileAttributeName option, and the strategies ending in AndFileAttribute require"
                            + " both.")
    private QueryStrategy queryStrategy = QueryStrategy.TimeStamp;
    @UriParam(label = "consumer", defaultValue = "camel-timestamps",
              description = "The name of the collection in the database that stores the timestamp of the"
                            + " PersistentTimestamp and PersistentTimestampAndFileAttribute strategies.")
    private String persistentTSCollection = "camel-timestamps";
    @UriParam(label = "consumer", defaultValue = "camel-timestamp",
              description = "The value of the id field of the document that stores the timestamp in the collection of the"
                            + " persistentTSCollection option. Consumers that keep separate timestamps need different"
                            + " values.")
    private String persistentTSObject = "camel-timestamp";
    @UriParam(label = "consumer", defaultValue = "camel-processed",
              description = "The name of the field that the strategies with a file attribute set in the document of a file:"
                            + " processing while the file is routed, and done after it. The consumer removes the field when"
                            + " the exchange fails, so that the next poll offers the file again.")
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
        GridFsConsumer consumer = new GridFsConsumer(this, processor);
        // the exceptionHandler and bridgeErrorHandler options
        configureConsumer(consumer);
        return consumer;
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

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public void setQueryStrategy(String s) {
        queryStrategy = QueryStrategy.valueOf(s);
    }

    public void setQueryStrategy(QueryStrategy queryStrategy) {
        this.queryStrategy = queryStrategy;
    }

    public QueryStrategy getQueryStrategy() {
        return queryStrategy;
    }

    public void setPersistentTSCollection(String s) {
        persistentTSCollection = s;
    }

    public String getPersistentTSCollection() {
        return persistentTSCollection;
    }

    public void setPersistentTSObject(String id) {
        persistentTSObject = id;
    }

    public String getPersistentTSObject() {
        return persistentTSObject;
    }

    public void setFileAttributeName(String f) {
        fileAttributeName = f;
    }

    public String getFileAttributeName() {
        return fileAttributeName;
    }

    public void setWriteConcern(String writeConcern) {
        this.writeConcern = WriteConcern.valueOf(writeConcern);
    }

    public void setWriteConcern(WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
    }

    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    public void setReadPreference(String readPreference) {
        this.readPreference = ReadPreference.valueOf(readPreference);
    }

    public void setReadPreference(ReadPreference readPreference) {
        this.readPreference = readPreference;
    }

    public ReadPreference getReadPreference() {
        return readPreference;
    }

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
