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
package org.apache.camel.component.braintree;

import java.io.File;

import com.braintreegateway.DocumentUpload;
import com.braintreegateway.DocumentUploadRequest;
import com.braintreegateway.Result;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.DocumentUploadGatewayApiMethod;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentUploadGatewayIntegrationTest extends AbstractBraintreeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentUploadGatewayIntegrationTest.class);
    private static final String PATH_PREFIX = BraintreeApiCollection.getCollection().getApiName(DocumentUploadGatewayApiMethod.class).getName();

    @Test
    public void testCreate() throws Exception {
        final String documentName = "pdf-sample.pdf";

        File evidenceDocument = new File(getClass().getClassLoader().getResource(documentName).getPath());
        DocumentUploadRequest documentUploadRequest = new DocumentUploadRequest(
                DocumentUpload.Kind.EVIDENCE_DOCUMENT,
                evidenceDocument
        );

        final Result<DocumentUpload> result = requestBody(
                "direct://CREATE",
                documentUploadRequest
        );

        assertNotNull("create result", result);
        assertTrue("create result success", result.isSuccess());

        DocumentUpload documentUpload = result.getTarget();
        assertEquals(documentName, documentUpload.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for create
                from("direct://CREATE")
                    .to("braintree://" + PATH_PREFIX + "/create?inBody=request");

            }
        };
    }
}
