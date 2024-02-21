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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.bson.Document;
import org.bson.types.ObjectId;

import static com.mongodb.client.model.Filters.eq;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.GRIDFS_FILE_KEY_CONTENT_TYPE;
import static org.apache.camel.component.mongodb.gridfs.GridFsConstants.GRIDFS_FILE_KEY_FILENAME;

public class GridFsProducer extends DefaultProducer {
    private final GridFsEndpoint endpoint;

    public GridFsProducer(GridFsEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = endpoint.getOperation();
        if (operation == null) {
            operation = exchange.getIn().getHeader(GridFsConstants.GRIDFS_OPERATION, String.class);
        }
        if (operation == null || "create".equals(operation)) {
            final String filename = exchange.getIn().getHeader(GridFsConstants.FILE_NAME, String.class);
            Integer chunkSize = exchange.getIn().getHeader(GridFsConstants.GRIDFS_CHUNKSIZE, Integer.class);

            GridFSUploadOptions options = new GridFSUploadOptions();
            if (chunkSize != null && chunkSize > 0) {
                options.chunkSizeBytes(chunkSize);
            }

            String metaData = exchange.getIn().getHeader(GridFsConstants.GRIDFS_METADATA, String.class);
            if (metaData != null) {
                Document document = Document.parse(metaData);
                if (document != null) {
                    options.metadata(document);
                }
            }

            final String ct = exchange.getIn().getHeader(GridFsConstants.CONTENT_TYPE, String.class);
            if (ct != null) {
                Document metadata = options.getMetadata();
                if (metadata == null) {
                    metadata = new Document();
                    options.metadata(metadata);
                }
                metadata.put(GRIDFS_FILE_KEY_CONTENT_TYPE, ct);
            }

            InputStream ins = exchange.getIn().getMandatoryBody(InputStream.class);
            ObjectId objectId = endpoint.getGridFsBucket().uploadFromStream(filename, ins, options);

            //add headers with the id and file name produced by the driver.
            exchange.getIn().setHeader(GridFsConstants.FILE_NAME_PRODUCED, filename);
            exchange.getIn().setHeader(GridFsConstants.GRIDFS_FILE_ID_PRODUCED, objectId);
            exchange.getIn().setHeader(GridFsConstants.GRIDFS_OBJECT_ID, objectId);
        } else if ("remove".equals(operation)) {
            final ObjectId objectId = exchange.getIn().getHeader(GridFsConstants.GRIDFS_OBJECT_ID, ObjectId.class);
            if (objectId != null) {
                endpoint.getGridFsBucket().delete(objectId);
            } else {
                final String filename = exchange.getIn().getHeader(GridFsConstants.FILE_NAME, String.class);
                GridFSFile file = endpoint.getGridFsBucket().find(eq(GRIDFS_FILE_KEY_FILENAME, filename)).first();
                if (file != null) {
                    endpoint.getGridFsBucket().delete(file.getId());
                }
            }
        } else if ("findOne".equals(operation)) {
            final String filename = exchange.getIn().getHeader(GridFsConstants.FILE_NAME, String.class);
            GridFSDownloadStream downloadStream = endpoint.getGridFsBucket().openDownloadStream(filename);
            GridFSFile file = downloadStream.getGridFSFile();
            Document metadata = file.getMetadata();
            if (metadata != null) {
                exchange.getIn().setHeader(GridFsConstants.GRIDFS_METADATA, metadata.toJson());

                Object contentType = metadata.get(GRIDFS_FILE_KEY_CONTENT_TYPE);
                if (contentType != null) {
                    exchange.getIn().setHeader(GridFsConstants.FILE_CONTENT_TYPE, contentType);
                }
            }
            exchange.getIn().setHeader(GridFsConstants.FILE_LENGTH, file.getLength());
            exchange.getIn().setHeader(GridFsConstants.FILE_LAST_MODIFIED, file.getUploadDate());
            exchange.getIn().setBody(downloadStream, InputStream.class);
        } else if ("listAll".equals(operation)) {
            final String filename = exchange.getIn().getHeader(GridFsConstants.FILE_NAME, String.class);
            MongoCursor<GridFSFile> cursor;
            if (filename == null) {
                cursor = endpoint.getGridFsBucket().find().cursor();
            } else {
                cursor = endpoint.getGridFsBucket().find(eq(GRIDFS_FILE_KEY_FILENAME, filename)).cursor();
            }
            exchange.getIn().setBody(new DBCursorFilenameReader(cursor), Reader.class);
        } else if ("count".equals(operation)) {
            long count;
            final String filename = exchange.getIn().getHeader(GridFsConstants.FILE_NAME, String.class);
            if (filename == null) {
                count = endpoint.getFilesCollection().countDocuments();
            } else {
                count = endpoint.getFilesCollection().countDocuments(eq(GRIDFS_FILE_KEY_FILENAME, filename));
            }
            exchange.getIn().setBody(count, Long.class);
        }
    }

    private static class DBCursorFilenameReader extends Reader {
        MongoCursor<GridFSFile> cursor;
        StringBuilder current;
        int pos;

        DBCursorFilenameReader(MongoCursor<GridFSFile> c) {
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
                GridFSFile file = cursor.next();
                current.append(file.getFilename()).append("\t").append(file.getId()).append("\n");
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
