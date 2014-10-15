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

import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSInputFile;
import com.mongodb.util.JSON;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class GridFsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(GridFsProducer.class);
    private GridFsEndpoint endpoint;

    public GridFsProducer(GridFsEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        // set DBObject for query
        DBObject dbObjQuery = (DBObject) JSON.parse("{_id:'inventory'}");

        // set DBObject for update
        DBObject dbObjUpdate = (DBObject) JSON.parse("{$inc:{seq:1}}");

        // get inventoryID
        DBObject invID = endpoint.getDbColCounters().findAndModify(dbObjQuery, dbObjUpdate);

        // get the in message body
        String TPID = exchange.getIn().getBody().toString();

        // TODO set generic
        // specific: get trading partner name, load_type, do_legacy
        DBObject dbObjTPQuery = (DBObject) JSON.parse("{'tpid':'" + TPID + "'}");
        DBObject tpName = endpoint.getDbColTP().findOne(dbObjTPQuery);

        // set the tpName and tpLoadType in the headers
        exchange.getIn().setHeader("tpName", tpName.get("name").toString());
        exchange.getIn().setHeader("tpLoadType", tpName.get("load_type").toString());
        // most won't have do_legacy, so catch error and default to 'Y'
        try {
            exchange.getIn().setHeader("tpDoLegacy", tpName.get("do_legacy").toString());
        } catch (Exception e) {
            exchange.getIn().setHeader("tpDoLegacy", "Y");
        }

        // save the TPID for move
        exchange.getIn().setHeader("TPID", TPID);

        String sInv = invID.get("seq").toString();
        // strip off decimal
        sInv = sInv.substring(0, sInv.lastIndexOf("."));
        exchange.getIn().setHeader("mInv", sInv);

        File file = new File(exchange.getIn().getHeader("gridFsInputFile").toString());
        GridFSInputFile gfsFile = endpoint.getGridFs().createFile(file);

        // set filename
        gfsFile.setFilename(exchange.getIn().getHeader("gridFsFileName").toString());

        // add metadata
        String metaData = "{'inventoryID':" + sInv + ", 'TPID':'" + TPID + "', 'doc_type':'original', 'status':'initial_save'}";
        DBObject dbObject = (DBObject) JSON.parse(metaData);
        gfsFile.setMetaData(dbObject);

        // save the input file into mongoDB
        gfsFile.save();
    }

}
