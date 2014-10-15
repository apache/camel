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
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import com.mongodb.gridfs.GridFS;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GridFsEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(GridFsEndpoint.class);

    private Mongo mongoConnection;
    private String database;
    private String colCounters;
    private String colTP;
    private DBCollection dbColCounters;
    private DBCollection dbColTP;
    private DB db;
    private GridFS gridFs;

    public GridFsEndpoint() { }

    public GridFsEndpoint(String uri, GridFsComponent component) {
        super(uri, component);
    }

    public GridFsEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        initializeConnection();
        return new GridFsProducer(this);
    }

    public Consumer createConsumer(Processor processor) {
        return null;
    }

    public boolean isSingleton() {
        return true;
    }

    public void initializeConnection() throws Exception {
        LOG.info("Initialize GridFS endpoint: {}", this.toString());
        if (database == null || colCounters == null || colTP == null) {
            throw new IllegalStateException("Missing required endpoint configuration: database and/or colCounters and/or colTP");
        }
        db = mongoConnection.getDB(database);
        if (db == null) {
            throw new IllegalStateException("Could not initialize GridFsComponent. Database " + database + " does not exist.");
        }
        dbColCounters = db.getCollection(colCounters);
        dbColTP = db.getCollection(colTP);
        gridFs = new GridFS(db);
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

    public String getColCounters() {
        return colCounters;
    }

    public void setColCounters(String colCounters) {
        this.colCounters = colCounters;
    }

    public String getColTP() {
        return colTP;
    }

    public void setColTP(String colTP) {
        this.colTP = colTP;
    }

    public DBCollection getDbColCounters() {
        return dbColCounters;
    }

    public void setDbColCounters(DBCollection dbColCounters) {
        this.dbColCounters = dbColCounters;
    }

    public DBCollection getDbColTP() {
        return dbColTP;
    }

    public void setDbColTP(DBCollection dbColTP) {
        this.dbColTP = dbColTP;
    }

    public DB getDb() {
        return db;
    }

    public void setDb(DB db) {
        this.db = db;
    }

    public GridFS getGridFs() {
        return gridFs;
    }

    public void setGridFs(GridFS gridFs) {
        this.gridFs = gridFs;
    }
}
