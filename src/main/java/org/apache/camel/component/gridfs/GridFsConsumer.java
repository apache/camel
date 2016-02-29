/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.camel.component.gridfs;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.util.JSON;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

/**
 * 
 */
public class GridFsConsumer extends DefaultConsumer implements Runnable {
    final GridFsEndpoint endpoint;
    private ExecutorService executor;

    /**
     * @param endpoint
     * @param processor
     */
    public GridFsConsumer(GridFsEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(), 1);
        executor.execute(this);
    }

    @Override
    public void run() {
        DBCursor c = null;
        java.util.Date fromDate = new java.util.Date();
        try {
            Thread.sleep(endpoint.getInitialDelay());
            while (isStarted()) {                
                if (c == null || c.getCursorId() == 0) {
                    if (c != null) {
                        c.close();
                    }
                    String queryString = endpoint.getQuery();
                    DBObject query;
                    if (queryString == null) {
                        query = new BasicDBObject();
                    } else {
                        query = (DBObject) JSON.parse(queryString);
                    }
                    
                    query.put("uploadDate", new BasicDBObject("$gte", fromDate));
                    c = endpoint.getFilesCollection().find(query);
                    fromDate = new java.util.Date();
                }
                while (c.hasNext() && isStarted()) {
                    GridFSDBFile file = (GridFSDBFile)c.next();
                    file = endpoint.getGridFs().findOne(new BasicDBObject("_id", file.getId()));
                    
                    Exchange exchange = endpoint.createExchange();
                    exchange.getIn().setHeader(GridFsEndpoint.GRIDFS_METADATA, JSON.serialize(file.getMetaData()));
                    exchange.getIn().setHeader(Exchange.FILE_CONTENT_TYPE, file.getContentType());
                    exchange.getIn().setHeader(Exchange.FILE_LENGTH, file.getLength());
                    exchange.getIn().setHeader(Exchange.FILE_LAST_MODIFIED, file.getUploadDate());
                    exchange.getIn().setBody(file.getInputStream(), InputStream.class);
                    try {
                        getProcessor().process(exchange);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                Thread.sleep(endpoint.getDelay());
            }
        } catch (Throwable e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        if (c != null) {
            c.close();
        }
    }
    
}
