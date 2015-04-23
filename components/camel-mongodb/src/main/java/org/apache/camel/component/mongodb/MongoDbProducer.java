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
import java.util.List;

import com.mongodb.AggregationOutput;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
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
        DBObject result = null;
        DB db = calculateDb(exchange);
        DBObject cmdObj = exchange.getIn().getMandatoryBody(DBObject.class);

        //TODO Manage the read preference
        result = db.command(cmdObj);


        Message responseMessage = prepareResponseMessage(exchange, MongoDbOperation.command);
        responseMessage.setBody(result);
    }

    protected void doGetStats(Exchange exchange, MongoDbOperation operation) throws Exception {
        DBObject result = null;

        if (operation == MongoDbOperation.getColStats) {
            result = calculateCollection(exchange).getStats();
        } else if (operation == MongoDbOperation.getDbStats) {
            // if it's a DB, also take into account the dynamicity option and the DB that is used
            result = calculateDb(exchange).getStats();
        } else {
            throw new CamelMongoDbException("Internal error: wrong operation for getStats variant" + operation);
        }

        Message responseMessage = prepareResponseMessage(exchange, operation);
        responseMessage.setBody(result);
    }

    protected void doRemove(Exchange exchange) throws Exception {
        DBCollection dbCol = calculateCollection(exchange);
        DBObject removeObj = exchange.getIn().getMandatoryBody(DBObject.class);

        WriteConcern wc = extractWriteConcern(exchange);
        WriteResult result = wc == null ? dbCol.remove(removeObj) : dbCol.remove(removeObj, wc);

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.remove);
        // we always return the WriteResult, because whether the getLastError was called or not,
        // the user will have the means to call it or obtain the cached CommandResult
        processAndTransferWriteResult(result, exchange);
        resultMessage.setHeader(MongoDbConstants.RECORDS_AFFECTED, result.getN());
    }

    @SuppressWarnings("unchecked")
    protected void doUpdate(Exchange exchange) throws Exception {
        DBCollection dbCol = calculateCollection(exchange);
        List<DBObject> saveObj = exchange.getIn().getMandatoryBody((Class<List<DBObject>>)(Class<?>)List.class);
        if (saveObj.size() != 2) {
            throw new CamelMongoDbException("MongoDB operation = insert, failed because body is not a List of DBObject objects with size = 2");
        }

        DBObject updateCriteria = saveObj.get(0);
        DBObject objNew = saveObj.get(1);

        Boolean multi = exchange.getIn().getHeader(MongoDbConstants.MULTIUPDATE, Boolean.class);
        Boolean upsert = exchange.getIn().getHeader(MongoDbConstants.UPSERT, Boolean.class);

        WriteResult result;
        WriteConcern wc = extractWriteConcern(exchange);
        // In API 2.7, the default upsert and multi values of update(DBObject, DBObject) are false, false, so we unconditionally invoke the
        // full-signature method update(DBObject, DBObject, boolean, boolean). However, the default behaviour may change in the future, 
        // so it's safer to be explicit at this level for full determinism
        if (multi == null && upsert == null) {
            // for update with no multi nor upsert but with specific WriteConcern there is no update signature without multi and upsert args,
            // so assume defaults
            result = wc == null ? dbCol.update(updateCriteria, objNew) : dbCol.update(updateCriteria, objNew, false, false, wc);
        } else {
            // we calculate the final boolean values so that if any of these
            // parameters is null, it is resolved to false
            result = wc == null ? dbCol.update(updateCriteria, objNew, calculateBooleanValue(upsert), calculateBooleanValue(multi)) : dbCol
                .update(updateCriteria, objNew, calculateBooleanValue(upsert), calculateBooleanValue(multi), wc);
        }

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.update);
        // we always return the WriteResult, because whether the getLastError was called or not, the user will have the means to call it or 
        // obtain the cached CommandResult
        processAndTransferWriteResult(result, exchange);
        resultMessage.setHeader(MongoDbConstants.RECORDS_AFFECTED, result.getN());
    }

    protected void doSave(Exchange exchange) throws Exception {
        DBCollection dbCol = calculateCollection(exchange);
        DBObject saveObj = exchange.getIn().getMandatoryBody(DBObject.class);

        WriteConcern wc = extractWriteConcern(exchange);
        WriteResult result = wc == null ? dbCol.save(saveObj) : dbCol.save(saveObj, wc);
        exchange.getIn().setHeader(MongoDbConstants.OID, saveObj.get("_id"));

        prepareResponseMessage(exchange, MongoDbOperation.save);
        // we always return the WriteResult, because whether the getLastError was called or not, the user will have the means to call it or 
        // obtain the cached CommandResult
        processAndTransferWriteResult(result, exchange);
    }

    protected void doFindById(Exchange exchange) throws Exception {
        DBCollection dbCol = calculateCollection(exchange);
        Object o = exchange.getIn().getMandatoryBody();
        DBObject ret;

        DBObject fieldFilter = exchange.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, DBObject.class);
        if (fieldFilter == null) {
            ret = dbCol.findOne(o);
        } else {
            ret = dbCol.findOne(o, fieldFilter);
        }

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.save);
        resultMessage.setBody(ret);
        resultMessage.setHeader(MongoDbConstants.RESULT_TOTAL_SIZE, ret == null ? 0 : 1);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void doInsert(Exchange exchange) throws Exception {
        DBCollection dbCol = calculateCollection(exchange);
        boolean singleInsert = true;
        Object insert = exchange.getIn().getBody(DBObject.class);
        // body could not be converted to DBObject, check to see if it's of type List<DBObject>
        if (insert == null) {
            insert = exchange.getIn().getBody(List.class);
            // if the body of type List was obtained, ensure that all items are of type DBObject and cast the List to List<DBObject>
            if (insert != null) {
                singleInsert = false;
                insert = attemptConvertToList((List)insert, exchange);
            } else {
                throw new CamelMongoDbException("MongoDB operation = insert, Body is not conversible to type DBObject nor List<DBObject>");
            }
        }

        WriteResult result;
        WriteConcern wc = extractWriteConcern(exchange);
        if (singleInsert) {
            DBObject insertObject = (DBObject) insert;
            result = wc == null ? dbCol.insert(insertObject) : dbCol.insert(insertObject, wc);
            exchange.getIn().setHeader(MongoDbConstants.OID, insertObject.get("_id"));
        } else {
            List<DBObject> insertObjects = (List<DBObject>) insert;
            result = wc == null ? dbCol.insert(insertObjects) : dbCol.insert(insertObjects, wc);
            List<Object> oids = new ArrayList<Object>(insertObjects.size());
            for (DBObject insertObject : insertObjects) {
                oids.add(insertObject.get("_id"));
            }
            exchange.getIn().setHeader(MongoDbConstants.OID, oids);
        }

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.insert);
        // we always return the WriteResult, because whether the getLastError was called or not, the user will have the means to call it or 
        // obtain the cached CommandResult
        processAndTransferWriteResult(result, exchange);
        resultMessage.setBody(result);
    }

    protected void doFindAll(Exchange exchange) throws Exception {
        DBCollection dbCol = calculateCollection(exchange);
        // do not use getMandatoryBody, because if the body is empty we want to retrieve all objects in the collection
        DBObject query = null;
        // do not run around looking for a type converter unless there is a need for it
        if (exchange.getIn().getBody() != null) {
            query = exchange.getIn().getBody(DBObject.class);
        }
        DBObject fieldFilter = exchange.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, DBObject.class);

        // get the batch size and number to skip
        Integer batchSize = exchange.getIn().getHeader(MongoDbConstants.BATCH_SIZE, Integer.class);
        Integer numToSkip = exchange.getIn().getHeader(MongoDbConstants.NUM_TO_SKIP, Integer.class);
        Integer limit = exchange.getIn().getHeader(MongoDbConstants.LIMIT, Integer.class);
        DBObject sortBy = exchange.getIn().getHeader(MongoDbConstants.SORT_BY, DBObject.class);
        DBCursor ret = null;
        try {
            if (query == null && fieldFilter == null) {
                ret = dbCol.find(new BasicDBObject());
            } else if (fieldFilter == null) {
                ret = dbCol.find(query);
            } else {
                ret = dbCol.find(query, fieldFilter);
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
                resultMessage.setBody(ret.toArray());
                resultMessage.setHeader(MongoDbConstants.RESULT_TOTAL_SIZE, ret.count());
                resultMessage.setHeader(MongoDbConstants.RESULT_PAGE_SIZE, ret.size());
            }
        } finally {
            // make sure the cursor is closed
            if (ret != null) {
                ret.close();
            }
        }

    }

    protected void doFindOneByQuery(Exchange exchange) throws Exception {
        DBCollection dbCol = calculateCollection(exchange);
        DBObject o = exchange.getIn().getMandatoryBody(DBObject.class);
        DBObject ret;

        DBObject fieldFilter = exchange.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, DBObject.class);
        if (fieldFilter == null) {
            ret = dbCol.findOne(o);
        } else {
            ret = dbCol.findOne(o, fieldFilter);
        }

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.findOneByQuery);
        resultMessage.setBody(ret);
        resultMessage.setHeader(MongoDbConstants.RESULT_TOTAL_SIZE, ret == null ? 0 : 1);
    }

    protected void doCount(Exchange exchange) throws Exception {
        DBCollection dbCol = calculateCollection(exchange);
        DBObject query = exchange.getIn().getBody(DBObject.class);
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
        DBCollection dbCol = calculateCollection(exchange);
        DBObject query = exchange.getIn().getMandatoryBody(DBObject.class);

        // Impossible with java driver to get the batch size and number to skip
        Iterable<DBObject> dbIterator = null;
        AggregationOutput aggregationResult = null;

        // Allow body to be a pipeline
        // @see http://docs.mongodb.org/manual/core/aggregation/
        if (query instanceof BasicDBList) {
            BasicDBList queryList = (BasicDBList)query;
            aggregationResult = dbCol.aggregate((DBObject)queryList.get(0), queryList
                .subList(1, queryList.size()).toArray(new BasicDBObject[queryList.size() - 1]));
        } else {
            aggregationResult = dbCol.aggregate(query);
        }

        dbIterator = aggregationResult.results();
        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.aggregate);
        resultMessage.setBody(dbIterator);
    }
    // --------- Convenience methods -----------------------
    private DB calculateDb(Exchange exchange) throws Exception {
        // dynamic calculation is an option. In most cases it won't be used and we should not penalise all users with running this
        // resolution logic on every Exchange if they won't be using this functionality at all
        if (!endpoint.isDynamicity()) {
            return endpoint.getDb();
        }

        String dynamicDB = exchange.getIn().getHeader(MongoDbConstants.DATABASE, String.class);
        DB db = null;

        if (dynamicDB == null) {
            db = endpoint.getDb();
        } else {
            db = endpoint.getMongoConnection().getDB(dynamicDB);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Dynamic database selected: {}", db.getName());
        }
        return db;
    }

    private DBCollection calculateCollection(Exchange exchange) throws Exception {
        // dynamic calculation is an option. In most cases it won't be used and we should not penalise all users with running this
        // resolution logic on every Exchange if they won't be using this functionality at all
        if (!endpoint.isDynamicity()) {
            return endpoint.getDbCollection();
        }
        
        String dynamicDB = exchange.getIn().getHeader(MongoDbConstants.DATABASE, String.class);
        String dynamicCollection = exchange.getIn().getHeader(MongoDbConstants.COLLECTION, String.class);
                
        @SuppressWarnings("unchecked")
        List<DBObject> dynamicIndex = exchange.getIn().getHeader(MongoDbConstants.COLLECTION_INDEX, List.class);

        DBCollection dbCol = null;
        
        if (dynamicDB == null && dynamicCollection == null) {
            dbCol = endpoint.getDbCollection();
        } else {
            DB db = calculateDb(exchange);

            if (dynamicCollection == null) {
                dbCol = db.getCollection(endpoint.getCollection());
            } else {
                dbCol = db.getCollection(dynamicCollection);

                // on the fly add index
                if (dynamicIndex == null) {
                    endpoint.ensureIndex(dbCol, endpoint.createIndex());
                } else {
                    endpoint.ensureIndex(dbCol, dynamicIndex);
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Dynamic database and/or collection selected: {}->{}", dbCol.getDB().getName(), dbCol.getName());
        }
        return dbCol;
    }
    
    private boolean calculateBooleanValue(Boolean b) {
        return b == null ? false : b.booleanValue();      
    }
    
    private void processAndTransferWriteResult(WriteResult result, Exchange exchange) {
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
