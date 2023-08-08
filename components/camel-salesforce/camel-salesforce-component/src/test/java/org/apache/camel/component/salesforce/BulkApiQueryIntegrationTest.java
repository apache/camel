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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.salesforce.api.dto.bulk.BatchInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.BatchStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.api.dto.bulk.JobInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.OperationEnum;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BulkApiQueryIntegrationTest extends AbstractBulkApiTestBase {

    @ParameterizedTest
    @EnumSource(names = { "XML", "CSV" })
    public void testQueryLifecycle(ContentType contentType) throws Exception {
        log.info("Testing Query lifecycle with {} content", contentType);

        // create a QUERY test Job
        JobInfo jobInfo = new JobInfo();
        jobInfo.setOperation(OperationEnum.QUERY);
        jobInfo.setContentType(contentType);
        jobInfo.setObject(Merchandise__c.class.getSimpleName());
        jobInfo = createJob(jobInfo);

        // test createQuery
        BatchInfo batchInfo = template().requestBody("direct:createBatchQuery", jobInfo, BatchInfo.class);
        assertNotNull(batchInfo, "Null batch query");
        assertNotNull(batchInfo.getId(), "Null batch query id");

        // test getRequest
        InputStream requestStream = template().requestBody("direct:getRequest", batchInfo, InputStream.class);
        assertNotNull(requestStream, "Null batch request");

        // wait for batch to finish
        log.info("Waiting for query batch to finish...");
        while (!batchProcessed(batchInfo)) {
            // sleep 5 seconds
            Thread.sleep(5000);
            // check again
            batchInfo = getBatchInfo(batchInfo);
        }
        log.info("Query finished with state {}", batchInfo.getState());
        assertEquals(BatchStateEnum.COMPLETED, batchInfo.getState(), "Query did not succeed");

        // test getQueryResultList
        @SuppressWarnings("unchecked")
        List<String> resultIds = template().requestBody("direct:getQueryResultIds", batchInfo, List.class);
        assertNotNull(resultIds, "Null query result ids");
        assertFalse(resultIds.isEmpty(), "Empty result ids");

        // test getQueryResult
        for (String resultId : resultIds) {
            InputStream results = template().requestBodyAndHeader("direct:getQueryResult", batchInfo,
                    SalesforceEndpointConfig.RESULT_ID, resultId, InputStream.class);
            assertNotNull(results, "Null query result");
        }

        // close the test job
        template().requestBody("direct:closeJob", jobInfo, JobInfo.class);
    }

    @Test
    public void testPkChunking() throws Exception {
        // create a QUERY test Job
        JobInfo jobInfo = new JobInfo();
        jobInfo.setOperation(OperationEnum.QUERY);
        jobInfo.setContentType(ContentType.CSV);
        jobInfo.setObject(Merchandise__c.class.getSimpleName());
        Map<String, Object> headers = new HashMap<>();
        headers.put(SalesforceEndpointConfig.PK_CHUNKING, true);
        headers.put(SalesforceEndpointConfig.PK_CHUNKING_CHUNK_SIZE, 1000);
        jobInfo = template().requestBodyAndHeaders(
                "direct:createJob", jobInfo, headers, JobInfo.class);
        assertNotNull(jobInfo.getId(), "Missing JobId");

        // test createQuery
        BatchInfo batchInfo = template().requestBody("direct:createBatchQuery", jobInfo, BatchInfo.class);
        assertNotNull(batchInfo, "Null batch query");
        assertNotNull(batchInfo.getId(), "Null batch query id");

        // test getRequest
        InputStream requestStream = template().requestBody("direct:getRequest", batchInfo, InputStream.class);
        assertNotNull(requestStream, "Null batch request");

        // wait for batch to finish
        log.info("Waiting for query batch to finish...");
        while (!batchProcessed(batchInfo)) {
            // sleep 5 seconds
            Thread.sleep(5000);
            // check again
            batchInfo = getBatchInfo(batchInfo);
        }
        log.info("Query finished with state {}", batchInfo.getState());
        // Because PK chunking is enabled, the original batch is given a state of Not Processed.
        assertEquals(BatchStateEnum.NOT_PROCESSED, batchInfo.getState(), "Query did not succeed");

        // close the test job
        template().requestBody("direct:closeJob", jobInfo, JobInfo.class);
    }
}
