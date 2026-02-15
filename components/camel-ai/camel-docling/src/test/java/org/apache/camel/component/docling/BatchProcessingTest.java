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
package org.apache.camel.component.docling;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchProcessingTest extends CamelTestSupport {

    @Test
    public void testBatchProcessingResultsCreation() {
        BatchProcessingResults results = new BatchProcessingResults();

        BatchConversionResult result1 = new BatchConversionResult("doc-1", "/path/doc1.pdf");
        result1.setSuccess(true);
        result1.setResult("Converted content 1");
        result1.setProcessingTimeMs(1000);

        BatchConversionResult result2 = new BatchConversionResult("doc-2", "/path/doc2.pdf");
        result2.setSuccess(true);
        result2.setResult("Converted content 2");
        result2.setProcessingTimeMs(1500);

        results.addResult(result1);
        results.addResult(result2);

        assertEquals(2, results.getTotalDocuments());
        assertEquals(2, results.getSuccessCount());
        assertEquals(0, results.getFailureCount());
        assertTrue(results.isAllSuccessful());
        assertEquals(100.0, results.getSuccessRate());
    }

    @Test
    public void testBatchProcessingWithFailures() {
        BatchProcessingResults results = new BatchProcessingResults();

        BatchConversionResult result1 = new BatchConversionResult("doc-1", "/path/doc1.pdf");
        result1.setSuccess(true);
        result1.setResult("Converted content 1");

        BatchConversionResult result2 = new BatchConversionResult("doc-2", "/path/doc2.pdf");
        result2.setSuccess(false);
        result2.setErrorMessage("File not found");

        BatchConversionResult result3 = new BatchConversionResult("doc-3", "/path/doc3.pdf");
        result3.setSuccess(true);
        result3.setResult("Converted content 3");

        results.addResult(result1);
        results.addResult(result2);
        results.addResult(result3);

        assertEquals(3, results.getTotalDocuments());
        assertEquals(2, results.getSuccessCount());
        assertEquals(1, results.getFailureCount());
        assertTrue(results.hasAnySuccessful());
        assertTrue(results.hasAnyFailures());
        assertEquals(66.67, results.getSuccessRate(), 0.01);

        List<BatchConversionResult> successful = results.getSuccessful();
        assertEquals(2, successful.size());

        List<BatchConversionResult> failed = results.getFailed();
        assertEquals(1, failed.size());
        assertEquals("doc-2", failed.get(0).getDocumentId());
    }

    @Test
    public void testBatchConversionResultProperties() {
        BatchConversionResult result = new BatchConversionResult("test-doc", "/path/test.pdf");
        result.setSuccess(true);
        result.setResult("Converted content");
        result.setProcessingTimeMs(2000);
        result.setBatchIndex(5);

        assertEquals("test-doc", result.getDocumentId());
        assertEquals("/path/test.pdf", result.getOriginalPath());
        assertTrue(result.isSuccess());
        assertEquals("Converted content", result.getResult());
        assertEquals(2000, result.getProcessingTimeMs());
        assertEquals(5, result.getBatchIndex());
    }

    @Test
    public void testBatchOperationsEnumExists() {
        // Verify all batch operations are defined
        assertNotNull(DoclingOperations.BATCH_CONVERT_TO_MARKDOWN);
        assertNotNull(DoclingOperations.BATCH_CONVERT_TO_HTML);
        assertNotNull(DoclingOperations.BATCH_CONVERT_TO_JSON);
        assertNotNull(DoclingOperations.BATCH_EXTRACT_TEXT);
        assertNotNull(DoclingOperations.BATCH_EXTRACT_STRUCTURED_DATA);
    }

    @Test
    public void testBatchConfigurationDefaults() {
        DoclingConfiguration config = new DoclingConfiguration();

        assertEquals(10, config.getBatchSize());
        assertEquals(4, config.getBatchParallelism());
        assertEquals(300000, config.getBatchTimeout());
        assertTrue(config.isBatchFailOnFirstError());
        assertEquals(false, config.isSplitBatchResults());
    }

    @Test
    public void testBatchConfigurationSetters() {
        DoclingConfiguration config = new DoclingConfiguration();

        config.setBatchSize(20);
        config.setBatchParallelism(8);
        config.setBatchTimeout(600000);
        config.setBatchFailOnFirstError(false);
        config.setSplitBatchResults(true);

        assertEquals(20, config.getBatchSize());
        assertEquals(8, config.getBatchParallelism());
        assertEquals(600000, config.getBatchTimeout());
        assertEquals(false, config.isBatchFailOnFirstError());
        assertTrue(config.isSplitBatchResults());
    }

    @Test
    public void testBatchTimeoutConfiguration() {
        DoclingConfiguration config = new DoclingConfiguration();

        // Test default timeout
        assertEquals(300000, config.getBatchTimeout());

        // Test custom timeout
        config.setBatchTimeout(120000);
        assertEquals(120000, config.getBatchTimeout());

        // Test minimum reasonable timeout
        config.setBatchTimeout(5000);
        assertEquals(5000, config.getBatchTimeout());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Placeholder route for test infrastructure
                from("direct:batch-test")
                        .log("Batch test route");
            }
        };
    }

}
