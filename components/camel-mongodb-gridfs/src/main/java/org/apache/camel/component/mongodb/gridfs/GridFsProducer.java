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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import com.mongodb.util.JSON;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;

public class GridFsProducer extends DefaultProducer {    
    private GridFsEndpoint endpoint;

    public GridFsProducer(GridFsEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = endpoint.getOperation();
        if (operation == null) {
            operation = exchange.getIn().getHeader(GridFsEndpoint.GRIDFS_OPERATION, String.class);
        }
        if (operation == null || "create".equals(operation)) {
            final String filename = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
            Long chunkSize = exchange.getIn().getHeader(GridFsEndpoint.GRIDFS_CHUNKSIZE, Long.class);

            InputStream ins = exchange.getIn().getMandatoryBody(InputStream.class);
            GridFSInputFile gfsFile = endpoint.getGridFs().createFile(ins, filename, true);
            if (chunkSize != null && chunkSize > 0) {
                gfsFile.setChunkSize(chunkSize);
            }
            final String ct = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
            if (ct != null) {
                gfsFile.setContentType(ct);
            }
            String metaData = exchange.getIn().getHeader(GridFsEndpoint.GRIDFS_METADATA, String.class);
            DBObject dbObject = (DBObject) JSON.parse(metaData);
            if (dbObject != null) {
                gfsFile.setMetaData(dbObject);
            }
            gfsFile.save();
            //add headers with the id and file name produced by the driver.
            exchange.getIn().setHeader(Exchange.FILE_NAME_PRODUCED, gfsFile.getFilename());
            exchange.getIn().setHeader(GridFsEndpoint.GRIDFS_FILE_ID_PRODUCED, gfsFile.getId());
        } else if ("remove".equals(operation)) {
            final String filename = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
            endpoint.getGridFs().remove(filename);
        } else if ("findOne".equals(operation)) {
            final String filename = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
            GridFSDBFile file = endpoint.getGridFs().findOne(filename);
            if (file != null) {
                exchange.getIn().setHeader(GridFsEndpoint.GRIDFS_METADATA, JSON.serialize(file.getMetaData()));
                exchange.getIn().setHeader(Exchange.FILE_CONTENT_TYPE, file.getContentType());
                exchange.getIn().setHeader(Exchange.FILE_LENGTH, file.getLength());
                exchange.getIn().setHeader(Exchange.FILE_LAST_MODIFIED, file.getUploadDate());
                exchange.getIn().setBody(file.getInputStream(), InputStream.class);                
            } else {
                throw new FileNotFoundException("No GridFS file for " + filename);
            }
        } else if ("listAll".equals(operation)) {
            final String filename = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
            DBCursor cursor;
            if (filename == null) {
                cursor = endpoint.getGridFs().getFileList();
            } else {
                cursor = endpoint.getGridFs().getFileList(new BasicDBObject("filename", filename));
            }
            exchange.getIn().setBody(new DBCursorFilenameReader(cursor), Reader.class);
        } else if ("count".equals(operation)) {
            final String filename = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
            DBCursor cursor;
            if (filename == null) {
                cursor = endpoint.getGridFs().getFileList();
            } else {
                cursor = endpoint.getGridFs().getFileList(new BasicDBObject("filename", filename));
            }
            exchange.getIn().setBody(cursor.count(), Integer.class);
        } 
        
    }

    
    private class DBCursorFilenameReader extends Reader {
        DBCursor cursor;
        StringBuilder current;
        int pos;
        
        DBCursorFilenameReader(DBCursor c) {
            cursor = c;
            current = new StringBuilder(4096);
            pos = 0;
            fill();
        }
        void fill() {
            if (pos > 0) {
                current.delete(0, pos);
                pos = 0;
            }
            while (cursor.hasNext() && current.length() < 4000) {
                DBObject o = cursor.next();
                current.append(o.get("filename")).append("\t").append(o.get("_id")).append("\n");
            }
        }
        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (pos == current.length()) {
                fill();
            }
            if (pos == current.length()) {
                return -1;
            }
            if (len > (current.length() - pos)) {
                len = current.length() - pos;
            }
            current.getChars(pos, pos + len, cbuf, off);
            pos += len;
            return len;
        }

        @Override
        public void close() throws IOException {
            cursor.close();
        }
    }
}
