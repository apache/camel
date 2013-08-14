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
package org.apache.camel.component.cmis;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The CMIS producer.
 */
public class CMISProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(CMISProducer.class);
    private final CMISSessionFacade cmisSessionFacade;

    public CMISProducer(CMISEndpoint endpoint, CMISSessionFacade cmisSessionFacade) {
        super(endpoint);
        this.cmisSessionFacade = cmisSessionFacade;
    }

    public void process(Exchange exchange) throws Exception {
        CmisObject cmisObject = createNode(exchange);
        LOG.debug("Created node with id: {}", cmisObject.getId());
        exchange.getOut().setBody(cmisObject.getId());
    }

    private CmisObject createNode(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, PropertyIds.NAME);

        Message message = exchange.getIn();
        String parentFolderPath = parentFolderPathFor(message);
        Folder parentFolder = getFolderOnPath(exchange, parentFolderPath);
        Map<String, Object> cmisProperties = CMISHelper.filterCMISProperties(message.getHeaders());

        if (isDocument(exchange)) {
            String fileName = message.getHeader(PropertyIds.NAME, String.class);
            String mimeType = getMimeType(message);
            byte[] buf = getBodyData(message);
            ContentStream contentStream = cmisSessionFacade.createContentStream(fileName, buf, mimeType);
            return storeDocument(parentFolder, cmisProperties, contentStream);
        } else if (isFolder(message)) {
            return storeFolder(parentFolder, cmisProperties);
        } else {  //other types
            return storeDocument(parentFolder, cmisProperties, null);
        }
    }

    private Folder getFolderOnPath(Exchange exchange, String path) {
        try {
            return (Folder)cmisSessionFacade.getObjectByPath(path);
        } catch (CmisObjectNotFoundException e) {
            throw new RuntimeExchangeException("Path not found " + path, exchange, e);
        }
    }

    private String parentFolderPathFor(Message message) {
        String customPath = message.getHeader(CamelCMISConstants.CMIS_FOLDER_PATH, String.class);
        if (customPath != null) {
            return customPath;
        }

        if (isFolder(message)) {
            String path = (String)message.getHeader(PropertyIds.PATH);
            String name = (String)message.getHeader(PropertyIds.NAME);
            if (path != null && path.length() > name.length()) {
                return path.substring(0, path.length() - name.length());
            }
        }

        return "/";
    }

    private boolean isFolder(Message message) {
        String baseTypeId = message.getHeader(PropertyIds.OBJECT_TYPE_ID, String.class);
        if (baseTypeId != null) {
            return baseTypeId.equals(CamelCMISConstants.CMIS_FOLDER);
        }
        return message.getBody() == null;
    }

    private Folder storeFolder(Folder parentFolder, Map<String, Object> cmisProperties) {
        if (!cmisProperties.containsKey(PropertyIds.OBJECT_TYPE_ID)) {
            cmisProperties.put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_FOLDER);
        }
        LOG.debug("Creating folder with properties: {}", cmisProperties);
        return parentFolder.createFolder(cmisProperties);
    }

    private Document storeDocument(Folder parentFolder, Map<String, Object> cmisProperties,
                                   ContentStream contentStream) {
        if (!cmisProperties.containsKey(PropertyIds.OBJECT_TYPE_ID)) {
            cmisProperties.put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        }

        VersioningState versioningState = VersioningState.NONE;
        if (cmisSessionFacade
                .isObjectTypeVersionable((String)cmisProperties.get(PropertyIds.OBJECT_TYPE_ID))) {
            versioningState = VersioningState.MAJOR;
        }
        LOG.debug("Creating document with properties: {}", cmisProperties);
        return parentFolder.createDocument(cmisProperties, contentStream, versioningState);
    }

    private void validateRequiredHeader(Exchange exchange, String name) throws NoSuchHeaderException {
        ExchangeHelper.getMandatoryHeader(exchange, name, String.class);
    }

    private boolean isDocument(Exchange exchange) {
        String baseTypeId = exchange.getIn().getHeader(PropertyIds.OBJECT_TYPE_ID, String.class);
        if (baseTypeId != null) {
            return baseTypeId.equals(CamelCMISConstants.CMIS_DOCUMENT);
        }
        return exchange.getIn().getBody() != null;
    }

    private byte[] getBodyData(Message message) {
        return message.getBody(new byte[0].getClass());
    }

    private String getMimeType(Message message) throws NoSuchHeaderException {
        String mimeType = message.getHeader(PropertyIds.CONTENT_STREAM_MIME_TYPE, String.class);
        if (mimeType == null) {
            mimeType = MessageHelper.getContentType(message);
        }
        return mimeType;
    }
}
