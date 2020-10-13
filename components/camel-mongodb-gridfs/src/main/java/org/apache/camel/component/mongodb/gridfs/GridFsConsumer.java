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

import java.io.InputStream;
import java.util.Date;
import java.util.concurrent.ExecutorService;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.IOHelper;
import org.bson.Document;
import org.bson.conversions.Bson;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.GRIDFS_FILE_ATTRIBUTE_DONE;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.GRIDFS_FILE_ATTRIBUTE_PROCESSING;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.GRIDFS_FILE_KEY_CONTENT_TYPE;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.GRIDFS_FILE_KEY_UPLOAD_DATE;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.PERSISTENT_TIMESTAMP_KEY;

public class GridFsConsumer extends DefaultConsumer implements Runnable {
    private final GridFsEndpoint endpoint;
    private volatile ExecutorService executor;

    public GridFsConsumer(GridFsEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(),
                1);
        executor.execute(this);
    }

    @Override
    public void run() {
        MongoCursor<GridFSFile> cursor = null;
        Date fromDate = null;

        QueryStrategy queryStrategy = endpoint.getQueryStrategy();
        boolean usesTimestamp = queryStrategy != QueryStrategy.FileAttribute;
        boolean persistsTimestamp = queryStrategy == QueryStrategy.PersistentTimestamp
                || queryStrategy == QueryStrategy.PersistentTimestampAndFileAttribute;
        boolean usesAttribute = queryStrategy == QueryStrategy.FileAttribute
                || queryStrategy == QueryStrategy.TimeStampAndFileAttribute
                || queryStrategy == QueryStrategy.PersistentTimestampAndFileAttribute;

        MongoCollection<Document> ptsCollection = null;
        Document persistentTimestamp = null;
        if (persistsTimestamp) {
            ptsCollection = endpoint.getDB().getCollection(endpoint.getPersistentTSCollection());

            // ensure standard indexes as long as collections are small
            if (ptsCollection.countDocuments() < 1000) {
                ptsCollection.createIndex(new BasicDBObject("id", 1));
            }

            persistentTimestamp = ptsCollection.find(eq("id", endpoint.getPersistentTSObject())).first();
            if (persistentTimestamp == null) {
                persistentTimestamp = new Document("id", endpoint.getPersistentTSObject());
                fromDate = new Date();
                persistentTimestamp.put(PERSISTENT_TIMESTAMP_KEY, fromDate);
                ptsCollection.insertOne(persistentTimestamp);
            }
            fromDate = persistentTimestamp.get(PERSISTENT_TIMESTAMP_KEY, Date.class);
        } else if (usesTimestamp) {
            fromDate = new Date();
        }
        try {
            Thread.sleep(endpoint.getInitialDelay());
            while (isStarted()) {
                if (cursor == null) {
                    String queryString = endpoint.getQuery();
                    Bson query = null;
                    if (queryString != null) {
                        query = Document.parse(queryString);
                    }
                    if (usesTimestamp) {
                        Bson uploadDateFilter = Filters.gt(GRIDFS_FILE_KEY_UPLOAD_DATE, fromDate);
                        if (query == null) {
                            query = uploadDateFilter;
                        } else {
                            query = Filters.and(query, uploadDateFilter);
                        }
                    }
                    if (usesAttribute) {
                        Bson fileAttributeNameFilter = Filters.eq(endpoint.getFileAttributeName(), null);
                        if (query == null) {
                            query = fileAttributeNameFilter;
                        } else {
                            query = Filters.and(query, fileAttributeNameFilter);
                        }
                    }
                    cursor = endpoint.getGridFsBucket().find(query).cursor();
                }
                boolean dateModified = false;
                while (cursor.hasNext() && isStarted()) {
                    GridFSFile file = cursor.next();
                    GridFSFile forig = file;
                    if (usesAttribute) {
                        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
                        options.returnDocument(ReturnDocument.AFTER);
                        Bson filter = Filters.and(eq("_id", file.getId()), eq(endpoint.getFileAttributeName(), null));
                        Bson update = Updates.set(endpoint.getFileAttributeName(), GRIDFS_FILE_ATTRIBUTE_PROCESSING);
                        forig = endpoint.getFilesCollection().findOneAndUpdate(filter, update, options);
                    }
                    if (forig != null) {
                        Exchange exchange = endpoint.createExchange();
                        GridFSDownloadStream downloadStream = endpoint.getGridFsBucket().openDownloadStream(file.getFilename());
                        file = downloadStream.getGridFSFile();

                        Document metadata = file.getMetadata();
                        if (metadata != null) {
                            String contentType = metadata.get(GRIDFS_FILE_KEY_CONTENT_TYPE, String.class);
                            if (contentType != null) {
                                exchange.getIn().setHeader(Exchange.FILE_CONTENT_TYPE, contentType);
                            }
                            exchange.getIn().setHeader(GridFsEndpoint.GRIDFS_METADATA, metadata.toJson());
                        }

                        exchange.getIn().setHeader(Exchange.FILE_LENGTH, file.getLength());
                        exchange.getIn().setHeader(Exchange.FILE_LAST_MODIFIED, file.getUploadDate());
                        exchange.getIn().setBody(downloadStream, InputStream.class);
                        try {
                            getProcessor().process(exchange);
                            if (usesAttribute) {
                                Bson update = Updates.set(endpoint.getFileAttributeName(), GRIDFS_FILE_ATTRIBUTE_DONE);
                                endpoint.getFilesCollection().findOneAndUpdate(eq("_id", forig.getId()), update);
                            }
                            if (usesTimestamp) {
                                if (file.getUploadDate().compareTo(fromDate) > 0) {
                                    fromDate = file.getUploadDate();
                                    dateModified = true;
                                }
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }

                if (persistsTimestamp && dateModified) {
                    Bson update = Updates.set(PERSISTENT_TIMESTAMP_KEY, fromDate);
                    ptsCollection.findOneAndUpdate(eq("_id", persistentTimestamp.getObjectId("_id")), update);
                }

                cursor = null;
                Thread.sleep(endpoint.getDelay());
            }
        } catch (Throwable e1) {
            // ignore
        }
        if (cursor != null) {
            IOHelper.close(cursor);
        }
    }
}
