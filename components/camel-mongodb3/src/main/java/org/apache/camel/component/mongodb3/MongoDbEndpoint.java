/**
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
package org.apache.camel.component.mongodb3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClient;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.mongodb3.MongoDbOperation.command;
import static org.apache.camel.component.mongodb3.MongoDbOperation.findAll;
import static org.apache.camel.component.mongodb3.MongoDbOperation.getDbStats;
import static org.apache.camel.component.mongodb3.MongoDbOperation.valueOf;
import static org.apache.camel.component.mongodb3.MongoDbOutputType.Document;
import static org.apache.camel.component.mongodb3.MongoDbOutputType.DocumentList;
import static org.apache.camel.component.mongodb3.MongoDbOutputType.MongoIterable;

/**
 * Component for working with documents stored in MongoDB database.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "mongodb3", title = "MongoDB", syntax = "mongodb3:connectionBean",
    consumerClass = MongoDbTailableCursorConsumer.class, label = "database,nosql")
public class MongoDbEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbEndpoint.class);

    private MongoClient mongoConnection;

    @UriPath
    @Metadata(required = "true")
    private String connectionBean;
    @UriParam
    private String database;
    @UriParam
    private String collection;
    @UriParam
    private String collectionIndex;
    @UriParam
    private MongoDbOperation operation;
    @UriParam(defaultValue = "true")
    private boolean createCollection = true;
    private WriteConcern writeConcernRef;
    @UriParam(label = "advanced")
    private boolean dynamicity;
    @UriParam(label = "advanced")
    private boolean writeResultAsHeader;
    // tailable cursor consumer by default
    private MongoDbConsumerType consumerType;
    @UriParam(label = "advanced", defaultValue = "1000")
    private long cursorRegenerationDelay = 1000L;
    @UriParam(label = "tail")
    private String tailTrackIncreasingField;

    // persistent tail tracking
    @UriParam(label = "tail")
    private boolean persistentTailTracking;
    @UriParam(label = "tail")
    private String persistentId;
    @UriParam(label = "tail")
    private String tailTrackDb;
    @UriParam(label = "tail")
    private String tailTrackCollection;
    @UriParam(label = "tail")
    private String tailTrackField;
    private MongoDbTailTrackingConfig tailTrackingConfig;

    @UriParam
    private MongoDbOutputType outputType;

    private MongoDatabase mongoDatabase;
    private MongoCollection<Document> mongoCollection;

    // ======= Constructors ===============================================

    public MongoDbEndpoint() {
    }

    public MongoDbEndpoint(String uri, MongoDbComponent component) {
        super(uri, component);
    }

    // ======= Implementation methods =====================================

    public Producer createProducer() throws Exception {
        validateProducerOptions();
        initializeConnection();
        return new MongoDbProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        validateConsumerOptions();

        // we never create the collection
        createCollection = false;
        initializeConnection();

        // select right consumer type
        if (consumerType == null) {
            consumerType = MongoDbConsumerType.tailable;
        }

        Consumer consumer;
        if (consumerType == MongoDbConsumerType.tailable) {
            consumer = new MongoDbTailableCursorConsumer(this, processor);
        } else {
            throw new CamelMongoDbException("Consumer type not supported: " + consumerType);
        }

        configureConsumer(consumer);
        return consumer;
    }

    /**
     * Check if outputType is compatible with operation. DbCursor and
     * DocumentList applies to findAll. Document applies to others.
     */
    @SuppressWarnings("unused") // TODO: validate Output on createProducer
                                // method.
    private void validateOutputType() {
        if (!ObjectHelper.isEmpty(outputType)) {
            if (DocumentList.equals(outputType) && !(findAll.equals(operation))) {
                throw new IllegalArgumentException("outputType DocumentList is only compatible with operation findAll");
            }
            if (MongoIterable.equals(outputType) && !(findAll.equals(operation))) {
                throw new IllegalArgumentException("outputType MongoIterable is only compatible with operation findAll");
            }
            if (Document.equals(outputType) && (findAll.equals(operation))) {
                throw new IllegalArgumentException("outputType Document is not compatible with operation findAll");
            }
        }
    }

    private void validateProducerOptions() throws IllegalArgumentException {
        // make our best effort to validate, options with defaults are checked
        // against their defaults, which is not always a guarantee that
        // they haven't been explicitly set, but it is enough
        if (!ObjectHelper.isEmpty(consumerType) || persistentTailTracking || !ObjectHelper.isEmpty(tailTrackDb) || !ObjectHelper.isEmpty(tailTrackCollection)
            || !ObjectHelper.isEmpty(tailTrackField) || cursorRegenerationDelay != 1000L) {
            throw new IllegalArgumentException("consumerType, tailTracking, cursorRegenerationDelay options cannot appear on a producer endpoint");
        }
    }

    private void validateConsumerOptions() throws IllegalArgumentException {
        // make our best effort to validate, options with defaults are checked
        // against their defaults, which is not always a guarantee that
        // they haven't been explicitly set, but it is enough
        if (!ObjectHelper.isEmpty(operation) || dynamicity || outputType != null) {
            throw new IllegalArgumentException("operation, dynamicity, outputType " + "options cannot appear on a consumer endpoint");
        }
        if (consumerType == MongoDbConsumerType.tailable) {
            if (tailTrackIncreasingField == null) {
                throw new IllegalArgumentException("tailTrackIncreasingField option must be set for tailable cursor MongoDB consumer endpoint");
            }
            if (persistentTailTracking && (ObjectHelper.isEmpty(persistentId))) {
                throw new IllegalArgumentException("persistentId is compulsory for persistent tail tracking");
            }
        }
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * Initialises the MongoDB connection using the Mongo object provided to the
     * endpoint
     * 
     * @throws CamelMongoDbException
     */
    public void initializeConnection() throws CamelMongoDbException {
        LOG.info("Initialising MongoDb endpoint: {}", this.toString());
        if (database == null || (collection == null && !(getDbStats.equals(operation) || command.equals(operation)))) {
            throw new CamelMongoDbException("Missing required endpoint configuration: database and/or collection");
        }
        mongoDatabase = mongoConnection.getDatabase(database);
        if (mongoDatabase == null) {
            throw new CamelMongoDbException("Could not initialise MongoDbComponent. Database " + database + " does not exist.");
        }
        if (collection != null) {
            if (!createCollection && !databaseContainsCollection(collection)) {
                throw new CamelMongoDbException("Could not initialise MongoDbComponent. Collection " + collection + " and createCollection is false.");
            }
            mongoCollection = mongoDatabase.getCollection(collection, Document.class);

            LOG.debug("MongoDb component initialised and endpoint bound to MongoDB collection with the following parameters. Address list: {}, Db: {}, Collection: {}",
                      new Object[] {mongoConnection.getAllAddress().toString(), mongoDatabase.getName(), collection});

            try {
                if (ObjectHelper.isNotEmpty(collectionIndex)) {
                    ensureIndex(mongoCollection, createIndex());
                }
            } catch (Exception e) {
                throw new CamelMongoDbException("Error creating index", e);
            }
        }
    }

    private boolean databaseContainsCollection(String collectionName) {
        return StreamSupport.stream(mongoDatabase.listCollectionNames().spliterator(), false).anyMatch(collectionName::equals);
    }

    /**
     * Add Index
     *
     * @param aCollection
     */
    public void ensureIndex(MongoCollection<Document> aCollection, List<Bson> dynamicIndex) {
        if (dynamicIndex != null && !dynamicIndex.isEmpty()) {
            for (Bson index : dynamicIndex) {
                LOG.debug("create Document Index {}", index);
                aCollection.createIndex(index);
            }
        }
    }

    /**
     * Create technical list index
     *
     * @return technical list index
     */
    @SuppressWarnings("unchecked")
    public List<Bson> createIndex() {
        try {
            List<Bson> indexList = new ArrayList<>();

            if (ObjectHelper.isNotEmpty(collectionIndex)) {
                HashMap<String, String> indexMap = new ObjectMapper().readValue(collectionIndex, HashMap.class);

                for (Map.Entry<String, String> set : indexMap.entrySet()) {
                    Document index = new Document();
                    // MongoDB 2.4 upwards is restrictive about the type of the
                    // 'single field index' being
                    // in use below (set.getValue())) as only an integer value
                    // type is accepted, otherwise
                    // server will throw an exception, see more details:
                    // http://docs.mongodb.org/manual/release-notes/2.4/#improved-validation-of-index-types
                    index.put(set.getKey(), set.getValue());

                    indexList.add(index);
                }
            }
            return indexList;
        } catch (IOException e) {
            throw new CamelMongoDbException("createIndex failed", e);
        }
    }

    public Exchange createMongoDbExchange(Document dbObj) {
        Exchange exchange = super.createExchange();
        Message message = exchange.getIn();
        message.setHeader(MongoDbConstants.DATABASE, database);
        message.setHeader(MongoDbConstants.COLLECTION, collection);
        message.setHeader(MongoDbConstants.FROM_TAILABLE, true);
        message.setBody(dbObj);
        return exchange;
    }
    
    @Override
    protected void doStart() throws Exception {
        mongoConnection = CamelContextHelper.mandatoryLookup(getCamelContext(), connectionBean, MongoClient.class);
        LOG.debug("Resolved the connection with the name {} as {}", connectionBean, mongoConnection);
        super.doStart();
    }
    
    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (mongoConnection != null) {
            LOG.debug("Closing connection");
            mongoConnection.close();
        }
    }

    // ======= Getters and setters
    // ===============================================

    public String getConnectionBean() {
        return connectionBean;
    }

    /**
     * Name of {@link com.mongodb.Mongo} to use.
     */
    public void setConnectionBean(String connectionBean) {
        this.connectionBean = connectionBean;
    }

    /**
     * Sets the name of the MongoDB collection to bind to this endpoint
     *
     * @param collection collection name
     */
    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getCollection() {
        return collection;
    }

    /**
     * Sets the collection index (JSON FORMAT : { "field1" : order1, "field2" :
     * order2})
     */
    public void setCollectionIndex(String collectionIndex) {
        this.collectionIndex = collectionIndex;
    }

    public String getCollectionIndex() {
        return collectionIndex;
    }

    /**
     * Sets the operation this endpoint will execute against MongoDB. For
     * possible values, see {@link MongoDbOperation}.
     *
     * @param operation name of the operation as per catalogued values
     * @throws CamelMongoDbException
     */
    public void setOperation(String operation) throws CamelMongoDbException {
        try {
            this.operation = valueOf(operation);
        } catch (IllegalArgumentException e) {
            throw new CamelMongoDbException("Operation not supported", e);
        }
    }

    public MongoDbOperation getOperation() {
        return operation;
    }

    /**
     * Sets the name of the MongoDB database to target
     * 
     * @param database name of the MongoDB database
     */
    public void setDatabase(String database) {
        this.database = database;
    }

    public String getDatabase() {
        return database;
    }

    /**
     * Create collection during initialisation if it doesn't exist. Default is
     * true.
     * 
     * @param createCollection true or false
     */
    public void setCreateCollection(boolean createCollection) {
        this.createCollection = createCollection;
    }

    public boolean isCreateCollection() {
        return createCollection;
    }

    /**
     * Sets the Mongo instance that represents the backing connection
     * 
     * @param mongoConnection the connection to the database
     */
    public void setMongoConnection(MongoClient mongoConnection) {
        this.mongoConnection = mongoConnection;
    }

    public MongoClient getMongoConnection() {
        return mongoConnection;
    }

    public WriteConcern getWriteConcern() {
        return getMongoConnection().getWriteConcern();
    }

    /**
     * Set the {@link WriteConcern} for write operations on MongoDB, passing in
     * the bean ref to a custom WriteConcern which exists in the Registry. You
     * can also use standard WriteConcerns by passing in their key. See the
     * {@link #setWriteConcern(String) setWriteConcern} method.
     * 
     * @param writeConcernRef the name of the bean in the registry that
     *            represents the WriteConcern to use
     */
    public void setWriteConcernRef(String writeConcernRef) {
        WriteConcern wc = this.getCamelContext().getRegistry().lookupByNameAndType(writeConcernRef, WriteConcern.class);
        if (wc == null) {
            String msg = "Camel MongoDB component could not find the WriteConcern in the Registry. Verify that the " + "provided bean name (" + writeConcernRef
                         + ")  is correct. Aborting initialization.";
            throw new IllegalArgumentException(msg);
        }

        this.writeConcernRef = wc;
    }

    public WriteConcern getWriteConcernRef() {
        return writeConcernRef;
    }

    public ReadPreference getReadPreference() {
        return getMongoConnection().getReadPreference();
    }

    /**
     * Sets whether this endpoint will attempt to dynamically resolve the target
     * database and collection from the incoming Exchange properties. Can be
     * used to override at runtime the database and collection specified on the
     * otherwise static endpoint URI. It is disabled by default to boost
     * performance. Enabling it will take a minimal performance hit.
     * 
     * @see MongoDbConstants#DATABASE
     * @see MongoDbConstants#COLLECTION
     * @param dynamicity true or false indicated whether target database and
     *            collection should be calculated dynamically based on Exchange
     *            properties.
     */
    public void setDynamicity(boolean dynamicity) {
        this.dynamicity = dynamicity;
    }

    public boolean isDynamicity() {
        return dynamicity;
    }

    /**
     * Reserved for future use, when more consumer types are supported.
     *
     * @param consumerType key of the consumer type
     * @throws CamelMongoDbException
     */
    public void setConsumerType(String consumerType) throws CamelMongoDbException {
        try {
            this.consumerType = MongoDbConsumerType.valueOf(consumerType);
        } catch (IllegalArgumentException e) {
            throw new CamelMongoDbException("Consumer type not supported", e);
        }
    }

    public MongoDbConsumerType getConsumerType() {
        return consumerType;
    }

    public String getTailTrackDb() {
        return tailTrackDb;
    }

    /**
     * Indicates what database the tail tracking mechanism will persist to. If
     * not specified, the current database will be picked by default. Dynamicity
     * will not be taken into account even if enabled, i.e. the tail tracking
     * database will not vary past endpoint initialisation.
     * 
     * @param tailTrackDb database name
     */
    public void setTailTrackDb(String tailTrackDb) {
        this.tailTrackDb = tailTrackDb;
    }

    public String getTailTrackCollection() {
        return tailTrackCollection;
    }

    /**
     * Collection where tail tracking information will be persisted. If not
     * specified, {@link MongoDbTailTrackingConfig#DEFAULT_COLLECTION} will be
     * used by default.
     * 
     * @param tailTrackCollection collection name
     */
    public void setTailTrackCollection(String tailTrackCollection) {
        this.tailTrackCollection = tailTrackCollection;
    }

    public String getTailTrackField() {
        return tailTrackField;
    }

    /**
     * Field where the last tracked value will be placed. If not specified,
     * {@link MongoDbTailTrackingConfig#DEFAULT_FIELD} will be used by default.
     * 
     * @param tailTrackField field name
     */
    public void setTailTrackField(String tailTrackField) {
        this.tailTrackField = tailTrackField;
    }

    /**
     * Enable persistent tail tracking, which is a mechanism to keep track of
     * the last consumed message across system restarts. The next time the
     * system is up, the endpoint will recover the cursor from the point where
     * it last stopped slurping records.
     * 
     * @param persistentTailTracking true or false
     */
    public void setPersistentTailTracking(boolean persistentTailTracking) {
        this.persistentTailTracking = persistentTailTracking;
    }

    public boolean isPersistentTailTracking() {
        return persistentTailTracking;
    }

    /**
     * Correlation field in the incoming record which is of increasing nature
     * and will be used to position the tailing cursor every time it is
     * generated. The cursor will be (re)created with a query of type:
     * tailTrackIncreasingField > lastValue (possibly recovered from persistent
     * tail tracking). Can be of type Integer, Date, String, etc. NOTE: No
     * support for dot notation at the current time, so the field should be at
     * the top level of the document.
     * 
     * @param tailTrackIncreasingField
     */
    public void setTailTrackIncreasingField(String tailTrackIncreasingField) {
        this.tailTrackIncreasingField = tailTrackIncreasingField;
    }

    public String getTailTrackIncreasingField() {
        return tailTrackIncreasingField;
    }

    public MongoDbTailTrackingConfig getTailTrackingConfig() {
        if (tailTrackingConfig == null) {
            tailTrackingConfig = new MongoDbTailTrackingConfig(persistentTailTracking, tailTrackIncreasingField, tailTrackDb == null ? database : tailTrackDb, tailTrackCollection,
                                                               tailTrackField, getPersistentId());
        }
        return tailTrackingConfig;
    }

    /**
     * MongoDB tailable cursors will block until new data arrives. If no new
     * data is inserted, after some time the cursor will be automatically freed
     * and closed by the MongoDB server. The client is expected to regenerate
     * the cursor if needed. This value specifies the time to wait before
     * attempting to fetch a new cursor, and if the attempt fails, how long
     * before the next attempt is made. Default value is 1000ms.
     * 
     * @param cursorRegenerationDelay delay specified in milliseconds
     */
    public void setCursorRegenerationDelay(long cursorRegenerationDelay) {
        this.cursorRegenerationDelay = cursorRegenerationDelay;
    }

    public long getCursorRegenerationDelay() {
        return cursorRegenerationDelay;
    }

    /**
     * One tail tracking collection can host many trackers for several tailable
     * consumers. To keep them separate, each tracker should have its own unique
     * persistentId.
     * 
     * @param persistentId the value of the persistent ID to use for this
     *            tailable consumer
     */
    public void setPersistentId(String persistentId) {
        this.persistentId = persistentId;
    }

    public String getPersistentId() {
        return persistentId;
    }

    public boolean isWriteResultAsHeader() {
        return writeResultAsHeader;
    }

    /**
     * In write operations, it determines whether instead of returning
     * {@link WriteResult} as the body of the OUT message, we transfer the IN
     * message to the OUT and attach the WriteResult as a header.
     * 
     * @param writeResultAsHeader flag to indicate if this option is enabled
     */
    public void setWriteResultAsHeader(boolean writeResultAsHeader) {
        this.writeResultAsHeader = writeResultAsHeader;
    }

    public MongoDbOutputType getOutputType() {
        return outputType;
    }

    /**
     * Convert the output of the producer to the selected type : "DocumentList",
     * "Document" or "MongoIterable". DocumentList or Document applies to
     * findAll. MongoIterable applies to all other operations.
     * 
     * @param outputType
     */
    public void setOutputType(MongoDbOutputType outputType) {
        this.outputType = outputType;
    }

    public MongoDatabase getMongoDatabase() {
        return mongoDatabase;
    }

    public MongoCollection<Document> getMongoCollection() {
        return mongoCollection;
    }
}
