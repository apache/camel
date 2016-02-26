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
package org.apache.camel.component.gridfs;

import com.mongodb.DB;
import com.mongodb.Mongo;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "gridfs", title = "MongoDBGridFS", syntax = "gridfs:connectionBean", 
            label = "database,nosql")
public class GridFsEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(GridFsEndpoint.class);

    @UriPath @Metadata(required = "true")
    private String connectionBean;
    @UriParam
    private String database;
    @UriParam
    private String bucket;
    @UriParam(enums = "ACKNOWLEDGED,W1,W2,W3,UNACKNOWLEDGED,JOURNALED,MAJORITY,SAFE")
    private WriteConcern writeConcern;
    @UriParam
    private WriteConcern writeConcernRef;
    @UriParam
    private ReadPreference readPreference;
    @UriParam
    private String operation;

    
    private Mongo mongoConnection;
    private DB db;
    private GridFS gridFs;

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
        gridFs = new GridFS(db, bucket == null ? GridFS.DEFAULT_BUCKET : bucket);
    }

    
    @Override
    protected void doStart() throws Exception {
        if (writeConcern != null && writeConcernRef != null) {
            String msg = "Cannot set both writeConcern and writeConcernRef at the same time. Respective values: " + writeConcern
                    + ", " + writeConcernRef + ". Aborting initialization.";
            throw new IllegalArgumentException(msg);
        }

        setWriteReadOptionsOnConnection();
        super.doStart();
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
    public void setMongoConnection(Mongo mongoConnection) {
        this.mongoConnection = mongoConnection;
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
}
