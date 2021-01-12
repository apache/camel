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

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.FileableCmisObject;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CMISProducerTest extends CMISTestSupport {

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Test
    void storeMessageBodyAsTextDocument() throws Exception {
        String content = "Some content to be store";
        Exchange exchange = createExchangeWithInBody(content);
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain; charset=UTF-8");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.file");
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.CREATE);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, createSession().getRootFolder().getId());

        template.send(exchange);

        CmisObject object = exchange.getMessage().getBody(CmisObject.class);

        assertNotNull(object);

        String newNodeContent = getDocumentContentAsString(object.getId());
        assertEquals(content, newNodeContent);
    }

    @Test
    void getDocumentMimeTypeFromMessageContentType() {
        Exchange exchange = createExchangeWithInBody("Some content to be store");
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.file");
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.CREATE);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, createSession().getRootFolder().getId());

        template.send(exchange);
        CmisObject cmisObject = exchange.getMessage().getBody(CmisObject.class);

        assertEquals("text/plain", cmisObject.getPropertyValue(PropertyIds.CONTENT_STREAM_MIME_TYPE));
    }

    @Test
    void namePropertyIsAlwaysRequired() {
        Exchange exchange = createExchangeWithInBody("Some content that will fail to be stored");
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain; charset=UTF-8");
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.CREATE);

        template.send(exchange);
        Exception exception = exchange.getException();
        CmisObject cmisObject = exchange.getMessage().getBody(CmisObject.class);

        assertNull(cmisObject);
        assertEquals("org.apache.camel.NoSuchHeaderException", exception.getCause().getClass().getName());
    }

    @Test
    void createDocumentWithoutContentByExplicitlySpecifyingObjectTypeHeader() {
        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain; charset=UTF-8");
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.file");
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.CREATE);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, createSession().getRootFolder().getId());

        template.send(exchange);

        CmisObject cmisObject = exchange.getMessage().getBody(CmisObject.class);
        assertNotNull(cmisObject);

        assertEquals(CamelCMISConstants.CMIS_DOCUMENT, cmisObject.getPropertyValue(PropertyIds.OBJECT_TYPE_ID));
    }

    @Test
    void emptyBodyAndMissingObjectTypeHeaderCreatesFolderNode() {
        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "testFolder");
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.CREATE);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, createSession().getRootFolder().getId());

        template.send(exchange);
        CmisObject cmisObject = exchange.getMessage().getBody(CmisObject.class);

        assertNotNull(cmisObject);
        assertEquals(CamelCMISConstants.CMIS_FOLDER, cmisObject.getPropertyValue(PropertyIds.OBJECT_TYPE_ID));
        assertTrue(cmisObject instanceof Folder);
    }

    @Test
    void cmisPropertiesAreStored() {
        Exchange exchange = createExchangeWithInBody("Some content to be store");
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain; charset=UTF-8");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.txt");
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.CREATE);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, createSession().getRootFolder().getId());
        exchange.getIn().getHeaders().put(CamelCMISConstants.VERSIONING_STATE, VersioningState.MAJOR);

        template.send(exchange);
        CmisObject cmisObject = exchange.getMessage().getBody(CmisObject.class);

        assertEquals("test.txt", cmisObject.getPropertyValue(PropertyIds.NAME));
        assertEquals("text/plain; charset=UTF-8",
                cmisObject.getPropertyValue(PropertyIds.CONTENT_STREAM_MIME_TYPE));
    }

    @Test
    void cmisSecondaryTypePropertiesAreStored() {

        List<String> secondaryTypes = Arrays.asList("MySecondaryType");

        Exchange exchange = createExchangeWithInBody("Some content to be store");
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain; charset=UTF-8");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.txt");
        exchange.getIn().getHeaders().put(PropertyIds.SECONDARY_OBJECT_TYPE_IDS, secondaryTypes);
        exchange.getIn().getHeaders().put("SecondaryStringProp", "secondaryTypePropValue");
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.CREATE);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, createSession().getRootFolder().getId());

        template.send(exchange);
        CmisObject cmisObject = exchange.getMessage().getBody(CmisObject.class);

        assertEquals(1, cmisObject.getSecondaryTypes().size());
        assertEquals("secondaryTypePropValue", cmisObject.getPropertyValue("SecondaryStringProp"));
    }

    @Test
    void failConnectingToNonExistingRepository() throws Exception {
        Endpoint endpoint = context.getEndpoint("cmis://" + getUrl()
                                                + "?username=admin&password=admin&repositoryId=NON_EXISTING_ID");
        Producer producer = endpoint.createProducer();

        Exchange exchange = createExchangeWithInBody("Some content to be store");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.txt");
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.CREATE);

        assertThrows(RuntimeCamelException.class, () -> {
            producer.process(exchange);
        });
    }

    @Test
    void failCreatingFolderAtNonExistingParentId() {

        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "folder1");
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_FOLDER);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.CREATE);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, "Wrong Id");

        template.send(exchange);
        assertTrue(exchange.getException() instanceof RuntimeCamelException);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("cmis://" + getUrl());
            }
        };
    }

    @Test
    void renameFolder() {

        Folder folder = createFolderWithName("New Folder");

        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_FOLDER);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, folder.getId());
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "Renamed Folder");
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.RENAME);

        template.send(exchange);

        CmisObject cmisObject = exchange.getMessage().getBody(CmisObject.class);

        assertEquals("Renamed Folder", cmisObject.getPropertyValue(PropertyIds.NAME));
        assertEquals(folder.getId(), cmisObject.getId());
        assertTrue(cmisObject instanceof Folder);
    }

    @Test
    void renameDocument() throws UnsupportedEncodingException {

        Document document = createTextDocument(createSession().getRootFolder(), "This is new test document", "document.txt");

        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, document.getId());
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "Renamed Document.txt");
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.RENAME);

        template.send(exchange);

        CmisObject cmisObject = exchange.getMessage().getBody(CmisObject.class);

        assertEquals("Renamed Document.txt", cmisObject.getPropertyValue(PropertyIds.NAME));
        assertEquals(document.getId(), cmisObject.getId());
        assertTrue(cmisObject instanceof Document);
    }

    @Test
    void deleteFolder() {

        Folder folder = createFolderWithName("Test");

        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_FOLDER);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.DELETE_FOLDER);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, folder.getId());

        template.send(exchange);

        List<String> unsuccessfullyDeletedObjects = exchange.getMessage().getBody(List.class);
        assertTrue(unsuccessfullyDeletedObjects.isEmpty());

        assertThrows(CmisObjectNotFoundException.class, () -> {
            // Try to get already deleted object by id should throw
            createSession().getObject(folder.getId());
        });
    }

    @Test
    void deleteDocument() throws UnsupportedEncodingException {

        Document document = createTextDocument(createSession().getRootFolder(), "This is new test document", "document.txt");

        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.DELETE_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, document.getId());

        template.send(exchange);

        createSession().getObject(document.getId());
    }

    @Test
    void moveFolder() {

        Folder toBeMoved = createFolderWithName("Moving folder");
        Folder destinationFolder = createFolderWithName("Destination");

        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_FOLDER);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.MOVE_FOLDER);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, toBeMoved.getId());
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID, destinationFolder.getId());

        template.send(exchange);

        FileableCmisObject movedFolder = exchange.getMessage().getBody(FileableCmisObject.class);

        assertEquals(movedFolder.getParents().get(0).getId(), destinationFolder.getId());
    }

    @Test
    void moveDocument() throws UnsupportedEncodingException {

        Folder rootFolder = createSession().getRootFolder();
        Document toBeMoved = createTextDocument(rootFolder, "This is new test document", "document.txt");
        Folder destinationFolder = createFolderWithName("Destination");

        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.MOVE_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, toBeMoved.getId());
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_SOURCE_FOLDER_ID, rootFolder.getId());
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID, destinationFolder.getId());

        template.send(exchange);

        assertEquals(toBeMoved.getParents().get(0).getId(), destinationFolder.getId());
    }

    @Test
    void copyDocument() throws UnsupportedEncodingException {
        Folder destination = createFolderWithName("Destination");
        Document document = createTextDocument(createSession().getRootFolder(), "This is new test document", "document.txt");

        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.COPY_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, document.getId());
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID, destination.getId());

        template.send(exchange);

        Document copy = exchange.getMessage().getBody(Document.class);

        assertNotNull(copy);
        assertEquals(document.getName(), copy.getName());
        assertEquals(document.getContentStreamLength(), copy.getContentStreamLength());
        assertEquals(destination.getId(), copy.getParents().get(0).getId());
    }

    @Test
    void copyDocumentWithNewName() throws UnsupportedEncodingException {
        Folder destination = createFolderWithName("Destination");
        Document document = createTextDocument(createSession().getRootFolder(), "This is new test document", "document.txt");

        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_DOCUMENT);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.COPY_DOCUMENT);
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "renamedDocument.txt");
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, document.getId());
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID, destination.getId());

        template.send(exchange);

        Document copy = exchange.getMessage().getBody(Document.class);

        assertNotNull(copy);
        assertNotEquals(document.getName(), copy.getName());
        assertEquals(document.getName(), "document.txt");
        assertEquals(copy.getName(), "renamedDocument.txt");
        assertEquals(document.getContentStreamLength(), copy.getContentStreamLength());
        assertEquals(destination.getId(), copy.getParents().get(0).getId());
    }

    @Test
    void copyFolder() {
        Folder folder = createFolderWithName("Folder");
        Folder destination = createFolderWithName("Destination Folder");

        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, CamelCMISConstants.CMIS_FOLDER);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_ACTION, CamelCMISActions.COPY_FOLDER);
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_OBJECT_ID, folder.getId());
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_DESTIONATION_FOLDER_ID, destination.getId());

        template.send(exchange);

        Map<String, CmisObject> copiedFolders = exchange.getMessage().getBody(HashMap.class);

        Folder copy = (Folder) createSession().getObject(copiedFolders.get(folder.getId()));
        assertEquals(folder.getName(), copy.getName());
        assertNotEquals(folder.getId(), copy.getId());
    }
}
