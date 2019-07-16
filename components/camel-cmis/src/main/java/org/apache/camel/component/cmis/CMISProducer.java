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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReflectionHelper;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.Action;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;

/**
 * The CMIS producer.
 */
public class CMISProducer extends DefaultProducer {

    private final CMISSessionFacadeFactory sessionFacadeFactory;
    private CMISSessionFacade sessionFacade;

    public CMISProducer(CMISEndpoint endpoint, CMISSessionFacadeFactory sessionFacadeFactory) {
        super(endpoint);
        this.sessionFacadeFactory = sessionFacadeFactory;
        this.sessionFacade = null;
    }

    @Override
    public CMISEndpoint getEndpoint() {
        return (CMISEndpoint) super.getEndpoint();
    }

    public void process(Exchange exchange) throws Exception {

        CamelCMISActions action = exchange.getIn().getHeader(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.class);

        Class[] paramMethod = {Exchange.class};
        Method method = ReflectionHelper.findMethod(this.getClass(), action.getMethodName(), paramMethod);
        ObjectHelper.invokeMethod(method, this, exchange);
    }

    private Map<String, Object> filterTypeProperties(Map<String, Object> properties) throws Exception {
        Map<String, Object> result = new HashMap<>(properties.size());

        String objectTypeName = CamelCMISConstants.CMIS_DOCUMENT;
        if (properties.containsKey(PropertyIds.OBJECT_TYPE_ID)) {
            objectTypeName = (String) properties.get(PropertyIds.OBJECT_TYPE_ID);
        }

        Set<String> types = new HashSet<>();
        types.addAll(getSessionFacade().getPropertiesFor(objectTypeName));

        if (getSessionFacade().supportsSecondaries() && properties.containsKey(PropertyIds.SECONDARY_OBJECT_TYPE_IDS)) {
            @SuppressWarnings("unchecked")
            Collection<String> secondaryTypes = (Collection<String>) properties.get(PropertyIds.SECONDARY_OBJECT_TYPE_IDS);
            for (String secondaryType : secondaryTypes) {
                types.addAll(getSessionFacade().getPropertiesFor(secondaryType));
            }
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (types.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public CmisObject createNode(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, PropertyIds.NAME);

        Message message = exchange.getIn();
        String parentFolderId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);
        Folder parentFolder = (Folder) getSessionFacade().getObjectById(parentFolderId);
        Map<String, Object> cmisProperties = filterTypeProperties(message.getHeaders());

        if (isDocument(exchange)) {
            String fileName = message.getHeader(PropertyIds.NAME, String.class);
            String mimeType = getMimeType(message);
            byte[] buf = getBodyData(message);
            ContentStream contentStream = getSessionFacade().createContentStream(fileName, buf, mimeType);
            return storeDocument(parentFolder, cmisProperties, contentStream);
        } else if (isFolder(message)) {
            return storeFolder(parentFolder, cmisProperties);
        } else { // other types
            return storeDocument(parentFolder, cmisProperties, null);
        }
    }

    public List<String> deleteFolder(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_OBJECT_ID);

        Message message = exchange.getIn();

        String objectId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);
        Folder folder = (Folder) getSessionFacade().getObjectById(objectId);
        return folder.deleteTree(true, UnfileObject.DELETE, true);
    }

    public void deleteDocument(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_OBJECT_ID);

        Message message = exchange.getIn();

        String objectId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);
        Document document = (Document) getSessionFacade().getObjectById(objectId);

        document.deleteAllVersions();
    }

    public void moveDocument(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID);
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_SOURCE_FOLDER_ID);
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_OBJECT_ID);

        Message message = exchange.getIn();

        String destinationFolderId = message.getHeader(CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID, String.class);
        String sourceFolderId = message.getHeader(CamelCMISConstants.CMIS_SOURCE_FOLDER_ID, String.class);
        String objectId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);


        Folder sourceFolder = (Folder) getSessionFacade().getObjectById(sourceFolderId);
        Folder targetFolder = (Folder) getSessionFacade().getObjectById(destinationFolderId);

        Document document = (Document) getSessionFacade().getObjectById(objectId);

        if (document != null) {
            if (!document.getAllowableActions().getAllowableActions().contains(Action.CAN_MOVE_OBJECT)) {
                throw new CmisUnauthorizedException("Current user does not have permission to move " + objectId + document.getName());
            }

            try {
                document.move(sourceFolder, targetFolder);
                log.info("Moved document from " + sourceFolder.getName() + " to " + targetFolder.getName());
            } catch (CmisRuntimeException e) {
                log.error("Cannot move document to folder " + targetFolder.getName() + " : " + e.getMessage());
            }
        } else {
            log.error("Document is null, cannot move!");
        }
    }

    public void moveFolder(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID);
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_OBJECT_ID);

        Message message = exchange.getIn();

        String destinationFolderId = message.getHeader(CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID, String.class);
        String objectId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);

        Folder toBeMoved = (Folder) getSessionFacade().getObjectById(objectId);
        Folder targetFolder = (Folder) getSessionFacade().getObjectById(destinationFolderId);

        copyFolderRecursive(targetFolder, toBeMoved);
        toBeMoved.deleteTree(true, UnfileObject.DELETE, true);
    }

    public void copyDocument(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_OBJECT_ID);
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID);

        Message message = exchange.getIn();

        String destinationFolderId = message.getHeader(CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID, String.class);
        String objectId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);
        Folder destinationFolder = (Folder) getSessionFacade().getObjectById(destinationFolderId);

        Document document = (Document) getSessionFacade().getObjectById(objectId);

        document.copy(destinationFolder);
    }

    public void copyFolder(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID);
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_OBJECT_ID);

        Message message = exchange.getIn();

        String destinationFolderId = message.getHeader(CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID, String.class);
        String toCopyFolderId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);

        Folder destinationFolder = (Folder) getSessionFacade().getObjectById(destinationFolderId);
        Folder toCopyFolder = (Folder) getSessionFacade().getObjectById(toCopyFolderId);

        copyFolderRecursive(destinationFolder, toCopyFolder);
    }

    public void copyFolderRecursive(Folder destinationFolder, Folder toCopyFolder) {
        Map<String, Object> folderProperties = new HashMap<String, Object>();
        folderProperties.put(PropertyIds.NAME, toCopyFolder.getName());
        folderProperties.put(PropertyIds.OBJECT_TYPE_ID, toCopyFolder.getBaseTypeId().value());
        Folder newFolder = destinationFolder.createFolder(folderProperties);
        copyChildren(newFolder, toCopyFolder);
    }

    public void copyChildren(Folder destinationFolder, Folder toCopyFolder) {
        ItemIterable<CmisObject> immediateChildren = toCopyFolder.getChildren();
        for (CmisObject child : immediateChildren) {
            if (child instanceof Document) {
                ((Document) child).copy(destinationFolder);
            } else if (child instanceof Folder) {
                copyFolderRecursive(destinationFolder, (Folder) child);
            }
        }
    }

    public void renameDocument(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, PropertyIds.NAME);
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_OBJECT_ID);

        Message message = exchange.getIn();

        String newName = message.getHeader(PropertyIds.NAME, String.class);
        String objectId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);
        try {
            Document document = (Document) getSessionFacade().getObjectById(objectId);
            document.rename(newName);
        } catch (Exception e) {
            throw new CmisObjectNotFoundException("Document with id: " + objectId + " can not be found!");
        }
    }

    public void renameFolder(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, PropertyIds.NAME);
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_OBJECT_ID);

        Message message = exchange.getIn();

        String newName = message.getHeader(PropertyIds.NAME, String.class);
        String objectId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);

        try {
            Folder folder = (Folder) getSessionFacade().getObjectById(objectId);
            folder.rename(newName);
        } catch (Exception e) {
            throw new CmisObjectNotFoundException("Folder with id: " + objectId + " can not be found!");
        }
    }

    public void checkIn(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_OBJECT_ID);

        Message message = exchange.getIn();

        String objectId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);
        String checkInComment = message.getHeader(PropertyIds.CHECKIN_COMMENT, String.class);
        String fileName = message.getHeader(PropertyIds.NAME, String.class);
        String mimeType = getMimeType(message);
        InputStream inputStream = (InputStream) message.getBody();

        byte[] bytes = StreamUtils.copyToByteArray(inputStream);
        Document document = (Document) getSessionFacade().getObjectById(objectId);
        Map<String, Object> properties = filterTypeProperties(message.getHeaders());

        ContentStream contentStream = getSessionFacade().createContentStream(fileName, bytes, mimeType);

        document.checkIn(true, properties, contentStream, checkInComment);

    }

    public void checkOut(Exchange exchange) throws Exception {
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_OBJECT_ID);

        Message message = exchange.getIn();

        String objectId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);

        Document document = (Document) getSessionFacade().getObjectById(objectId);
        document.checkOut();
    }

    public void cancelCheckOut(Exchange exchange)throws Exception {
        validateRequiredHeader(exchange, CamelCMISConstants.CMIS_OBJECT_ID);

        Message message = exchange.getIn();

        String objectId = message.getHeader(CamelCMISConstants.CMIS_OBJECT_ID, String.class);

        Document document = (Document) getSessionFacade().getObjectById(objectId);
        document.cancelCheckOut();
    }

    private Folder getFolderOnPath(Exchange exchange, String path) throws Exception {
        try {
            return (Folder) getSessionFacade().getObjectByPath(path);
        } catch (CmisObjectNotFoundException e) {
            throw new RuntimeExchangeException("Path not found " + path, exchange, e);
        }
    }

    private Document getDocumentOnPath(Exchange exchange, String path) throws Exception {
        try {
            return (Document) getSessionFacade().getObjectByPath(path);
        } catch (CmisObjectNotFoundException e) {
            throw new RuntimeExchangeException("Path not found " + path, exchange, e);
        }
    }

    private String parentFolderPathFor(Message message) throws Exception {
        String customPath = message.getHeader(CamelCMISConstants.CMIS_FOLDER_PATH, String.class);
        if (customPath != null) {
            return customPath;
        }

        if (isFolder(message)) {
            String path = (String) message.getHeader(PropertyIds.PATH);
            String name = (String) message.getHeader(PropertyIds.NAME);
            if (path != null && path.length() > name.length()) {
                return path.substring(0, path.length() - name.length());
            }
        }

        return "/";
    }

    private boolean isFolder(Message message) throws Exception {
        String baseTypeId = message.getHeader(PropertyIds.OBJECT_TYPE_ID, String.class);
        if (baseTypeId != null) {
            return CamelCMISConstants.CMIS_FOLDER.equals(getSessionFacade().getCMISTypeFor(baseTypeId));
        }
        return message.getBody() == null;
    }

    private Folder storeFolder(Folder parentFolder, Map<String, Object> cmisProperties) throws Exception {
        if (!cmisProperties.containsKey(PropertyIds.OBJECT_TYPE_ID)) {
            cmisProperties.put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_FOLDER);
        }
        log.debug("Creating folder with properties: {}", cmisProperties);
        return parentFolder.createFolder(cmisProperties);
    }

    private Document storeDocument(Folder parentFolder, Map<String, Object> cmisProperties, ContentStream contentStream) throws Exception {
        if (!cmisProperties.containsKey(PropertyIds.OBJECT_TYPE_ID)) {
            cmisProperties.put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        }

        VersioningState versioningState = VersioningState.NONE;
        if (getSessionFacade().isObjectTypeVersionable((String) cmisProperties.get(PropertyIds.OBJECT_TYPE_ID))) {
            versioningState = VersioningState.MAJOR;
        }
        log.debug("Creating document with properties: {}", cmisProperties);

        return parentFolder.createDocument(cmisProperties, contentStream, versioningState);
    }

    private void validateRequiredHeader(Exchange exchange, String name) throws NoSuchHeaderException {
        ExchangeHelper.getMandatoryHeader(exchange, name, String.class);
    }

    private boolean isDocument(Exchange exchange) throws Exception {
        String baseTypeId = exchange.getIn().getHeader(PropertyIds.OBJECT_TYPE_ID, String.class);
        if (baseTypeId != null) {
            return CamelCMISConstants.CMIS_DOCUMENT.equals(getSessionFacade().getCMISTypeFor(baseTypeId));
        }
        return exchange.getIn().getBody() != null;
    }

    private byte[] getBodyData(Message message) {
        return message.getBody(byte[].class);
    }

    private String getMimeType(Message message) throws NoSuchHeaderException {
        String mimeType = message.getHeader(PropertyIds.CONTENT_STREAM_MIME_TYPE, String.class);
        if (mimeType == null) {
            mimeType = MessageHelper.getContentType(message);
        }
        return mimeType;
    }

    private CMISSessionFacade getSessionFacade() throws Exception {
        if (sessionFacade == null) {
            CMISSessionFacade sessionFacade = sessionFacadeFactory.create(getEndpoint());
            sessionFacade.initSession();
            // make sure to set sessionFacade to the field after successful initialisation
            // so that it has a valid session
            this.sessionFacade = sessionFacade;
        }

        return sessionFacade;
    }
}