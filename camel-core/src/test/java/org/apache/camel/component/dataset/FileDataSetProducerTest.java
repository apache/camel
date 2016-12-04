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
package org.apache.camel.component.dataset;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * @version 
 */
public class FileDataSetProducerTest extends ContextTestSupport {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    protected FileDataSet dataSet;

    final String testPayload = String.format("Line 1%nLine 2%nLine 3%nLine 4%nLine 5%nLine 6%nLine 7%nLine 8%nLine 9%nLine 10%n");

    final String sourceUri = "direct://source";
    final String dataSetName = "foo";
    final String dataSetUri = "dataset://" + dataSetName;

    public void testDefaultListDataSet() throws Exception {
        template.sendBodyAndHeader(sourceUri, testPayload, Exchange.DATASET_INDEX, 0);

        assertMockEndpointsSatisfied();
    }

    public void testDefaultListDataSetWithSizeGreaterThanListSize() throws Exception {
        int messageCount = 20;
        dataSet.setSize(messageCount);

        getMockEndpoint(dataSetUri).expectedMessageCount(messageCount);

        for (int i = 0; i < messageCount; ++i) {
            template.sendBodyAndHeader(sourceUri, testPayload, Exchange.DATASET_INDEX, i);
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    public void setUp() throws Exception {
        File fileDataset = createFileDatasetWithSystemEndOfLine();
        dataSet = new FileDataSet(fileDataset);
        assertEquals("Unexpected DataSet size", 1, dataSet.getSize());
        super.setUp();
    }

    private File createFileDatasetWithSystemEndOfLine() throws IOException {
        tempFolder.create();
        File fileDataset = tempFolder.newFile("file-dataset-test.txt");
        Files.copy(new ByteArrayInputStream(testPayload.getBytes()), fileDataset.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return fileDataset;
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        context.bind(dataSetName, dataSet);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(sourceUri)
                        .to(dataSetUri);
            }
        };
    }
}
