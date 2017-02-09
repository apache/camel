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
package org.apache.camel.component.mongodb.gridfs;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.gridfs.GridFS;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.CamelContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component for working with MongoDB GridFS.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "mongodb-gridfs", title = "MongoDB GridFS", syntax = "mongodb-gridfs:connectionBean", label = "database,nosql")
public class GridFsEndpoint extends DefaultEndpoint {

    public static final String GRIDFS_OPERATION = "gridfs.operation";
    public static final String GRIDFS_METADATA = "gridfs.metadata";
    public static final String GRIDFS_CHUNKSIZE = "gridfs.chunksize";
    public static final String GRIDFS_FILE_ID_PRODUCED = "gridfs.fileid";

    private static final Logger LOG = LoggerFactory.getLogger(GridFsEndpoint.class);

    @UriPath @Metadata(required = "true")
    private String connectionBean;
    @UriParam @Metadata(required = "true")
    private String database;
    @UriParam(defaultValue = GridFS.DEFAULT_BUCKET)
    private String bucket;
    @UriParam(enums = "ACKNOWLEDGED,W1,W2,W3,UNACKNOWLEDGED,JOURNALED,MAJORITY,SAFE")
    private WriteConcern writeConcern;
    @UriParam
    private WriteConcern writeConcernRef;
    @UriParam
    private ReadPreference readPreference;
    
    @UriParam(label = "producer")
    private String operation;

    @UriParam(label = "consumer")
    private String query;
    @UriParam(label = "consumer", defaultValue = "1000")
    private long initialDelay = 1000;
    @UriParam(label = "consumer", defaultValue = "500")
    private long delay = 500;
    
    @UriParam(label = "consumer", defaultValue = "TimeStamp")
    private QueryStrategy queryStrategy = QueryStrategy.TimeStamp;
    @UriParam(label = "consumer", defaultValue = "camel-timestamps")
    private String persistentTSCollection = "camel-timestamps";
    @UriParam(label = "consumer", defaultValue = "camel-timestamp")
    private String persistentTSObject = "camel-timestamp";
    @UriParam(label = "consumer", defaultValue = "camel-processed")
    private String fileAttributeName = "camel-processed";


    private Mongo mongoConnection;
    private DB db;
    private GridFS gridFs;
    private DBCollection filesCollection;

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

    public boolean isSingleton() {
        return true;
    }

    @SuppressWarnings("deprecation")
    public void initializeConnection() throws Exception {
        LOG.info("Initialize GridFS endpoint: {}", this.toString());
        if (database == null) {
            throw new IllegalStateException("Missing required endpoint configuration: database");
        }
        db = mongoConnection.getDB(database);
        if (db == null) {
            throw new IllegalStateException("Could not initialize GridFsComponent. Database " + database + " does not exist.");
        }
        gridFs = new GridFS(db, bucket == null ? GridFS.DEFAULT_BUCKET : bucket) {
            {
                filesCollection = getFilesCollection();
            }
        };
    }

    
    @Override
    protected void doStart() throws Exception {
        if (writeConcern != null && writeConcernRef != null) {
            String msg = "Cannot set both writeConcern and writeConcernRef at the same time. Respective values: " + writeConcern
                    + ", " + writeConcernRef + ". Aborting initialization.";
            throw new IllegalArgumentException(msg);
        }
        mongoConnection = CamelContextHelper.mandatoryLookup(getCamelContext(), connectionBean, MongoClient.class);
        LOG.debug("Resolved the connection with the name {} as {}", connectionBean, mongoConnection);
        setWriteReadOptionsOnConnection();
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
    
    private void setWriteReadOptionsOnConnection() {
        // Set the WriteConcern
        if (writeConcern != null) {
            mongoConnection.setWriteConcern(writeConcern);
        } else if (writeConcernRef != null) {
            mongoConnection.setWriteConcern(writeConcernRef);
        }

        // Set the ReadPreference
        if (readPreference != null) {
            mongoConnection.setReadPreference(readPreference);
        }
    }
    
    
    
    
    // ======= Getters and setters ===============================================
    public String getConnectionBean() {
        return connectionBean;
    }
    /**
     * Name of {@link com.mongodb.Mongo} to use.
     */
    public void setConnectionBean(String connectionBean) {
        this.connectionBean = connectionBean;
    }
    
    public Mongo getMongoConnection() {
        return mongoConnection;
    }
    /**
     * Sets the Mongo instance that represents the backing connection
     * 
     * @param mongoConnection the connection to the database
     */
    public void setMongoConnection(Mongo mongoConnection) {
        this.mongoConnection = mongoConnection;
    }

    public DB getDB() {
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
     * Sets the name of the GridFS bucket within the database.   Default is "fs".
     * 
     * @param database name of the MongoDB database
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
     * Additional query parameters (in JSON) that are used to configure the query used for finding
     * files in the GridFsConsumer
     * @param query
     */
    public void setQuery(String query) {
        this.query = query;
    }
    public long getDelay() {
        return delay;
    }
    /**
     * Sets the delay between polls within the Consumer.  Default is 500ms
     * @param delay
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }
    public long getInitialDelay() {
        return initialDelay;
    }
    /**
     * Sets the initialDelay before the consumer will start polling.  Default is 1000ms
     * @param initialDelay
     */
    public void setInitialDelay(long initialDelay) {
        this.initialDelay = delay;
    }
    
    /**
     * Sets the QueryStrategy that is used for polling for new files.  Default is Timestamp
     * @see QueryStrategy
     * @param s
     */
    public void setQueryStrategy(String s) {
        queryStrategy = QueryStrategy.valueOf(s);
    }
    public QueryStrategy getQueryStrategy() {
        return queryStrategy;
    }
    /**
     * If the QueryType uses a persistent timestamp, this sets the name of the collection within
     * the DB to store the timestamp.
     * @param s
     */
    public void setPersistentTSCollection(String s) {
        persistentTSCollection = s;
    }
    public String getPersistentTSCollection() {
        return persistentTSCollection;
    }
    /**
     * If the QueryType uses a persistent timestamp, this is the ID of the object in the collection
     * to store the timestamp.   
     * @param s
     */
    public void setPersistentTSObject(String id) {
        persistentTSObject = id;
    }
    public String getPersistentTSObject() {
        return persistentTSObject;
    }
    
    /**
     * If the QueryType uses a FileAttribute, this sets the name of the attribute that is used. Default is "camel-processed".
     * @param f
     */
    public void setFileAttributeName(String f) {
        fileAttributeName = f;
    }
    public String getFileAttributeName() {
        return fileAttributeName;
    }   
    
    /**
     * Set the {@link WriteConcern} for write operations on MongoDB using the standard ones.
     * Resolved from the fields of the WriteConcern class by calling the {@link WriteConcern#valueOf(String)} method.
     * 
     * @param writeConcern the standard name of the WriteConcern
     * @see <a href="http://api.mongodb.org/java/current/com/mongodb/WriteConcern.html#valueOf(java.lang.String)">possible options</a>
     */
    public void setWriteConcern(String writeConcern) {
        this.writeConcern = WriteConcern.valueOf(writeConcern);
    }

    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    /**
     * Set the {@link WriteConcern} for write operations on MongoDB, passing in the bean ref to a custom WriteConcern which exists in the Registry.
     * You can also use standard WriteConcerns by passing in their key. See the {@link #setWriteConcern(String) setWriteConcern} method.
     * 
     * @param writeConcernRef the name of the bean in the registry that represents the WriteConcern to use
     */
    public void setWriteConcernRef(String writeConcernRef) {
        WriteConcern wc = this.getCamelContext().getRegistry().lookupByNameAndType(writeConcernRef, WriteConcern.class);
        if (wc == null) {
            String msg = "Camel MongoDB component could not find the WriteConcern in the Registry. Verify that the "
                    + "provided bean name (" + writeConcernRef + ")  is correct. Aborting initialization.";
            throw new IllegalArgumentException(msg);
        }

        this.writeConcernRef = wc;
    }

    public WriteConcern getWriteConcernRef() {
        return writeConcernRef;
    }

    /** 
     * Sets a MongoDB {@link ReadPreference} on the Mongo connection. Read preferences set directly on the connection will be
     * overridden by this setting.
     * <p/>
     * The {@link com.mongodb.ReadPreference#valueOf(String)} utility method is used to resolve the passed {@code readPreference}
     * value. Some examples for the possible values are {@code nearest}, {@code primary} or {@code secondary} etc.
     * 
     * @param readPreference the name of the read preference to set
     */
    public void setReadPreference(String readPreference) {
        this.readPreference = ReadPreference.valueOf(readPreference);
    }

    public ReadPreference getReadPreference() {
        return readPreference;
    }
    
    
    /**
     * Sets the operation this endpoint will execute against GridRS.
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }

    public GridFS getGridFs() {
        return gridFs;
    }

    public void setGridFs(GridFS gridFs) {
        this.gridFs = gridFs;
    }
    public DBCollection getFilesCollection() {
        return filesCollection;
    }

}
