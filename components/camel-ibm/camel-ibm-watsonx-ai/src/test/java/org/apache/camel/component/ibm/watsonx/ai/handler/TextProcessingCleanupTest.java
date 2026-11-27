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

import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationParameters;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionParameters;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiComponent;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConfiguration;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SDK deletes the document of an AndFetch operation when removeUploadedFile is set, even when the route did not
 * upload it: only the operations that upload the document ask for its removal.
 */
class TextProcessingCleanupTest {

    private static WatsonxAiEndpoint endpoint(CamelContext context) {
        WatsonxAiConfiguration configuration = new WatsonxAiConfiguration();
        configuration.setRemoveUploadedFile(true);
        configuration.setRemoveOutputFile(true);
        WatsonxAiComponent component = new WatsonxAiComponent();
        component.setCamelContext(context);
        return new WatsonxAiEndpoint("ibm-watsonx-ai:test", component, configuration);
    }

    @Test
    void extractionRemovesOnlyTheDocumentItUploaded() {
        CamelContext context = new DefaultCamelContext();
        TextExtractionHandler handler = new TextExtractionHandler(endpoint(context));
        Exchange exchange = new DefaultExchange(context);

        TextExtractionParameters uploaded = handler.parameters(exchange, true, true);
        assertTrue(uploaded.isRemoveUploadedFile());
        assertTrue(uploaded.isRemoveOutputFile());

        TextExtractionParameters existing = handler.parameters(exchange, true, false);
        assertFalse(existing.isRemoveUploadedFile());
        assertTrue(existing.isRemoveOutputFile());

        // an asynchronous start takes no cleanup, which the SDK refuses
        assertNull(handler.parameters(exchange, false, true));
    }

    @Test
    void classificationRemovesOnlyTheDocumentItUploaded() {
        CamelContext context = new DefaultCamelContext();
        TextClassificationHandler handler = new TextClassificationHandler(endpoint(context));

        TextClassificationParameters uploaded = handler.parameters(true, true);
        assertTrue(uploaded.isRemoveUploadedFile());
        assertNull(handler.parameters(true, false));
        assertNull(handler.parameters(false, true));
    }
}
