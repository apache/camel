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
package org.apache.camel.component.cmis;

import java.io.InputStream;
import java.util.Map;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecursiveTreeWalker {
    private static final Logger LOG = LoggerFactory.getLogger(RecursiveTreeWalker.class);

    private final CMISConsumer cmisConsumer;
    private final boolean readContent;
    private final int readCount;
    private final int pageSize;
    private int totalPolled;

    public RecursiveTreeWalker(CMISConsumer cmisConsumer, boolean readContent, int readCount, int pageSize) {
        this.cmisConsumer = cmisConsumer;
        this.readContent = readContent;
        this.readCount = readCount;
        this.pageSize = pageSize;
    }

    int processFolderRecursively(Folder folder) throws Exception {
        processFolderNode(folder);

        
        OperationContext operationContext = cmisConsumer.createOperationContext();
        operationContext.setMaxItemsPerPage(pageSize);

        int count = 0;
        int pageNumber = 0;
        boolean finished = false;
        ItemIterable<CmisObject> itemIterable = folder.getChildren(operationContext);
        while (!finished) {
            ItemIterable<CmisObject> currentPage = itemIterable.skipTo(count).getPage();
            LOG.debug("Processing page {}", pageNumber);
            for (CmisObject child : currentPage) {
                if (CMISHelper.isFolder(child)) {
                    Folder childFolder = (Folder)child;
                    processFolderRecursively(childFolder);
                } else {
                    processNonFolderNode(child, folder);
                }

                count++;
                if (totalPolled == readCount) {
                    finished = true;
                    break;
                }
            }
            pageNumber++;
            if (!currentPage.getHasMoreItems()) {
                finished = true;
            }
        }

        return totalPolled;
    }

    private void processNonFolderNode(CmisObject cmisObject, Folder parentFolder) throws Exception {
        InputStream inputStream = null;
        Map<String, Object> properties = CMISHelper.objectProperties(cmisObject);
        properties.put(CamelCMISConstants.CMIS_FOLDER_PATH, parentFolder.getPath());
        if (CMISHelper.isDocument(cmisObject) && readContent) {
            ContentStream contentStream = ((Document)cmisObject).getContentStream();
            if (contentStream != null) {
                inputStream = contentStream.getStream();
            }
        }
        sendNode(properties, inputStream);
    }

    private void processFolderNode(Folder folder) throws Exception {
        sendNode(CMISHelper.objectProperties(folder), null);
    }

    private void sendNode(Map<String, Object> properties, InputStream inputStream) throws Exception {
        totalPolled += cmisConsumer.sendExchangeWithPropsAndBody(properties, inputStream);
    }
}
