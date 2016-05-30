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
import java.util.Iterator;
import java.util.List;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MongoDb producer.
 */
public class MongoDbProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbProducer.class);
    private MongoDbEndpoint endpoint;

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
     * @throws Exception
     */
    protected void invokeOperation(MongoDbOperation operation, Exchange exchange) throws Exception {
        switch (operation) {
        case count:
            doCount(exchange);
            break;

        case findOneByQuery:
            doFindOneByQuery(exchange);
            break;

        case findById:
            doFindById(exchange);
            break;

        case findAll:
            doFindAll(exchange);
            break;

        case insert:
            doInsert(exchange);
            break;

        case save:
            doSave(exchange);
            break;

        case update:
            doUpdate(exchange);
            break;

        case remove:
            doRemove(exchange);
            break;

        case aggregate:
            doAggregate(exchange);
            break;

        case getDbStats:
            doGetStats(exchange, MongoDbOperation.getDbStats);
            break;

        case getColStats:
            doGetStats(exchange, MongoDbOperation.getColStats);
            break;
        case command:
            doCommand(exchange);
            break;
        default:
            throw new CamelMongoDbException("Operation not supported. Value: " + operation);
        }
    }

    // ----------- MongoDB operations ----------------

    protected void doCommand(Exchange exchange) throws Exception {
        Document result;
        MongoDatabase db = calculateDb(exchange);
        BasicDBObject cmdObj = exchange.getIn().getMandatoryBody(BasicDBObject.class);

        //TODO Manage the read preference
        result = db.runCommand(cmdObj);


        Message responseMessage = prepareResponseMessage(exchange, MongoDbOperation.command);
        responseMessage.setBody(result);
    }

    protected void doGetStats(Exchange exchange, MongoDbOperation operation) throws Exception {
        Document result = null;

        if (operation == MongoDbOperation.getColStats) {
            result = calculateDb(exchange).runCommand(createCollStatsCommand(calculateCollectionName(exchange)));
        } else if (operation == MongoDbOperation.getDbStats) {
            // if it's a DB, also take into account the dynamicity option and the DB that is used
            result = calculateDb(exchange).runCommand(createDbStatsCommand());
        } else {
            throw new CamelMongoDbException("Internal error: wrong operation for getStats variant" + operation);
        }

        Message responseMessage = prepareResponseMessage(exchange, operation);
        responseMessage.setBody(result);
    }

    private BasicDBObject createDbStatsCommand() {
        return new BasicDBObject("dbStats", 1).append("scale", 1);
    }

    private BasicDBObject createCollStatsCommand(String collectionName) {
        return new BasicDBObject("collStats", collectionName);
    }

    protected void doRemove(Exchange exchange) throws Exception {
        MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange);
        BasicDBObject removeObj = exchange.getIn().getMandatoryBody(BasicDBObject.class);

        DeleteResult result = dbCol.deleteMany(removeObj);

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.remove);
        // we always return the WriteResult, because whether the getLastError was called or not,
        // the user will have the means to call it or obtain the cached CommandResult
        processAndTransferDeleteResult(result, exchange);
        resultMessage.setHeader(MongoDbConstants.RECORDS_AFFECTED, result.getDeletedCount());
    }

    @SuppressWarnings("unchecked")
    protected void doUpdate(Exchange exchange) throws Exception {
        MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange);
        List<BasicDBObject> saveObj = exchange.getIn().getMandatoryBody((Class<List<BasicDBObject>>) (Class<?>) List.class);
        if (saveObj.size() != 2) {
            throw new CamelMongoDbException("MongoDB operation = insert, failed because body is not a List of DBObject objects with size = 2");
        }

        BasicDBObject updateCriteria = saveObj.get(0);
        BasicDBObject objNew = saveObj.get(1);

        Boolean multi = exchange.getIn().getHeader(MongoDbConstants.MULTIUPDATE, Boolean.class);
        Boolean upsert = exchange.getIn().getHeader(MongoDbConstants.UPSERT, Boolean.class);

        UpdateResult result;
        WriteConcern wc = extractWriteConcern(exchange);
        // In API 2.7, the default upsert and multi values of update(DBObject, DBObject) are false, false, so we unconditionally invoke the
        // full-signature method update(DBObject, DBObject, boolean, boolean). However, the default behaviour may change in the future, 
        // so it's safer to be explicit at this level for full determinism
        if (multi == null && upsert == null) {
            // for update with no multi nor upsert but with specific WriteConcern there is no update signature without multi and upsert args,
            // so assume defaults
            result = dbCol.updateOne(updateCriteria, objNew);
        } else if (multi == null) {
            // we calculate the final boolean values so that if any of these
            // parameters is null, it is resolved to false
            UpdateOptions options = new UpdateOptions().upsert(true);
            result = dbCol.updateOne(updateCriteria, objNew, options);
        } else if (upsert == null) {
            result = dbCol.updateMany(updateCriteria, objNew);
        } else {
            UpdateOptions options = new UpdateOptions().upsert(true);
            result = dbCol.updateOne(updateCriteria, objNew, options);
        }

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.update);
        // we always return the WriteResult, because whether the getLastError was called or not, the user will have the means to call it or 
        // obtain the cached CommandResult
        processAndTransferUpdateResult(result, exchange);
        resultMessage.setHeader(MongoDbConstants.RECORDS_AFFECTED, result.getModifiedCount());
    }

    /**
     * upserts a document
     *
     * @param exchange
     * @throws Exception
     */
    protected void doSave(Exchange exchange) throws Exception {
        MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange);
        BasicDBObject saveObj = exchange.getIn().getMandatoryBody(BasicDBObject.class);

        UpdateOptions options = new UpdateOptions().upsert(true);
        BasicDBObject queryObject = new BasicDBObject("_id", saveObj.get("_id"));
        UpdateResult result = dbCol.replaceOne(queryObject, saveObj, options);
        exchange.getIn().setHeader(MongoDbConstants.OID, saveObj.get("_id"));

        prepareResponseMessage(exchange, MongoDbOperation.save);
        //TODO: insertOne doesn't return a WriteResult
        // we always return the WriteResult, because whether the getLastError was called or not, the user will have the means to call it or 
        // obtain the cached CommandResult
        processAndTransferUpdateResult(result, exchange);
    }

    private void processAndTransferResult(Object result, Exchange exchange) {
        // determine where to set the WriteResult: as the OUT body or as an IN message header
        if (endpoint.isWriteResultAsHeader()) {
            exchange.getOut().setHeader(MongoDbConstants.WRITERESULT, result);
        } else {
            exchange.getOut().setBody(result);
        }

    }

    protected void doFindById(Exchange exchange) throws Exception {
        MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange);
        String id = exchange.getIn().getMandatoryBody(String.class);
        BasicDBObject o = new BasicDBObject("_id", id);
        DBObject ret;

        BasicDBObject fieldFilter = exchange.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, BasicDBObject.class);
        if (fieldFilter == null) {
            ret = dbCol.find(o).first();
        } else {
            ret = dbCol.find(o).filter(fieldFilter).first();
        }

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.save);
        resultMessage.setBody(ret);
        resultMessage.setHeader(MongoDbConstants.RESULT_TOTAL_SIZE, ret == null ? 0 : 1);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    /**
     * insert new documents
     */
    protected void doInsert(Exchange exchange) throws Exception {
        MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange);
        boolean singleInsert = true;
        Object insert = exchange.getIn().getBody(DBObject.class);
        // body could not be converted to DBObject, check to see if it's of type List<DBObject>
        if (insert == null) {
            insert = exchange.getIn().getBody(List.class);
            // if the body of type List was obtained, ensure that all items are of type DBObject and cast the List to List<DBObject>
            if (insert != null) {
                singleInsert = false;
                insert = attemptConvertToList((List) insert, exchange);
            } else {
                throw new CamelMongoDbException("MongoDB operation = insert, Body is not conversible to type DBObject nor List<DBObject>");
            }
        }

        if (singleInsert) {
            BasicDBObject insertObject = (BasicDBObject) insert;
            dbCol.insertOne(insertObject);
            exchange.getIn().setHeader(MongoDbConstants.OID, insertObject.get("_id"));
        } else {
            List<BasicDBObject> insertObjects = (List<BasicDBObject>) insert;
            dbCol.insertMany(insertObjects);
            List<Object> oids = new ArrayList<Object>(insertObjects.size());
            for (DBObject insertObject : insertObjects) {
                oids.add(insertObject.get("_id"));
            }
            exchange.getIn().setHeader(MongoDbConstants.OID, oids);
        }

        prepareResponseMessage(exchange, MongoDbOperation.insert);

        processAndTransferResult(insert, exchange);
    }

    protected void doFindAll(Exchange exchange) throws Exception {
        MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange);
        // do not use getMandatoryBody, because if the body is empty we want to retrieve all objects in the collection
        BasicDBObject query = null;
        // do not run around looking for a type converter unless there is a need for it
        if (exchange.getIn().getBody() != null) {
            query = exchange.getIn().getBody(BasicDBObject.class);
        }
        BasicDBObject fieldFilter = exchange.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, BasicDBObject.class);

        // get the batch size and number to skip
        Integer batchSize = exchange.getIn().getHeader(MongoDbConstants.BATCH_SIZE, Integer.class);
        Integer numToSkip = exchange.getIn().getHeader(MongoDbConstants.NUM_TO_SKIP, Integer.class);
        Integer limit = exchange.getIn().getHeader(MongoDbConstants.LIMIT, Integer.class);
        BasicDBObject sortBy = exchange.getIn().getHeader(MongoDbConstants.SORT_BY, BasicDBObject.class);
        FindIterable<BasicDBObject> ret = null;
        try {
            if (query == null && fieldFilter == null) {
                ret = dbCol.find(new BasicDBObject());
            } else if (fieldFilter == null) {
                ret = dbCol.find(query);
            } else {
                ret = dbCol.find(new BasicDBObject()).projection(fieldFilter);
            }

            if (sortBy != null) {
                ret.sort(sortBy);
            }

            if (batchSize != null) {
                ret.batchSize(batchSize.intValue());
            }

            if (numToSkip != null) {
                ret.skip(numToSkip.intValue());
            }

            if (limit != null) {
                ret.limit(limit.intValue());
            }

            Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.findAll);
            if (MongoDbOutputType.DBCursor.equals(endpoint.getOutputType())) {
                resultMessage.setBody(ret.iterator());
            } else {
                List<BasicDBObject> result = new ArrayList<>();
                ret.iterator().forEachRemaining(result::add);
                resultMessage.setBody(result);
                //TODO: decide what to do with total number of elements (count query needed).
                //resultMessage.setHeader(MongoDbConstants.RESULT_TOTAL_SIZE, ret.....);
                resultMessage.setHeader(MongoDbConstants.RESULT_PAGE_SIZE, result.size());
            }
        } finally {
            // make sure the cursor is closed
            if (ret != null) {
                ret.iterator().close();
            }
        }

    }

    protected void doFindOneByQuery(Exchange exchange) throws Exception {
        MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange);
        BasicDBObject o = exchange.getIn().getMandatoryBody(BasicDBObject.class);
        BasicDBObject ret;

        BasicDBObject fieldFilter = exchange.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, BasicDBObject.class);
        if (fieldFilter == null) {
            ret = dbCol.find(o).first();
        } else {
            ret = dbCol.find(o).filter(fieldFilter).first();
        }

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.findOneByQuery);
        resultMessage.setBody(ret);
        resultMessage.setHeader(MongoDbConstants.RESULT_TOTAL_SIZE, ret == null ? 0 : 1);
    }

    protected void doCount(Exchange exchange) throws Exception {
        MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange);
        BasicDBObject query = exchange.getIn().getBody(BasicDBObject.class);
        Long answer;
        if (query == null) {
            answer = dbCol.count();
        } else {
            answer = dbCol.count(query);
        }
        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.count);
        resultMessage.setBody(answer);
    }

    /**
     * All headers except collection and database are non available for this
     * operation.
     *
     * @param exchange
     * @throws Exception
     */
    protected void doAggregate(Exchange exchange) throws Exception {
        MongoCollection<BasicDBObject> dbCol = calculateCollection(exchange);
        DBObject query = exchange.getIn().getMandatoryBody(DBObject.class);

        // Impossible with java driver to get the batch size and number to skip
        List<BasicDBObject> dbIterator = new ArrayList<>();
        AggregateIterable<BasicDBObject> aggregationResult;

        // Allow body to be a pipeline
        // @see http://docs.mongodb.org/manual/core/aggregation/
        if (query instanceof BasicDBList) {
            //BasicDBList queryList = (BasicDBList)query;
            List<Bson> queryList = new ArrayList<>();
            Iterator<Object> it = ((BasicDBList) query).iterator();
            while (it.hasNext()) {
                queryList.add((Bson) it.next());
            }
            aggregationResult = dbCol.aggregate(queryList);
        } else {
            List<Bson> queryList = new ArrayList<>();
            queryList.add((Bson) query);
            aggregationResult = dbCol.aggregate(queryList);
        }

        aggregationResult.iterator().forEachRemaining(dbIterator::add);
        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.aggregate);
        resultMessage.setBody(dbIterator);
    }

    // --------- Convenience methods -----------------------
    private MongoDatabase calculateDb(Exchange exchange) throws Exception {
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

    private String calculateCollectionName(Exchange exchange) throws Exception {
        if (!endpoint.isDynamicity()) {
            return endpoint.getCollection();
        }
        String dynamicCollection = exchange.getIn().getHeader(MongoDbConstants.COLLECTION, String.class);
        if (dynamicCollection == null) {
            return endpoint.getCollection();
        }
        return dynamicCollection;
    }

    private MongoCollection<BasicDBObject> calculateCollection(Exchange exchange) throws Exception {
        // dynamic calculation is an option. In most cases it won't be used and we should not penalise all users with running this
        // resolution logic on every Exchange if they won't be using this functionality at all
        if (!endpoint.isDynamicity()) {
            return endpoint.getMongoCollection();
        }

        String dynamicDB = exchange.getIn().getHeader(MongoDbConstants.DATABASE, String.class);
        String dynamicCollection = exchange.getIn().getHeader(MongoDbConstants.COLLECTION, String.class);

        @SuppressWarnings("unchecked")
        List<BasicDBObject> dynamicIndex = exchange.getIn().getHeader(MongoDbConstants.COLLECTION_INDEX, List.class);

        MongoCollection<BasicDBObject> dbCol;

        if (dynamicDB == null && dynamicCollection == null) {
            dbCol = endpoint.getMongoCollection();
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

    private void processAndTransferDeleteResult(DeleteResult result, Exchange exchange) {
        // determine where to set the WriteResult: as the OUT body or as an IN message header
        if (endpoint.isWriteResultAsHeader()) {
            exchange.getOut().setHeader(MongoDbConstants.WRITERESULT, result);
        } else {
            exchange.getOut().setBody(result);
        }
    }

    private void processAndTransferUpdateResult(UpdateResult result, Exchange exchange) {
        // determine where to set the WriteResult: as the OUT body or as an IN message header
        if (endpoint.isWriteResultAsHeader()) {
            exchange.getOut().setHeader(MongoDbConstants.WRITERESULT, result);
        } else {
            exchange.getOut().setBody(result);
        }
    }

    private WriteConcern extractWriteConcern(Exchange exchange) throws CamelMongoDbException {
        Object o = exchange.getIn().getHeader(MongoDbConstants.WRITECONCERN);

        if (o == null) {
            return null;
        } else if (o instanceof WriteConcern) {
            return ObjectHelper.cast(WriteConcern.class, o);
        } else if (o instanceof String) {
            WriteConcern answer = WriteConcern.valueOf(ObjectHelper.cast(String.class, o));
            if (answer == null) {
                throw new CamelMongoDbException("WriteConcern specified in the " + MongoDbConstants.WRITECONCERN + " header, with value " + o
                        + " could not be resolved to a WriteConcern type");
            }
        }

        // should never get here
        LOG.warn("A problem occurred while resolving the Exchange's Write Concern");
        return null;
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

    private Message prepareResponseMessage(Exchange exchange, MongoDbOperation operation) {
        Message answer = exchange.getOut();
        MessageHelper.copyHeaders(exchange.getIn(), answer, false);
        if (isWriteOperation(operation) && endpoint.isWriteResultAsHeader()) {
            answer.setBody(exchange.getIn().getBody());
        }
        return answer;
    }

    private boolean isWriteOperation(MongoDbOperation operation) {
        return MongoDbComponent.WRITE_OPERATIONS.contains(operation);
    }

}
