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
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.junit.Before;
import org.junit.Test;

public class CMISConsumerTest extends CMISTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Test
    public void getAllContentFromServerOrderedFromRootToLeaves() throws Exception {
        resultEndpoint.expectedMessageCount(5);

        Consumer treeBasedConsumer = createConsumerFor(getUrl() + "?pageSize=50");
        treeBasedConsumer.start();

        resultEndpoint.assertIsSatisfied();
        treeBasedConsumer.stop();

        List<Exchange> exchanges = resultEndpoint.getExchanges();
        assertTrue(getNodeNameForIndex(exchanges, 0).equals("RootFolder"));
        assertTrue(getNodeNameForIndex(exchanges, 1).equals("Folder1"));
        assertTrue(getNodeNameForIndex(exchanges, 2).equals("Folder2"));
        assertTrue(getNodeNameForIndex(exchanges, 3).contains(".txt"));
        assertTrue(getNodeNameForIndex(exchanges, 4).contains(".txt"));
    }

    @Test
    public void consumeDocumentsWithQuery() throws Exception {
        resultEndpoint.expectedMessageCount(2);

        Consumer queryBasedConsumer = createConsumerFor(
                getUrl() + "?query=SELECT * FROM cmis:document");
        queryBasedConsumer.start();
        resultEndpoint.assertIsSatisfied();
        queryBasedConsumer.stop();
    }

    private Consumer createConsumerFor(String path) throws Exception {
        Endpoint endpoint = context.getEndpoint("cmis://" + path);
        return endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                template.send("mock:result", exchange);
            }
        });
    }

    private String getNodeNameForIndex(List<Exchange> exchanges, int index) {
        return exchanges.get(index).getIn().getHeader("cmis:name", String.class);
    }

    private void populateRepositoryRootFolderWithTwoFoldersAndTwoDocuments()
        throws UnsupportedEncodingException {
        Folder folder1 = createFolderWithName("Folder1");
        Folder folder2 = createChildFolderWithName(folder1, "Folder2");
        createTextDocument(folder2, "Document2.1", "2.1.txt");
        createTextDocument(folder2, "Document2.2", "2.2.txt");
        //L0              ROOT
        //                |
        //L1            Folder1
        //L2              |_____Folder2
        //                        ||
        //L3            Doc2.1___||___Doc2.2
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        populateRepositoryRootFolderWithTwoFoldersAndTwoDocuments();
    }
}
