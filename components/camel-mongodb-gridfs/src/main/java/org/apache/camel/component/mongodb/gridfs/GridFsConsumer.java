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
import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.camel.util.IOHelper;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.GRIDFS_FILE_ATTRIBUTE_DONE;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.GRIDFS_FILE_ATTRIBUTE_PROCESSING;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.GRIDFS_FILE_KEY_CONTENT_TYPE;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.GRIDFS_FILE_KEY_UPLOAD_DATE;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.PERSISTENT_TIMESTAMP_KEY;

public class GridFsConsumer extends DefaultConsumer implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GridFsConsumer.class);

    private final GridFsEndpoint endpoint;
    private volatile ScheduledExecutorService taskExecutor;
    private volatile ExecutorService executor;
    // the polls ask for the files uploaded after this date. With the TimeStamp and PersistentTimestamp strategies it
    // is the upload date of the newest file that was routed, kept between the polls. The strategies with a file
    // attribute keep the start date, because the attribute already makes sure that a file is routed once, and a file
    // whose upload date is older than one that was routed already (another uploader, clock skew) is still found.
    // Two threads use it: run() sets the start value on the thread of the executor, before it schedules the polls;
    // the polls then read and move it on the thread of the taskExecutor.
    private volatile Date fromDate;
    // the collection and the document of the persistent timestamp, read by the first poll that can reach MongoDB.
    // run() resets them before it schedules the polls, the polls then use them on the thread of the taskExecutor
    private volatile MongoCollection<Document> ptsCollection;
    private volatile Document persistentTimestamp;
    // the number of polls in a row that failed, so that only the first failure of an outage is reported
    private volatile int consecutiveFailures;
    // the ids of the files whose exchange failed in the last poll: when such a file fails again in the next poll, the
    // failure is logged at DEBUG instead of being reported again, so a file that fails at every poll is reported once,
    // as for the failures of the polls. It holds the files of one poll only, so it does not grow with the bucket
    private volatile Set<BsonValue> lastFailedFiles = Set.of();

    public GridFsConsumer(GridFsEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (taskExecutor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(taskExecutor);
            taskExecutor = null;
        }
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        taskExecutor = endpoint.getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, "GridFsPollingTask");
        executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(),
                1);
        executor.execute(this);
    }

    @Override
    public void run() {
        fromDate = null;
        // the start of the consumer: the first value of the timestamp, also of a persistent timestamp that the first
        // poll that reaches MongoDB creates
        Date startDate = new Date();

        QueryStrategy queryStrategy = endpoint.getQueryStrategy();
        boolean usesTimestamp = queryStrategy != QueryStrategy.FileAttribute;
        boolean persistsTimestamp = queryStrategy == QueryStrategy.PersistentTimestamp
                || queryStrategy == QueryStrategy.PersistentTimestampAndFileAttribute;
        boolean usesAttribute = queryStrategy == QueryStrategy.FileAttribute
                || queryStrategy == QueryStrategy.TimeStampAndFileAttribute
                || queryStrategy == QueryStrategy.PersistentTimestampAndFileAttribute;

        ptsCollection = null;
        persistentTimestamp = null;
        consecutiveFailures = 0;
        lastFailedFiles = Set.of();
        if (usesTimestamp && !persistsTimestamp) {
            fromDate = startDate;
        }

        BlockingTask task = Tasks.backgroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        // maxIterations defaults to Integer.MAX_VALUE (effectively unlimited)
                        .withInterval(Duration.ofMillis(endpoint.getDelay()))
                        .withInitialDelay(Duration.ofMillis(endpoint.getInitialDelay()))
                        .withUnlimitedDuration()
                        .build())
                .withScheduledExecutor(taskExecutor)
                .withName("GridFsPolling")
                .build();

        task.run(endpoint.getCamelContext(), () -> poll(usesTimestamp, persistsTimestamp, usesAttribute, startDate));
    }

    /**
     * One poll. A failure, such as MongoDB that cannot be reached, is reported and the next poll tries again: an
     * exception thrown out of the repeated task would end it, and the consumer would stay started without polling.
     */
    private boolean poll(boolean usesTimestamp, boolean persistsTimestamp, boolean usesAttribute, Date startDate) {
        try {
            if (persistsTimestamp && persistentTimestamp == null) {
                readPersistentTimestamp(startDate);
            }
            boolean answer = processCollection(usesTimestamp, persistsTimestamp, usesAttribute, ptsCollection,
                    persistentTimestamp);
            if (consecutiveFailures > 0) {
                LOG.info("Polling {} works again after {} failed polls", endpoint, consecutiveFailures);
                consecutiveFailures = 0;
            }
            return answer;
        } catch (Exception e) {
            if (!isRunAllowed()) {
                // the consumer is stopping (for example the MongoClient is closed during shutdown): no poll follows
                LOG.debug("Error polling {} while the consumer is stopping: {}", endpoint, e.toString());
                return false;
            }
            consecutiveFailures++;
            if (consecutiveFailures == 1) {
                getExceptionHandler().handleException("Error polling " + endpoint + " (will try again on the next poll)", e);
            } else {
                // the failure of the outage is reported already, and the polls run every delay (500 ms by default)
                LOG.debug("Error polling {} ({} polls in a row failed): {}", endpoint, consecutiveFailures, e.getMessage());
            }
            return false;
        }
    }

    private void readPersistentTimestamp(Date startDate) {
        MongoCollection<Document> collection = endpoint.getDB().getCollection(endpoint.getPersistentTSCollection());

        // ensure standard indexes as long as collections are small
        if (collection.countDocuments() < 1000) {
            collection.createIndex(new BasicDBObject("id", 1));
        }

        Document timestamp = collection.find(eq("id", endpoint.getPersistentTSObject())).first();
        if (timestamp == null) {
            timestamp = new Document("id", endpoint.getPersistentTSObject());
            timestamp.put(PERSISTENT_TIMESTAMP_KEY, startDate);
            collection.insertOne(timestamp);
        }
        fromDate = timestamp.get(PERSISTENT_TIMESTAMP_KEY, Date.class);
        ptsCollection = collection;
        persistentTimestamp = timestamp;
    }

    private boolean processCollection(
            boolean usesTimestamp, boolean persistsTimestamp, boolean usesAttribute,
            final MongoCollection<Document> ptsCollection, final Document persistentTimestamp) {

        if (!isStarted()) {
            return false;
        }

        // without a file attribute, the timestamp is the only record of the files that were routed: the files are
        // routed in the order of their upload date, and the poll stops at a file whose exchange failed, so that the
        // timestamp stays before that file and the next poll offers it again (and the files after it)
        boolean stopsAtFailure = usesTimestamp && !usesAttribute;
        Date pollStartDate = fromDate;
        Date pollFromDate = pollStartDate;
        // the upload date of the oldest file of this poll whose exchange failed
        Date failedDate = null;
        // the ids of the files of this poll whose exchange failed
        Set<BsonValue> failedFiles = new HashSet<>();
        try (MongoCursor<GridFSFile> cursor
                = getGridFSFileMongoCursor(pollStartDate, usesTimestamp, usesAttribute, stopsAtFailure)) {

            while (cursor.hasNext() && isStarted()) {
                GridFSFile file = cursor.next();
                GridFSFile fOrig = file;
                if (usesAttribute) {
                    FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
                    options.returnDocument(ReturnDocument.AFTER);
                    Bson filter = Filters.and(eq("_id", file.getId()), eq(endpoint.getFileAttributeName(), null));
                    Bson update = Updates.set(endpoint.getFileAttributeName(), GRIDFS_FILE_ATTRIBUTE_PROCESSING);
                    fOrig = endpoint.getFilesCollection().findOneAndUpdate(filter, update, options);
                }
                if (fOrig != null) {
                    // by id: several files can have the same name (revisions), and by name GridFS opens the newest
                    GridFSDownloadStream downloadStream = endpoint.getGridFsBucket().openDownloadStream(file.getId());
                    file = downloadStream.getGridFSFile();

                    if (processFile(file, downloadStream, lastFailedFiles.contains(file.getId()))) {
                        if (usesAttribute) {
                            Bson update = Updates.set(endpoint.getFileAttributeName(), GRIDFS_FILE_ATTRIBUTE_DONE);
                            endpoint.getFilesCollection().findOneAndUpdate(eq("_id", fOrig.getId()), update);
                        }
                        if (usesTimestamp && file.getUploadDate().compareTo(pollFromDate) > 0) {
                            pollFromDate = file.getUploadDate();
                            if (!usesAttribute) {
                                fromDate = pollFromDate;
                            }
                        }
                    } else {
                        failedFiles.add(file.getId());
                        // leave the file as it was, so that the next poll offers it again
                        if (usesAttribute) {
                            Bson update = Updates.unset(endpoint.getFileAttributeName());
                            endpoint.getFilesCollection().findOneAndUpdate(eq("_id", fOrig.getId()), update);
                        }
                        if (usesTimestamp && (failedDate == null || file.getUploadDate().before(failedDate))) {
                            failedDate = file.getUploadDate();
                        }
                        if (stopsAtFailure) {
                            break;
                        }
                    }
                }
            }

            if (failedDate != null && !pollFromDate.before(failedDate)) {
                // a file routed in this poll is not older than a file that failed (it has the same upload date or, with
                // a file attribute, it is newer): keep the timestamp before the failed file. With a file attribute the
                // routed files are not routed again; without it, the next poll also offers again the routed files with
                // the same upload date as the failed file
                pollFromDate = new Date(failedDate.getTime() - 1);
                if (!usesAttribute) {
                    fromDate = pollFromDate;
                }
            }
            if (persistsTimestamp && !pollFromDate.equals(pollStartDate)) {
                Bson update = Updates.set(PERSISTENT_TIMESTAMP_KEY, pollFromDate);
                ptsCollection.findOneAndUpdate(eq("_id", persistentTimestamp.getObjectId("_id")), update);
            }
        } finally {
            lastFailedFiles = failedFiles;
        }

        return false;
    }

    /**
     * Routes the exchange of one file. The download stream is closed when the exchange is done. Returns false when the
     * exchange failed: the failure is reported to the exception handler of the consumer, or logged at DEBUG when the
     * file failed in the last poll too, as that failure was reported already.
     */
    private boolean processFile(GridFSFile file, GridFSDownloadStream downloadStream, boolean failedInLastPoll) {
        Exchange exchange = createExchange(false);
        try {
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange exchange) {
                    IOHelper.close(downloadStream);
                }
            });

            Message in = exchange.getIn();
            in.setHeader(GridFsConstants.FILE_NAME, file.getFilename());
            Document metadata = file.getMetadata();
            if (metadata != null) {
                String contentType = metadata.get(GRIDFS_FILE_KEY_CONTENT_TYPE, String.class);
                if (contentType != null) {
                    in.setHeader(GridFsConstants.FILE_CONTENT_TYPE, contentType);
                }
                in.setHeader(GridFsConstants.GRIDFS_METADATA, metadata.toJson());
            }
            in.setHeader(GridFsConstants.FILE_LENGTH, file.getLength());
            in.setHeader(GridFsConstants.FILE_LAST_MODIFIED, file.getUploadDate());
            in.setBody(downloadStream, InputStream.class);

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
                // the exchange may not have reached its unit of work, which runs the on completion
                IOHelper.close(downloadStream);
            }
            Exception cause = exchange.getException();
            if (cause != null) {
                if (failedInLastPoll) {
                    LOG.debug("Error processing the GridFS file {} again, which the next poll offers again: {}",
                            file.getFilename(), cause.getMessage());
                } else {
                    getExceptionHandler().handleException(
                            "Error processing the GridFS file " + file.getFilename() + ", which the next poll offers again",
                            exchange, cause);
                }
                return false;
            }
            return true;
        } finally {
            releaseExchange(exchange, false);
        }
    }

    private MongoCursor<GridFSFile> getGridFSFileMongoCursor(
            Date fromDate, boolean usesTimestamp, boolean usesAttribute, boolean byUploadDate) {
        String queryString = endpoint.getQuery();
        Bson query = getBsonDocument(fromDate, usesTimestamp, usesAttribute, queryString);
        GridFSFindIterable files = endpoint.getGridFsBucket().find(query);
        if (byUploadDate) {
            files = files.sort(Sorts.ascending(GRIDFS_FILE_KEY_UPLOAD_DATE));
        }
        return files.cursor();
    }

    private Bson getBsonDocument(Date fromDate, boolean usesTimestamp, boolean usesAttribute, String queryString) {
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
        return query;
    }
}
