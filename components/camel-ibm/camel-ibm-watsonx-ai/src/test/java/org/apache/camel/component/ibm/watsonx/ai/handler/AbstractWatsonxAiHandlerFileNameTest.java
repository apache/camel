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
package org.apache.camel.component.ibm.watsonx.ai.handler;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests how {@link AbstractWatsonxAiHandler#resolveFileName(Message)} picks the file name of a streamed input from the
 * headers that the file, S3 and Azure Blob consumers set.
 */
class AbstractWatsonxAiHandlerFileNameTest {

    private CamelContext context;
    private AbstractWatsonxAiHandler handler;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        WatsonxAiEndpoint endpoint = context.getEndpoint(
                "ibm-watsonx-ai:myLabel?apiKey=test-key&baseUrl=https://test.example.com&modelId=test-model&projectId=test-project&operation=textGeneration",
                WatsonxAiEndpoint.class);
        handler = new AbstractWatsonxAiHandler(endpoint) {
            @Override
            public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) {
                throw new UnsupportedOperationException();
            }

            @Override
            public WatsonxAiOperations[] getSupportedOperations() {
                return new WatsonxAiOperations[0];
            }
        };
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private Message message() {
        return new DefaultExchange(context).getIn();
    }

    @Test
    void explicitFileNameHeaderWins() {
        Message in = message();
        in.setHeader(WatsonxAiConstants.FILE_NAME, "explicit.pdf");
        in.setHeader(AbstractWatsonxAiHandler.AWS_S3_KEY_HEADER, "docs/from-s3.pdf");

        assertEquals("explicit.pdf", handler.resolveFileName(in));
    }

    @Test
    void usesTheS3ObjectKey() {
        Message in = message();
        in.setHeader("CamelAwsS3Key", "docs/2024/invoice.pdf");

        assertEquals("invoice.pdf", handler.resolveFileName(in));
    }

    @Test
    void usesTheAzureBlobName() {
        Message in = message();
        // the header camel-azure-storage-blob sets: BlobConstants.BLOB_NAME
        in.setHeader("CamelAzureStorageBlobBlobName", "documents/report.pdf");

        assertEquals("report.pdf", handler.resolveFileName(in));
    }

    @Test
    void fallsBackToCamelFileName() {
        Message in = message();
        in.setHeader(Exchange.FILE_NAME, "incoming/notes.txt");

        assertEquals("notes.txt", handler.resolveFileName(in));
    }

    @Test
    void failsWhenNoHeaderNamesTheFile() {
        Message in = message();

        assertThrows(IllegalArgumentException.class, () -> handler.resolveFileName(in));
    }
}
