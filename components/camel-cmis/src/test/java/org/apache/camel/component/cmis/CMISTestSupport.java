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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.Repository;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.UnfileObject;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public class CMISTestSupport extends CamelTestSupport {
    protected static final String CMIS_ENDPOINT_TEST_SERVER
        = "http://localhost:%s/chemistry-opencmis-server-inmemory/atom11";
    protected static final String OPEN_CMIS_SERVER_WAR_PATH
        = "target/dependency/chemistry-opencmis-server-inmemory.war";

    protected static Server cmisServer;
    protected static int port;

    protected String getUrl() {
        return String.format(CMIS_ENDPOINT_TEST_SERVER, port);
    }

    protected Exchange createExchangeWithInBody(String body) {
        DefaultExchange exchange = new DefaultExchange(context);
        if (body != null) {
            exchange.getIn().setBody(body);
        }
        return exchange;
    }

    protected CmisObject retrieveCMISObjectByIdFromServer(String nodeId) throws Exception {
        Session session = createSession();
        return session.getObject(nodeId);
    }

    protected void deleteAllContent() {
        Session session = createSession();
        Folder rootFolder = session.getRootFolder();
        ItemIterable<CmisObject> children = rootFolder.getChildren();
        for (CmisObject cmisObject : children) {
            if ("cmis:folder".equals(cmisObject.getPropertyValue(PropertyIds.OBJECT_TYPE_ID))) {
                List<String> notDeltedIdList = ((Folder)cmisObject)
                        .deleteTree(true, UnfileObject.DELETE, true);
                if (notDeltedIdList != null && notDeltedIdList.size() > 0) {
                    throw new RuntimeException("Cannot empty repo");
                }
            } else {
                cmisObject.delete(true);
            }
        }
        session.getBinding().close();
    }

    protected Session createSession() {
        SessionFactory sessionFactory = SessionFactoryImpl.newInstance();
        Map<String, String> parameter = new HashMap<String, String>();
        parameter.put(SessionParameter.ATOMPUB_URL, getUrl());
        parameter.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());

        Repository repository = sessionFactory.getRepositories(parameter).get(0);
        return repository.createSession();
    }

    protected String getDocumentContentAsString(String nodeId) throws Exception {
        CmisObject cmisObject = retrieveCMISObjectByIdFromServer(nodeId);
        Document doc = (Document)cmisObject;
        InputStream inputStream = doc.getContentStream().getStream();
        return readFromStream(inputStream);
    }

    protected String readFromStream(InputStream in) throws Exception {
        StringBuilder result = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        String strLine;
        while ((strLine = br.readLine()) != null) {
            result.append(strLine);
        }
        in.close();
        return result.toString();
    }

    protected Folder createFolderWithName(String folderName) {
        Folder rootFolder = createSession().getRootFolder();
        return createChildFolderWithName(rootFolder, folderName);
    }

    protected Folder createChildFolderWithName(Folder parent, String childName) {
        Map<String, String> newFolderProps = new HashMap<String, String>();
        newFolderProps.put(PropertyIds.OBJECT_TYPE_ID, "cmis:folder");
        newFolderProps.put(PropertyIds.NAME, childName);
        return parent.createFolder(newFolderProps);
    }

    protected void createTextDocument(Folder newFolder, String content, String fileName)
        throws UnsupportedEncodingException {
        byte[] buf = content.getBytes("UTF-8");
        ByteArrayInputStream input = new ByteArrayInputStream(buf);
        ContentStream contentStream = createSession().getObjectFactory()
                .createContentStream(fileName, buf.length, "text/plain; charset=UTF-8", input);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
        properties.put(PropertyIds.NAME, fileName);
        newFolder.createDocument(properties, contentStream, VersioningState.NONE);
    }

    @BeforeClass
    public static void startServer() throws Exception {
        port = AvailablePortFinder.getNextAvailable(26500);
        cmisServer = new Server(port);
        cmisServer.setHandler(new WebAppContext(OPEN_CMIS_SERVER_WAR_PATH, "/chemistry-opencmis-server-inmemory"));
        cmisServer.start();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        cmisServer.stop();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        deleteAllContent();
        super.setUp();
    }

}
