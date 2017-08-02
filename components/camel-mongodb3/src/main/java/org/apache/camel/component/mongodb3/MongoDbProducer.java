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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Processor;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
//import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb3.MongoDbConstants.BATCH_SIZE;
import static org.apache.camel.component.mongodb3.MongoDbConstants.COLLECTION;
import static org.apache.camel.component.mongodb3.MongoDbConstants.COLLECTION_INDEX;
import static org.apache.camel.component.mongodb3.MongoDbConstants.CRITERIA;
import static org.apache.camel.component.mongodb3.MongoDbConstants.DATABASE;
import static org.apache.camel.component.mongodb3.MongoDbConstants.FIELDS_PROJECTION;
import static org.apache.camel.component.mongodb3.MongoDbConstants.LIMIT;
import static org.apache.camel.component.mongodb3.MongoDbConstants.MONGO_ID;
import static org.apache.camel.component.mongodb3.MongoDbConstants.MULTIUPDATE;
import static org.apache.camel.component.mongodb3.MongoDbConstants.NUM_TO_SKIP;
import static org.apache.camel.component.mongodb3.MongoDbConstants.OID;
import static org.apache.camel.component.mongodb3.MongoDbConstants.OPERATION_HEADER;
import static org.apache.camel.component.mongodb3.MongoDbConstants.RECORDS_AFFECTED;
import static org.apache.camel.component.mongodb3.MongoDbConstants.RECORDS_MATCHED;
import static org.apache.camel.component.mongodb3.MongoDbConstants.RESULT_PAGE_SIZE;
import static org.apache.camel.component.mongodb3.MongoDbConstants.RESULT_TOTAL_SIZE;
import static org.apache.camel.component.mongodb3.MongoDbConstants.SORT_BY;
import static org.apache.camel.component.mongodb3.MongoDbConstants.UPSERT;
import static org.apache.camel.component.mongodb3.MongoDbConstants.WRITERESULT;

/**
 * The MongoDb producer.
 */
public class MongoDbProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbProducer.class);
    private final Map<MongoDbOperation, Processor> operations = new HashMap<>();
    private MongoDbEndpoint endpoint;

    {
        bind(MongoDbOperation.aggregate, createDoAggregate());
        bind(MongoDbOperation.command, createDoCommand());
        bind(MongoDbOperation.count, createDoCount());
        bind(MongoDbOperation.findDistinct, createDoDistinct());
        bind(MongoDbOperation.findAll, createDoFindAll());
        bind(MongoDbOperation.findById, createDoFindById());
        bind(MongoDbOperation.findOneByQuery, createDoFindOneByQuery());
        bind(MongoDbOperation.getColStats, createDoGetColStats());
        bind(MongoDbOperation.getDbStats, createDoGetDbStats());
        bind(MongoDbOperation.insert, createDoInsert());
        bind(MongoDbOperation.remove, createDoRemove());
        bind(MongoDbOperation.save, createDoSave());
        bind(MongoDbOperation.update, createDoUpdate());
    }

    public MongoDbProducer(MongoDbEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        MongoDbOperation operation = endpoint.getOperation();
        Object header = exchange.getIn().getHeader(OPERATION_HEADER);
        if (header != null) {
            LOG.debug("Overriding default operation with operation specified on header: {}", header);
            try {
                if (header instanceof MongoDbOperation) {
                    operation = ObjectHelper.cast(MongoDbOperation.class, header);
                } else {
                    // evaluate as a String
                    operation = MongoDbOperation.valueOf(exchange.getIn().getHeader(OPERATION_HEADER, String.class));
                }
            } catch (Exception e) {
                throw new CamelMongoDbException("Operation specified on header is not supported. Value: " + header, e);
            }
        }

        try {
            invokeOperation(operation, exchange);
        } catch (Exception e) {
            throw MongoDbComponent.wrapInCamelMongoDbException(e);
        }

    }

    /**
     * Entry method that selects the appropriate MongoDB operation and executes it
     */
    protected void invokeOperation(MongoDbOperation operation, Exchange exchange) throws Exception {
        Processor processor = operations.get(operation);
        if (processor != null) {
            processor.process(exchange);
        } else {
            throw new CamelMongoDbException("Operation not supported. Value: " + operation);
        }
    }

    private MongoDbProducer bind(MongoDbOperation operation, Function<Exchange, Object> mongoDbFunction) {
        operations.put(operation, wrap(mongoDbFunction, operation));
        return this;
    }

    // ----------- MongoDB operations ----------------

    private Document createDbStatsCommand() {
        return new Document("dbStats", 1).append("scale", 1);
    }

    private Document createCollStatsCommand(String collectionName) {
        return new Document("collStats", collectionName);
    }

    // --------- Convenience methods -----------------------
    private MongoDatabase calculateDb(Exchange exchange) {
        // dynamic calculation is an option. In most cases it won't be used and
        // we should not penalise all users with running this
        // resolution logic on every Exchange if they won't be using this
        // functionality at all
        if (!endpoint.isDynamicity()) {
            return endpoint.getMongoDatabase();
        }

        String dynamicDB = exchange.getIn().getHeader(DATABASE, String.class);
        MongoDatabase db;

        if (dynamicDB == null) {
            db = endpoint.getMongoDatabase();
        } else {
            db = endpoint.getMongoConnection().getDatabase(dynamicDB);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Dynamic database selected: {}", db.getName());
        }
        return db;
    }

    private String calculateCollectionName(Exchange exchange) {
        if (!endpoint.isDynamicity()) {
            return endpoint.getCollection();
        }
        String dynamicCollection = exchange.getIn().getHeader(COLLECTION, String.class);
        if (dynamicCollection == null) {
            return endpoint.getCollection();
        }
        return dynamicCollection;
    }

    private MongoCollection<Document> calculateCollection(Exchange exchange) {
        // dynamic calculation is an option. In most cases it won't be used and
        // we should not penalise all users with running this
        // resolution logic on every Exchange if they won't be using this
        // functionality at all
        if (!endpoint.isDynamicity()) {
            return endpoint.getMongoCollection().withWriteConcern(endpoint.getWriteConcern());
        }

        String dynamicDB = exchange.getIn().getHeader(DATABASE, String.class);
        String dynamicCollection = exchange.getIn().getHeader(COLLECTION, String.class);

        @SuppressWarnings("unchecked")
        List<Bson> dynamicIndex = exchange.getIn().getHeader(COLLECTION_INDEX, List.class);

        MongoCollection<Document> dbCol;

        if (dynamicDB == null && dynamicCollection == null) {
            dbCol = endpoint.getMongoCollection().withWriteConcern(endpoint.getWriteConcern());
        } else {
            MongoDatabase db = calculateDb(exchange);

            if (dynamicCollection == null) {
                dbCol = db.getCollection(endpoint.getCollection(), Document.class);
            } else {
                dbCol = db.getCollection(dynamicCollection, Document.class);

                // on the fly add index
                if (dynamicIndex == null) {
                    endpoint.ensureIndex(dbCol, endpoint.createIndex());
                } else {
                    endpoint.ensureIndex(dbCol, dynamicIndex);
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Dynamic database and/or collection selected: {}->{}", endpoint.getDatabase(), endpoint.getCollection());
        }
        return dbCol;
    }

    @SuppressWarnings("rawtypes")
    private List<Document> attemptConvertToList(List insertList, Exchange exchange) throws CamelMongoDbException {
        List<Document> documentList = new ArrayList<>(insertList.size());
        TypeConverter converter = exchange.getContext().getTypeConverter();
        for (Object item : insertList) {
            try {
                Document document = converter.mandatoryConvertTo(Document.class, item);
                documentList.add(document);
            } catch (Exception e) {
                throw new CamelMongoDbException("MongoDB operation = insert, Assuming List variant of MongoDB insert operation, but List contains non-Document items", e);
            }
        }
        return documentList;
    }

    private boolean isWriteOperation(MongoDbOperation operation) {
        return MongoDbComponent.WRITE_OPERATIONS.contains(operation);
    }

    private Processor wrap(Function<Exchange, Object> supplier, MongoDbOperation operation) {
        return exchange -> {
            Object result = supplier.apply(exchange);
            copyHeaders(exchange);
            moveBodyToOutIfResultIsReturnedAsHeader(exchange, operation);
            processAndTransferResult(result, exchange, operation);
        };
    }

    private void copyHeaders(Exchange exchange) {
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), false);
    }

    private void moveBodyToOutIfResultIsReturnedAsHeader(Exchange exchange, MongoDbOperation operation) {
        if (isWriteOperation(operation) && endpoint.isWriteResultAsHeader()) {
            exchange.getOut().setBody(exchange.getIn().getBody());
        }
    }

    private void processAndTransferResult(Object result, Exchange exchange, MongoDbOperation operation) {
        // determine where to set the WriteResult: as the OUT body or as an IN
        // message header
        if (isWriteOperation(operation) && endpoint.isWriteResultAsHeader()) {
            exchange.getOut().setHeader(WRITERESULT, result);
        } else {
            exchange.getOut().setBody(result);
        }
    }

    private Function<Exchange, Object> createDoGetColStats() {
        return exch -> calculateDb(exch).runCommand(createCollStatsCommand(calculateCollectionName(exch)));
    }

    private Function<Exchange, Object> createDoFindOneByQuery() {
        return exchange -> {
            try {
                MongoCollection<Document> dbCol = calculateCollection(exchange);

                Bson query = exchange.getIn().getHeader(CRITERIA, Bson.class);
                if (null == query) {
                    query = exchange.getIn().getMandatoryBody(Bson.class);
                }

                Bson sortBy = exchange.getIn().getHeader(SORT_BY, Bson.class);
                Bson fieldFilter = exchange.getIn().getHeader(FIELDS_PROJECTION, Bson.class);

                if (fieldFilter == null) {
                    fieldFilter = new Document();
                }

                if (sortBy == null) {
                    sortBy = new Document();
                }

                Document ret = dbCol.find(query).projection(fieldFilter).sort(sortBy).first();
                exchange.getOut().setHeader(RESULT_TOTAL_SIZE, ret == null ? 0 : 1);
                return ret;
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Payload is no Document", e);
            }
        };
    }

    private Function<Exchange, Object> createDoCount() {
        return exchange -> {
            Bson query = exchange.getIn().getHeader(CRITERIA, Bson.class);
            if (query == null) {
                query = exchange.getContext().getTypeConverter().tryConvertTo(Bson.class, exchange, exchange.getIn().getBody());
            }
            if (query == null) {
                query = new Document();
            }
            return calculateCollection(exchange).count(query);
        };
    }

    private Function<Exchange, Object> createDoDistinct() {
        return exchange -> {
            Iterable<String> result = new ArrayList<>();
            MongoCollection<Document> dbCol = calculateCollection(exchange);

            // get the parameters out of the Exchange Header
            String distinctFieldName = exchange.getIn().getHeader(MongoDbConstants.DISTINCT_QUERY_FIELD, String.class);
            Bson query = exchange.getContext().getTypeConverter().tryConvertTo(Bson.class, exchange, exchange.getIn().getBody());
            DistinctIterable<String> ret;
            if (query != null) {
                ret = dbCol.distinct(distinctFieldName, query, String.class);
            } else {
                ret = dbCol.distinct(distinctFieldName, String.class);
            }

            try {
                ret.iterator().forEachRemaining(((List<String>) result)::add);
                exchange.getOut().setHeader(MongoDbConstants.RESULT_PAGE_SIZE, ((List<String>) result).size());
            } finally {
                ret.iterator().close();
            }
            return result;
        };
    }

    private Function<Exchange, Object> createDoFindAll() {
        return exchange -> {
            Iterable<Document> result;
            MongoCollection<Document> dbCol = calculateCollection(exchange);
            // do not use getMandatoryBody, because if the body is empty we want
            // to retrieve all objects in the collection
            Bson query = exchange.getIn().getHeader(CRITERIA, Bson.class);
            // do not run around looking for a type converter unless there is a
            // need for it
            if (query == null && exchange.getIn().getBody() != null) {
                query = exchange.getContext().getTypeConverter().tryConvertTo(Bson.class, exchange, exchange.getIn().getBody());
            }
            Bson fieldFilter = exchange.getIn().getHeader(FIELDS_PROJECTION, Bson.class);

            // get the batch size and number to skip
            Integer batchSize = exchange.getIn().getHeader(BATCH_SIZE, Integer.class);
            Integer numToSkip = exchange.getIn().getHeader(NUM_TO_SKIP, Integer.class);
            Integer limit = exchange.getIn().getHeader(LIMIT, Integer.class);
            Document sortBy = exchange.getIn().getHeader(SORT_BY, Document.class);
            FindIterable<Document> ret;
            if (query == null && fieldFilter == null) {
                ret = dbCol.find(new Document());
            } else if (fieldFilter == null) {
                ret = dbCol.find(query);
            } else if (query != null) {
                ret = dbCol.find(query).projection(fieldFilter);
            } else {
                ret = dbCol.find(new Document()).projection(fieldFilter);
            }

            if (sortBy != null) {
                ret.sort(sortBy);
            }

            if (batchSize != null) {
                ret.batchSize(batchSize);
            }

            if (numToSkip != null) {
                ret.skip(numToSkip);
            }

            if (limit != null) {
                ret.limit(limit);
            }

            if (!MongoDbOutputType.MongoIterable.equals(endpoint.getOutputType())) {
                try {
                    result = new ArrayList<>();
                    ret.iterator().forEachRemaining(((List<Document>)result)::add);
                    exchange.getOut().setHeader(RESULT_PAGE_SIZE, ((List<Document>)result).size());
                } finally {
                    ret.iterator().close();
                }
            } else {
                result = ret;
            }
            return result;
        };
    }

    private Function<Exchange, Object> createDoInsert() {
        return exchange -> {
            MongoCollection<Document> dbCol = calculateCollection(exchange);
            boolean singleInsert = true;
            Object insert = exchange.getContext().getTypeConverter().tryConvertTo(Document.class, exchange, exchange.getIn().getBody());
            // body could not be converted to Document, check to see if it's of
            // type List<Document>
            if (insert == null) {
                insert = exchange.getIn().getBody(List.class);
                // if the body of type List was obtained, ensure that all items
                // are of type Document and cast the List to List<Document>
                if (insert != null) {
                    singleInsert = false;
                    insert = attemptConvertToList((List<?>)insert, exchange);
                } else {
                    throw new CamelMongoDbException("MongoDB operation = insert, Body is not conversible to type Document nor List<Document>");
                }
            }

            if (singleInsert) {
                Document insertObject = Document.class.cast(insert);
                dbCol.insertOne(insertObject);

                exchange.getIn().setHeader(OID, insertObject.get(MONGO_ID));
            } else {
                @SuppressWarnings("unchecked")
                List<Document> insertObjects = (List<Document>)insert;
                dbCol.insertMany(insertObjects);
                List<Object> objectIdentification = new ArrayList<>(insertObjects.size());
                objectIdentification.addAll(insertObjects.stream().map(insertObject -> insertObject.get(MONGO_ID)).collect(Collectors.toList()));
                exchange.getIn().setHeader(OID, objectIdentification);
            }
            return insert;
        };
    }

    private Function<Exchange, Object> createDoUpdate() {
        return exchange -> {
            try {
                MongoCollection<Document> dbCol = calculateCollection(exchange);

                Bson updateCriteria = exchange.getIn().getHeader(CRITERIA, Bson.class);
                Bson objNew;
                if (null == updateCriteria) {
                    @SuppressWarnings("unchecked")
                    List<Bson> saveObj = exchange.getIn().getMandatoryBody((Class<List<Bson>>)Class.class.cast(List.class));
                    if (saveObj.size() != 2) {
                        throw new CamelMongoDbException("MongoDB operation = insert, failed because body is not a List of Document objects with size = 2");
                    }

                    updateCriteria = saveObj.get(0);
                    objNew = saveObj.get(1);
                } else {
                    objNew = exchange.getIn().getMandatoryBody(Bson.class);
                }

                Boolean multi = exchange.getIn().getHeader(MULTIUPDATE, Boolean.class);
                Boolean upsert = exchange.getIn().getHeader(UPSERT, Boolean.class);

                UpdateResult result;
                UpdateOptions options = new UpdateOptions();
                if (upsert != null) {
                    options.upsert(upsert);
                }

                if (multi == null || !multi) {
                    result = dbCol.updateOne(updateCriteria, objNew, options);
                } else {
                    result = dbCol.updateMany(updateCriteria, objNew, options);
                }
                if (result.isModifiedCountAvailable()) {
                    exchange.getOut().setHeader(RECORDS_AFFECTED, result.getModifiedCount());
                }
                exchange.getOut().setHeader(RECORDS_MATCHED, result.getMatchedCount());
                return result;
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Invalid payload for update", e);
            }
        };
    }

    private Function<Exchange, Object> createDoRemove() {
        return exchange -> {
            try {
                MongoCollection<Document> dbCol = calculateCollection(exchange);
                Document removeObj = exchange.getIn().getMandatoryBody(Document.class);

                DeleteResult result = dbCol.deleteMany(removeObj);
                if (result.wasAcknowledged()) {
                    exchange.getOut().setHeader(RECORDS_AFFECTED, result.getDeletedCount());
                }
                return result;
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Invalid payload for remove", e);
            }
        };
    }

    private Function<Exchange, Object> createDoAggregate() {
        return exchange -> {
            try {
                MongoCollection<Document> dbCol = calculateCollection(exchange);

                // Impossible with java driver to get the batch size and number
                // to skip
                List<Document> dbIterator = new ArrayList<>();
                AggregateIterable<Document> aggregationResult;

                @SuppressWarnings("unchecked")
                List<Bson> query = exchange.getIn().getMandatoryBody((Class<List<Bson>>)Class.class.cast(List.class));

                // Allow body to be a pipeline
                // @see http://docs.mongodb.org/manual/core/aggregation/
                if (query != null) {
                    List<Bson> queryList = query.stream().map(o -> (Bson)o).collect(Collectors.toList());
                    aggregationResult = dbCol.aggregate(queryList);
                } else {
                    List<Bson> queryList = new ArrayList<>();
                    queryList.add(Bson.class.cast(exchange.getIn().getMandatoryBody(Bson.class)));
                    aggregationResult = dbCol.aggregate(queryList);
                }
                aggregationResult.iterator().forEachRemaining(dbIterator::add);
                return dbIterator;
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Invalid payload for aggregate", e);
            }
        };
    }

    private Function<Exchange, Object> createDoCommand() {
        return exchange -> {
            try {
                MongoDatabase db = calculateDb(exchange);
                Document cmdObj = exchange.getIn().getMandatoryBody(Document.class);
                return db.runCommand(cmdObj);
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Invalid payload for command", e);
            }
        };
    }

    private Function<Exchange, Object> createDoGetDbStats() {
        return exchange1 -> calculateDb(exchange1).runCommand(createDbStatsCommand());
    }

    private Function<Exchange, Object> createDoFindById() {
        return exchange -> {
            try {
                MongoCollection<Document> dbCol = calculateCollection(exchange);
                Object id = exchange.getIn().getMandatoryBody();
                Bson o = Filters.eq(MONGO_ID, id);
                Document ret;

                Bson fieldFilter = exchange.getIn().getHeader(FIELDS_PROJECTION, Bson.class);
                if (fieldFilter == null) {
                    fieldFilter = new Document();
                }
                ret = dbCol.find(o).projection(fieldFilter).first();
                exchange.getOut().setHeader(RESULT_TOTAL_SIZE, ret == null ? 0 : 1);
                return ret;
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Invalid payload for findById", e);
            }
        };
    }

    private Function<Exchange, Object> createDoSave() {
        return exchange -> {
            try {
                MongoCollection<Document> dbCol = calculateCollection(exchange);
                Document saveObj = exchange.getIn().getMandatoryBody(Document.class);
                UpdateOptions options = new UpdateOptions().upsert(true);
                UpdateResult result;
                if (null == saveObj.get(MONGO_ID)) {
                    result = dbCol.replaceOne(Filters.where("false"), saveObj, options);
                    exchange.getIn().setHeader(OID, result.getUpsertedId().asObjectId().getValue());
                } else {
                    result = dbCol.replaceOne(eq(MONGO_ID, saveObj.get(MONGO_ID)), saveObj, options);
                    exchange.getIn().setHeader(OID, saveObj.get(MONGO_ID));
                }
                return result;
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Body incorrect type for save", e);
            }
        };
    }
}
