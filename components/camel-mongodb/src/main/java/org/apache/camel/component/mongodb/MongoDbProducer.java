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
package org.apache.camel.component.mongodb;

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
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
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
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        Object header = exchange.getIn().getHeader(MongoDbConstants.OPERATION_HEADER);
        if (header != null) {
            LOG.debug("Overriding default operation with operation specified on header: {}", header);
            try {
                if (header instanceof MongoDbOperation) {
                    operation = ObjectHelper.cast(MongoDbOperation.class, header);
                } else {
                    // evaluate as a String
                    operation = MongoDbOperation.valueOf(exchange.getIn().getHeader(MongoDbConstants.OPERATION_HEADER, String.class));
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
     *
     * @param operation
     * @param exchange
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

    private BasicDBObject createDbStatsCommand() {
        return new BasicDBObject("dbStats", 1).append("scale", 1);
    }

    private BasicDBObject createCollStatsCommand(String collectionName) {
        return new BasicDBObject("collStats", collectionName);
    }


    // --------- Convenience methods -----------------------
    private MongoDatabase calculateDb(Exchange exchange) {
        // dynamic calculation is an option. In most cases it won't be used and we should not penalise all users with running this
        // resolution logic on every Exchange if they won't be using this functionality at all
        if (!endpoint.isDynamicity()) {
            return endpoint.getMongoDatabase();
        }

        String dynamicDB = exchange.getIn().getHeader(MongoDbConstants.DATABASE, String.class);
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
        String dynamicCollection = exchange.getIn().getHeader(MongoDbConstants.COLLECTION, String.class);
        if (dynamicCollection == null) {
            return endpoint.getCollection();
        }
        return dynamicCollection;
    }

    private MongoCollection<BasicDBObject> calculateCollection(Exchange exchange) {
        // dynamic calculation is an option. In most cases it won't be used and we should not penalise all users with running this
        // resolution logic on every Exchange if they won't be using this functionality at all
        if (!endpoint.isDynamicity()) {
            return endpoint.getMongoCollection()
                    .withWriteConcern(endpoint.getWriteConcern());
        }

        String dynamicDB = exchange.getIn().getHeader(MongoDbConstants.DATABASE, String.class);
        String dynamicCollection = exchange.getIn().getHeader(MongoDbConstants.COLLECTION, String.class);

        @SuppressWarnings("unchecked")
        List<BasicDBObject> dynamicIndex = exchange.getIn().getHeader(MongoDbConstants.COLLECTION_INDEX, List.class);

        MongoCollection<BasicDBObject> dbCol;

        if (dynamicDB == null && dynamicCollection == null) {
            dbCol = endpoint.getMongoCollection()
                    .withWriteConcern(endpoint.getWriteConcern());
        } else {
            MongoDatabase db = calculateDb(exchange);

            if (dynamicCollection == null) {
                dbCol = db.getCollection(endpoint.getCollection(), BasicDBObject.class);
            } else {
                dbCol = db.getCollection(dynamicCollection, BasicDBObject.class);

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
    private List<DBObject> attemptConvertToList(List insertList, Exchange exchange) throws CamelMongoDbException {
        List<DBObject> dbObjectList = new ArrayList<DBObject>(insertList.size());
        TypeConverter converter = exchange.getContext().getTypeConverter();
        for (Object item : insertList) {
            try {
                DBObject dbObject = converter.mandatoryConvertTo(DBObject.class, item);
                dbObjectList.add(dbObject);
            } catch (Exception e) {
                throw new CamelMongoDbException("MongoDB operation = insert, Assuming List variant of MongoDB insert operation, but List contains non-DBObject items", e);
            }
        }
        return dbObjectList;
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
        // determine where to set the WriteResult: as the OUT body or as an IN message header
        if (isWriteOperation(operation) && endpoint.isWriteResultAsHeader()) {
            exchange.getOut().setHeader(MongoDbConstants.WRITERESULT, result);
        } else {
            exchange.getOut().setBody(result);
        }
    }

    private Function<Exchange, Object> createDoGetColStats() {
        return exch ->
                calculateDb(exch).runCommand(createCollStatsCommand(calculateCollectionName(exch)));
    }

    private Function<Exchange, Object> createDoFindOneByQuery() {
        return exch -> {
            try {
                MongoCollection<BasicDBObject> dbCol = calculateCollection(exch);
                BasicDBObject o = exch.getIn().getMandatoryBody(BasicDBObject.class);

                BasicDBObject sortBy = exch.getIn().getHeader(MongoDbConstants.SORT_BY, BasicDBObject.class);
                BasicDBObject fieldFilter = exch.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, BasicDBObject.class);

                if (fieldFilter == null) {
                    fieldFilter = new BasicDBObject();
                }
                
                if (sortBy == null) {
                    sortBy = new BasicDBObject();
                }
                
                BasicDBObject ret = dbCol.find(o).projection(fieldFilter).sort(sortBy).first();
                exch.getOut().setHeader(MongoDbConstants.RESULT_TOTAL_SIZE, ret == null ? 0 : 1);
                return ret;
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Payload is no BasicDBObject", e);
            }
        };
    }

    private Function<Exchange, Object> createDoCount() {
        return exch -> {
            BasicDBObject query = exch.getIn().getBody(BasicDBObject.class);
            if (query == null) {
                query = new BasicDBObject();
            }
            return (Long) calculateCollection(exch).count(query);
        };
    }

    private Function<Exchange, Object> createDoFindAll() {
        return exchange1 -> {
            Iterable<BasicDBObject> result;
            MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange1);
            // do not use getMandatoryBody, because if the body is empty we want to retrieve all objects in the collection
            BasicDBObject query = null;
            // do not run around looking for a type converter unless there is a need for it
            if (exchange1.getIn().getBody() != null) {
                query = exchange1.getIn().getBody(BasicDBObject.class);
            }
            BasicDBObject fieldFilter = exchange1.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, BasicDBObject.class);

            // get the batch size and number to skip
            Integer batchSize = exchange1.getIn().getHeader(MongoDbConstants.BATCH_SIZE, Integer.class);
            Integer numToSkip = exchange1.getIn().getHeader(MongoDbConstants.NUM_TO_SKIP, Integer.class);
            Integer limit = exchange1.getIn().getHeader(MongoDbConstants.LIMIT, Integer.class);
            BasicDBObject sortBy = exchange1.getIn().getHeader(MongoDbConstants.SORT_BY, BasicDBObject.class);
            FindIterable<BasicDBObject> ret;
            if (query == null && fieldFilter == null) {
                ret = dbCol.find(new BasicDBObject());
            } else if (fieldFilter == null) {
                ret = dbCol.find(query);
            } else if (query != null) {
                ret = dbCol.find(query).projection(fieldFilter);
            } else {
                ret = dbCol.find(new BasicDBObject()).projection(fieldFilter);
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

            if (!MongoDbOutputType.DBCursor.equals(endpoint.getOutputType())) {
                try {
                    result = new ArrayList<>();
                    ret.iterator().forEachRemaining(((List<BasicDBObject>) result)::add);
                    exchange1.getOut().setHeader(MongoDbConstants.RESULT_PAGE_SIZE, ((List<BasicDBObject>) result).size());
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
        return exchange1 -> {
            MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange1);
            boolean singleInsert = true;
            Object insert = exchange1.getIn().getBody(DBObject.class);
            // body could not be converted to DBObject, check to see if it's of type List<DBObject>
            if (insert == null) {
                insert = exchange1.getIn().getBody(List.class);
                // if the body of type List was obtained, ensure that all items are of type DBObject and cast the List to List<DBObject>
                if (insert != null) {
                    singleInsert = false;
                    insert = attemptConvertToList((List) insert, exchange1);
                } else {
                    throw new CamelMongoDbException("MongoDB operation = insert, Body is not conversible to type DBObject nor List<DBObject>");
                }
            }

            if (singleInsert) {
                BasicDBObject insertObject = (BasicDBObject) insert;
                dbCol.insertOne(insertObject);
                exchange1.getIn().setHeader(MongoDbConstants.OID, insertObject.get("_id"));
            } else {
                List<BasicDBObject> insertObjects = (List<BasicDBObject>) insert;
                dbCol.insertMany(insertObjects);
                List<Object> objectIdentification = new ArrayList<>(insertObjects.size());
                objectIdentification.addAll(insertObjects.stream().map(insertObject -> insertObject.get("_id")).collect(Collectors.toList()));
                exchange1.getIn().setHeader(MongoDbConstants.OID, objectIdentification);
            }
            return insert;
        };
    }

    private Function<Exchange, Object> createDoUpdate() {
        return exchange1 -> {
            try {
                MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange1);
                List<BasicDBObject> saveObj = exchange1.getIn().getMandatoryBody((Class<List<BasicDBObject>>) (Class<?>) List.class);
                if (saveObj.size() != 2) {
                    throw new CamelMongoDbException("MongoDB operation = insert, failed because body is not a List of DBObject objects with size = 2");
                }

                BasicDBObject updateCriteria = saveObj.get(0);
                BasicDBObject objNew = saveObj.get(1);

                Boolean multi = exchange1.getIn().getHeader(MongoDbConstants.MULTIUPDATE, Boolean.class);
                Boolean upsert = exchange1.getIn().getHeader(MongoDbConstants.UPSERT, Boolean.class);

                UpdateResult result;
                UpdateOptions options = new UpdateOptions();
                if (upsert != null) {
                    options.upsert(true);
                }
                if (multi == null) {
                    result = dbCol.updateOne(updateCriteria, objNew, options);
                } else {
                    result = dbCol.updateMany(updateCriteria, objNew, options);
                }
                return result;
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Invalid payload for update", e);
            }
        };
    }

    private Function<Exchange, Object> createDoRemove() {
        return exchange1 -> {
            try {
                MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange1);
                BasicDBObject removeObj = exchange1.getIn().getMandatoryBody(BasicDBObject.class);

                DeleteResult result = dbCol.deleteMany(removeObj);
                return result;
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Invalid payload for remove", e);
            }
        };
    }

    private Function<Exchange, Object> createDoAggregate() {
        return exchange1 -> {
            try {
                MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange1);
                DBObject query = exchange1.getIn().getMandatoryBody(DBObject.class);

                // Impossible with java driver to get the batch size and number to skip
                List<BasicDBObject> dbIterator = new ArrayList<>();
                AggregateIterable<BasicDBObject> aggregationResult;

                // Allow body to be a pipeline
                // @see http://docs.mongodb.org/manual/core/aggregation/
                if (query instanceof BasicDBList) {
                    List<Bson> queryList = ((BasicDBList) query).stream().map(o -> (Bson) o).collect(Collectors.toList());
                    aggregationResult = dbCol.aggregate(queryList);
                } else {
                    List<Bson> queryList = new ArrayList<>();
                    queryList.add((Bson) query);
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
        return exchange1 -> {
            try {
                MongoDatabase db = calculateDb(exchange1);
                BasicDBObject cmdObj = exchange1.getIn().getMandatoryBody(BasicDBObject.class);
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
        return exchange1 -> {
            try {
                MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange1);
                String id = exchange1.getIn().getMandatoryBody(String.class);
                BasicDBObject o = new BasicDBObject("_id", id);
                DBObject ret;

                BasicDBObject fieldFilter = exchange1.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, BasicDBObject.class);
                if (fieldFilter == null) {
                    fieldFilter = new BasicDBObject();
                }
                ret = dbCol.find(o).projection(fieldFilter).first();
                exchange1.getOut().setHeader(MongoDbConstants.RESULT_TOTAL_SIZE, ret == null ? 0 : 1);
                return ret;
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Invalid payload for findById", e);
            }
        };
    }

    private Function<Exchange, Object> createDoSave() {
        return exchange1 -> {
            try {
                MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange1);
                BasicDBObject saveObj = exchange1.getIn().getMandatoryBody(BasicDBObject.class);

                UpdateOptions options = new UpdateOptions().upsert(true);
                BasicDBObject queryObject = new BasicDBObject("_id", saveObj.get("_id"));
                UpdateResult result = dbCol.replaceOne(queryObject, saveObj, options);
                exchange1.getIn().setHeader(MongoDbConstants.OID, saveObj.get("_id"));
                return result;
            } catch (InvalidPayloadException e) {
                throw new CamelMongoDbException("Body incorrect type for save", e);
            }
        };
    }

}
