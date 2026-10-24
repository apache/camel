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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExceptionHandler;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The consumer with a timestamp query strategy against a fake bucket that answers the upload date query of each poll
 * the way MongoDB does: a file that was routed must not be routed again by the following polls. With a file attribute,
 * the fake files collection marks a file as the consumer asks, and the bucket no longer returns a marked file.
 */
class GridFsConsumerTimestampTest {

    private static final long TIMEOUT_SECONDS = 20;

    private final List<GridFSFile> stored = new CopyOnWriteArrayList<>();
    private final List<String> routed = new CopyOnWriteArrayList<>();
    // the ids of the files whose content was routed
    private final List<BsonValue> routedIds = new CopyOnWriteArrayList<>();
    private final List<Date> persisted = new CopyOnWriteArrayList<>();
    // the ids of the files that the consumer has marked with its file attribute
    private final Set<BsonValue> marked = ConcurrentHashMap.newKeySet();
    // the number of polls (queries of the bucket) that fail, as when MongoDB cannot be reached
    private final AtomicInteger failingPolls = new AtomicInteger();
    // the number of reads of the persistent timestamp that fail
    private final AtomicInteger failingTimestampReads = new AtomicInteger();
    // called when a read of the persistent timestamp fails
    private volatile Runnable onFailedTimestampRead = () -> {
    };
    // the persistent timestamp documents that the consumer created
    private final List<Date> created = new CopyOnWriteArrayList<>();
    // the failures that the consumer reported through its ExceptionHandler
    private final List<Throwable> reported = new CopyOnWriteArrayList<>();
    // called by a query of the bucket, before the files are returned (or the failure is thrown)
    private volatile Runnable onPoll = () -> {
    };
    private final GridFSBucket bucket = mock(GridFSBucket.class);
    private final MongoDatabase database = mock(MongoDatabase.class);
    @SuppressWarnings("unchecked")
    private final MongoCollection<GridFSFile> files = mock(MongoCollection.class);

    private volatile CountDownLatch polls = new CountDownLatch(0);
    private CamelContext context;
    private GridFsConsumer consumer;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();

        when(bucket.find(any(Bson.class))).thenAnswer(invocation -> {
            onPoll.run();
            if (failingPolls.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                polls.countDown();
                throw new MongoTimeoutException("Timed out while waiting for a server (test)");
            }
            List<GridFSFile> found = filesUploadedAfter(invocation.getArgument(0));
            polls.countDown();
            return iterableOf(found);
        });
        // as MongoDB: by file name, the newest revision of the files with that name; by id, that file
        when(bucket.openDownloadStream(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return downloadStreamOf(stored.stream().filter(f -> f.getFilename().equals(name))
                    .max(Comparator.comparing(GridFSFile::getUploadDate)).orElseThrow());
        });
        when(bucket.openDownloadStream(any(BsonValue.class))).thenAnswer(invocation -> {
            BsonValue id = invocation.getArgument(0);
            return downloadStreamOf(stored.stream().filter(f -> f.getId().equals(id)).findFirst().orElseThrow());
        });
        // the consumer marks a file before it routes it: only a file that is not marked yet is returned
        when(files.findOneAndUpdate(any(Bson.class), any(Bson.class), any(FindOneAndUpdateOptions.class)))
                .thenAnswer(invocation -> {
                    BsonValue id = find(toDocument(invocation.getArgument(0)), "_id");
                    return marked.add(id)
                            ? stored.stream().filter(f -> f.getId().equals(id)).findFirst().orElseThrow() : null;
                });
        MongoCollection<Document> timestamps = timestampCollection();
        when(database.getCollection("camel-timestamps")).thenReturn(timestamps);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.stop();
        }
        context.stop();
    }

    @Test
    void timestampStrategyRoutesAFileOnce() throws Exception {
        startConsumer(QueryStrategy.TimeStamp);

        stored.add(file("first", 60));
        awaitFullPolls(3);

        assertEquals(List.of("first"), routed, "three polls saw the file, it must be routed by the first of them only");
    }

    @Test
    void timestampStrategyRoutesALaterFileOnce() throws Exception {
        startConsumer(QueryStrategy.TimeStamp);

        stored.add(file("first", 60));
        awaitFullPolls(2);
        stored.add(file("second", 120));
        awaitFullPolls(3);

        assertEquals(List.of("first", "second"), routed);
    }

    @Test
    void persistentTimestampStrategyRoutesAFileOnce() throws Exception {
        startConsumer(QueryStrategy.PersistentTimestamp);

        GridFSFile first = file("first", 60);
        stored.add(first);
        awaitFullPolls(3);

        assertEquals(List.of("first"), routed, "three polls saw the file, it must be routed by the first of them only");
        assertEquals(List.of(first.getUploadDate()), persisted, "the timestamp is stored when it moves, not at every poll");
    }

    @ParameterizedTest
    @EnumSource(QueryStrategy.class)
    void filesWithTheSameNameAreEachRoutedWithTheirOwnContent(QueryStrategy strategy) throws Exception {
        startConsumer(strategy);

        // two revisions of a file name, both new for the same poll (uploaded within one delay, or while the
        // consumer was stopped): GridFS keeps both, and each is a file of its own
        GridFSFile first = file("report.csv", 60);
        GridFSFile second = file("report.csv", 61);
        stored.add(first);
        stored.add(second);
        awaitFullPolls(2);

        assertEquals(List.of(first.getId(), second.getId()), routedIds,
                "each file found by the poll must be routed with its own content, not with the newest file of that name");
    }

    @Test
    void timestampStrategySkipsAFileWithAnOlderUploadDateThanARoutedFile() throws Exception {
        startConsumer(QueryStrategy.TimeStamp);

        stored.add(file("newer", 120));
        awaitFullPolls(2);
        // visible only now, but uploaded before the routed file (another uploader, or a client with another clock)
        stored.add(file("older", 60));
        awaitFullPolls(2);

        assertEquals(List.of("newer"), routed, "the strategy only asks for files uploaded after the newest routed file");
    }

    @ParameterizedTest
    @EnumSource(value = QueryStrategy.class, names = { "TimeStampAndFileAttribute", "PersistentTimestampAndFileAttribute" })
    void strategyWithFileAttributeRoutesAFileWithAnOlderUploadDateThanARoutedFile(QueryStrategy strategy)
            throws Exception {
        startConsumer(strategy);

        stored.add(file("newer", 120));
        awaitFullPolls(2);
        // visible only now, but uploaded before the routed file (another uploader, or a client with another clock)
        stored.add(file("older", 60));
        awaitFullPolls(2);

        assertEquals(List.of("newer", "older"), routed,
                "the file attribute makes sure a file is routed once, the query keeps the start date");
    }

    @ParameterizedTest
    @EnumSource(QueryStrategy.class)
    void consumerKeepsPollingAfterAFailedPoll(QueryStrategy strategy) throws Exception {
        failingPolls.set(2);
        startConsumer(strategy);

        // the two failing polls, then one that sees no file
        awaitFullPolls(2);
        stored.add(file("first", 60));
        awaitFullPolls(2);

        assertEquals(List.of("first"), routed, "a failed poll (MongoDB not reachable) must not end the consumer");
    }

    @Test
    void persistentTimestampStrategyKeepsPollingWhenTheTimestampCannotBeReadAtStart() throws Exception {
        failingTimestampReads.set(1);
        startConsumer(QueryStrategy.PersistentTimestamp);

        stored.add(file("first", 60));
        awaitFullPolls(2);

        assertEquals(List.of("first"), routed,
                "the timestamp could not be read when the consumer started, the next poll must read it");
    }

    @ParameterizedTest
    @EnumSource(value = QueryStrategy.class, names = { "PersistentTimestamp", "PersistentTimestampAndFileAttribute" })
    void newPersistentTimestampStartsWhenTheConsumerStartsAlthoughTheFirstPollFails(QueryStrategy strategy)
            throws Exception {
        // the first poll cannot read the timestamp (MongoDB not reachable yet); meanwhile another client uploads a file
        AtomicReference<Date> failedAt = new AtomicReference<>();
        failingTimestampReads.set(1);
        onFailedTimestampRead = () -> {
            failedAt.set(new Date());
            stored.add(new GridFSFile(new BsonObjectId(), "early", 1, 1, new Date(failedAt.get().getTime() + 25), null));
        };
        // the next poll, which creates the timestamp, runs at least 50 ms after the failed one
        startConsumer(strategy, 50);

        awaitFullPolls(2);

        assertEquals(1, created.size());
        assertFalse(created.get(0).after(failedAt.get()),
                "the new timestamp must be the start of the consumer, not the time of the first poll that worked");
        assertEquals(List.of("early"), routed, "a file uploaded after the consumer started must be routed");
    }

    @Test
    void pollFailureWhileTheConsumerStopsIsNotReported() throws Exception {
        AtomicReference<Thread> pollThread = new AtomicReference<>();
        CountDownLatch stopped = new CountDownLatch(1);
        startConsumer(QueryStrategy.TimeStamp);
        awaitFullPolls(1);

        // the consumer is stopped during a poll, which then fails (as when the MongoClient is closed by the shutdown)
        onPoll = () -> {
            if (pollThread.compareAndSet(null, Thread.currentThread())) {
                consumer.stop();
                stopped.countDown();
                throw new IllegalStateException("state should be: open (test)");
            }
        };
        assertTrue(stopped.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the consumer did not poll again");
        // the polling thread ends once the failed poll returns, as its executor is shut down
        pollThread.get().join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertFalse(pollThread.get().isAlive(), "the failed poll did not return");

        assertEquals(List.of(), reported, "no poll follows, the failure must not be reported as one that is retried");
    }

    private void startConsumer(QueryStrategy strategy) {
        startConsumer(strategy, 1);
    }

    private void startConsumer(QueryStrategy strategy, long delay) {
        GridFsComponent component = new GridFsComponent();
        component.setCamelContext(context);
        GridFsEndpoint endpoint = new GridFsEndpoint("mongodb-gridfs:fake", component) {
            @Override
            public GridFSBucket getGridFsBucket() {
                return bucket;
            }

            @Override
            public MongoDatabase getDB() {
                return database;
            }

            @Override
            public MongoCollection<GridFSFile> getFilesCollection() {
                return files;
            }
        };
        endpoint.setCamelContext(context);
        endpoint.setQueryStrategy(strategy);
        endpoint.setInitialDelay(1);
        endpoint.setDelay(delay);

        Processor processor = exchange -> {
            GridFSFile file = exchange.getMessage().getBody(GridFSDownloadStream.class).getGridFSFile();
            routed.add(file.getFilename());
            routedIds.add(file.getId());
        };
        consumer = new GridFsConsumer(endpoint, processor);
        consumer.setExceptionHandler(new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                reported.add(exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                reported.add(exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                reported.add(exception);
            }
        });
        consumer.start();
    }

    /**
     * Waits until the consumer has started that many polls and one more, so that many polls have run to their end: the
     * consumer polls on one thread.
     */
    private void awaitFullPolls(int count) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(count + 1);
        polls = latch;
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the consumer did not poll " + (count + 1) + " times");
    }

    private static GridFSDownloadStream downloadStreamOf(GridFSFile file) {
        GridFSDownloadStream stream = mock(GridFSDownloadStream.class);
        when(stream.getGridFSFile()).thenReturn(file);
        return stream;
    }

    /**
     * A file uploaded the given number of seconds after the test (and so the consumer) started.
     */
    private static GridFSFile file(String name, int secondsFromNow) {
        Date uploadDate = new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(secondsFromNow));
        return new GridFSFile(new BsonObjectId(), name, 1, 1, uploadDate, null);
    }

    /**
     * Evaluates the query of the consumer on the stored files: <tt>{uploadDate: {$gt: date}}</tt> (not with the
     * FileAttribute strategy), and with a file attribute also <tt>{camel-processed: null}</tt>.
     */
    private List<GridFSFile> filesUploadedAfter(Bson query) {
        BsonDocument document = toDocument(query);
        // the FileAttribute strategy asks for the file attribute only
        BsonValue uploadDate = find(document, GridFsConstants.GRIDFS_FILE_KEY_UPLOAD_DATE);
        long after = uploadDate != null ? uploadDate.asDocument().getDateTime("$gt").getValue() : Long.MIN_VALUE;
        boolean notMarkedOnly = find(document, "camel-processed") != null;
        List<GridFSFile> found = new ArrayList<>();
        for (GridFSFile file : stored) {
            if (file.getUploadDate().getTime() > after && !(notMarkedOnly && marked.contains(file.getId()))) {
                found.add(file);
            }
        }
        return found;
    }

    private static BsonDocument toDocument(Bson bson) {
        return bson.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    /**
     * The value of the given key in a filter, or in one of the filters of an <tt>$and</tt>.
     */
    private static BsonValue find(BsonDocument filter, String key) {
        if (filter.containsKey(key)) {
            return filter.get(key);
        }
        BsonArray and = filter.getArray("$and", new BsonArray());
        for (BsonValue part : and) {
            BsonValue value = find(part.asDocument(), key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static GridFSFindIterable iterableOf(List<GridFSFile> files) {
        Iterator<GridFSFile> iterator = files.iterator();
        MongoCursor<GridFSFile> cursor = mock(MongoCursor.class);
        when(cursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        when(cursor.next()).thenAnswer(invocation -> iterator.next());
        GridFSFindIterable iterable = mock(GridFSFindIterable.class);
        when(iterable.cursor()).thenReturn(cursor);
        return iterable;
    }

    /**
     * The collection of the persistent timestamp: empty at first, records the dates the consumer stores in it.
     */
    @SuppressWarnings("unchecked")
    private MongoCollection<Document> timestampCollection() {
        MongoCollection<Document> timestamps = mock(MongoCollection.class);
        when(timestamps.countDocuments()).thenAnswer(invocation -> {
            if (failingTimestampReads.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                onFailedTimestampRead.run();
                throw new MongoTimeoutException("Timed out while waiting for a server (test)");
            }
            return 0L;
        });
        FindIterable<Document> none = mock(FindIterable.class);
        when(timestamps.find(any(Bson.class))).thenReturn(none);
        when(timestamps.insertOne(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            created.add(document.get(GridFsConstants.PERSISTENT_TIMESTAMP_KEY, Date.class));
            return null;
        });
        when(timestamps.findOneAndUpdate(any(Bson.class), any(Bson.class))).thenAnswer(invocation -> {
            Bson update = invocation.getArgument(1);
            BsonDocument document = toDocument(update);
            persisted.add(new Date(
                    document.getDocument("$set").getDateTime(GridFsConstants.PERSISTENT_TIMESTAMP_KEY).getValue()));
            return null;
        });
        return timestamps;
    }
}
