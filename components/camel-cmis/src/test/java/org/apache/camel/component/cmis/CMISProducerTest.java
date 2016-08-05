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

import java.util.Arrays;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.Produce;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.junit.Test;

public class CMISProducerTest extends CMISTestSupport {

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Test
    public void storeMessageBodyAsTextDocument() throws Exception {
        String content = "Some content to be store";
        Exchange exchange = createExchangeWithInBody(content);
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain; charset=UTF-8");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.file");

        template.send(exchange);

        String newNodeId = exchange.getOut().getBody(String.class);
        assertNotNull(newNodeId);

        String newNodeContent = getDocumentContentAsString(newNodeId);
        assertEquals(content, newNodeContent);
    }

    @Test
    public void getDocumentMimeTypeFromMessageContentType() throws Exception {
        Exchange exchange = createExchangeWithInBody("Some content to be store");
        exchange.getIn().getHeaders().put(Exchange.CONTENT_TYPE, "text/plain");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.file");

        template.send(exchange);
        String newNodeId = exchange.getOut().getBody(String.class);

        CmisObject cmisObject = retrieveCMISObjectByIdFromServer(newNodeId);
        Document doc = (Document) cmisObject;
        assertEquals("text/plain", doc.getPropertyValue(PropertyIds.CONTENT_STREAM_MIME_TYPE));
    }

    @Test
    public void namePropertyIsAlwaysRequired() {
        Exchange exchange = createExchangeWithInBody("Some content that will fail to be stored");
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain; charset=UTF-8");

        template.send(exchange);
        Exception exception = exchange.getException();
        Object body = exchange.getOut().getBody();

        assertNull(body);
        assertTrue(exception instanceof NoSuchHeaderException);
    }

    @Test
    public void createDocumentWithoutContentByExplicitlySpecifyingObjectTypeHeader() throws Exception {
        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain; charset=UTF-8");
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.file");

        template.send(exchange);
        String newNodeId = exchange.getOut().getBody(String.class);
        assertNotNull(newNodeId);

        CmisObject cmisObject = retrieveCMISObjectByIdFromServer(newNodeId);
        Document doc = (Document) cmisObject;
        assertEquals("cmis:document", doc.getPropertyValue(PropertyIds.OBJECT_TYPE_ID));
    }

    @Test
    public void emptyBodyAndMissingObjectTypeHeaderCreatesFolderNode() throws Exception {
        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "testFolder");

        template.send(exchange);
        String newNodeId = exchange.getOut().getBody(String.class);
        assertNotNull(newNodeId);

        CmisObject newNode = retrieveCMISObjectByIdFromServer(newNodeId);
        assertEquals("cmis:folder", newNode.getType().getId());
        assertTrue(newNode instanceof Folder);
    }

    @Test
    public void cmisPropertiesAreStored() throws Exception {
        Exchange exchange = createExchangeWithInBody("Some content to be store");
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain; charset=UTF-8");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.txt");

        template.send(exchange);
        String newNodeId = exchange.getOut().getBody(String.class);
        CmisObject newNode = retrieveCMISObjectByIdFromServer(newNodeId);

        assertEquals("test.txt", newNode.getPropertyValue(PropertyIds.NAME));
        assertEquals("text/plain; charset=UTF-8",
                newNode.getPropertyValue(PropertyIds.CONTENT_STREAM_MIME_TYPE));
    }

    @Test
    public void cmisSecondaryTypePropertiesAreStored() throws Exception {

        List<String> secondaryTypes = Arrays.asList("MySecondaryType");

        Exchange exchange = createExchangeWithInBody("Some content to be store");
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain; charset=UTF-8");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.txt");
        exchange.getIn().getHeaders().put(PropertyIds.SECONDARY_OBJECT_TYPE_IDS, secondaryTypes);
        exchange.getIn().getHeaders().put("SecondaryStringProp", "secondaryTypePropValue");

        template.send(exchange);
        String newNodeId = exchange.getOut().getBody(String.class);
        CmisObject newNode = retrieveCMISObjectByIdFromServer(newNodeId);

        assertEquals(1, newNode.getSecondaryTypes().size());
        assertEquals("secondaryTypePropValue", newNode.getPropertyValue("SecondaryStringProp"));
    }

    @Test(expected = CmisInvalidArgumentException.class)
    public void failConnectingToNonExistingRepository() throws Exception {
        Endpoint endpoint = context.getEndpoint("cmis://" + getUrl()
                + "?username=admin&password=admin&repositoryId=NON_EXISTING_ID");
        Producer producer = endpoint.createProducer();

        Exchange exchange = createExchangeWithInBody("Some content to be store");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.txt");
        producer.process(exchange);
    }

    @Test
    public void createDocumentAtSpecificPath() throws Exception {
        Folder folder1 = createFolderWithName("Folder1");
        createChildFolderWithName(folder1, "Folder2");
        String existingFolderStructure = "/Folder1/Folder2";

        Exchange exchange = createExchangeWithInBody("Some content to be stored");
        exchange.getIn().getHeaders().put(PropertyIds.CONTENT_STREAM_MIME_TYPE, "text/plain; charset=UTF-8");
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "test.file");
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_FOLDER_PATH, existingFolderStructure);

        template.send(exchange);
        String newNodeId = exchange.getOut().getBody(String.class);

        Document document = (Document) retrieveCMISObjectByIdFromServer(newNodeId);
        String documentFullPath = document.getPaths().get(0);
        assertEquals(existingFolderStructure + "/test.file", documentFullPath);
    }

    @Test
    public void failCreatingFolderAtNonExistingPath() throws Exception {
        String existingFolderStructure = "/No/Path/Here";

        Exchange exchange = createExchangeWithInBody(null);
        exchange.getIn().getHeaders().put(PropertyIds.NAME, "folder1");
        exchange.getIn().getHeaders().put(PropertyIds.OBJECT_TYPE_ID, "cmis:folder");
        exchange.getIn().getHeaders().put(CamelCMISConstants.CMIS_FOLDER_PATH, existingFolderStructure);

        template.send(exchange);
        assertTrue(exchange.getException() instanceof RuntimeExchangeException);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("cmis://" + getUrl());
            }
        };
    }

}
