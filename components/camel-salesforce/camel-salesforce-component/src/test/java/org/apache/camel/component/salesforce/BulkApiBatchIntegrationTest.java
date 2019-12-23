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
package org.apache.camel.component.salesforce;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.salesforce.api.dto.bulk.BatchInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.BatchStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.api.dto.bulk.JobInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.OperationEnum;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theory;

public class BulkApiBatchIntegrationTest extends AbstractBulkApiTestBase {

    private static final String TEST_REQUEST_XML = "/test-request.xml";
    private static final String TEST_REQUEST_CSV = "/test-request.csv";

    @DataPoints
    public static BatchTest[] getBatches() {
        List<BatchTest> result = new ArrayList<>();
        BatchTest test = new BatchTest();
        test.contentType = ContentType.XML;
        test.stream = BulkApiBatchIntegrationTest.class.getResourceAsStream(TEST_REQUEST_XML);
        result.add(test);

        test = new BatchTest();
        test.contentType = ContentType.CSV;
        test.stream = BulkApiBatchIntegrationTest.class.getResourceAsStream(TEST_REQUEST_CSV);
        result.add(test);

        // TODO test ZIP_XML and ZIP_CSV
        return result.toArray(new BatchTest[result.size()]);
    }

    @Theory
    @Test
    public void testBatchLifecycle(BatchTest request) throws Exception {
        log.info("Testing Batch lifecycle with {} content", request.contentType);

        // create an UPSERT test Job for this batch request
        JobInfo jobInfo = new JobInfo();
        jobInfo.setOperation(OperationEnum.UPSERT);
        jobInfo.setContentType(request.contentType);
        jobInfo.setObject(Merchandise__c.class.getSimpleName());
        jobInfo.setExternalIdFieldName("Name");
        jobInfo = createJob(jobInfo);

        // test createBatch
        Map<String, Object> headers = new HashMap<>();
        headers.put(SalesforceEndpointConfig.JOB_ID, jobInfo.getId());
        headers.put(SalesforceEndpointConfig.CONTENT_TYPE, jobInfo.getContentType());
        BatchInfo batchInfo = template().requestBodyAndHeaders("direct:createBatch", request.stream, headers, BatchInfo.class);
        assertNotNull("Null batch", batchInfo);
        assertNotNull("Null batch id", batchInfo.getId());

        // test getAllBatches
        @SuppressWarnings("unchecked")
        List<BatchInfo> batches = template().requestBody("direct:getAllBatches", jobInfo, List.class);
        assertNotNull("Null batches", batches);
        assertFalse("Empty batch list", batches.isEmpty());

        // test getBatch
        batchInfo = batches.get(0);
        batchInfo = getBatchInfo(batchInfo);

        // test getRequest
        InputStream requestStream = template().requestBody("direct:getRequest", batchInfo, InputStream.class);
        assertNotNull("Null batch request", requestStream);

        // wait for batch to finish
        log.info("Waiting for batch to finish...");
        while (!batchProcessed(batchInfo)) {
            // sleep 5 seconds
            Thread.sleep(5000);
            // check again
            batchInfo = getBatchInfo(batchInfo);
        }
        log.info("Batch finished with state " + batchInfo.getState());
        assertEquals("Batch did not succeed", BatchStateEnum.COMPLETED, batchInfo.getState());

        // test getResults
        InputStream results = template().requestBody("direct:getResults", batchInfo, InputStream.class);
        assertNotNull("Null batch results", results);

        // close the test job
        template().requestBody("direct:closeJob", jobInfo, JobInfo.class);
    }

    private static class BatchTest {
        public InputStream stream;
        public ContentType contentType;
    }
}
