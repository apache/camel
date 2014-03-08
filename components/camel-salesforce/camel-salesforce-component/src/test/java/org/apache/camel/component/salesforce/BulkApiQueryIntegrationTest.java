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
package org.apache.camel.component.salesforce;

import java.io.InputStream;
import java.util.List;

import org.apache.camel.component.salesforce.api.dto.bulk.BatchInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.BatchStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.api.dto.bulk.JobInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.OperationEnum;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theory;

public class BulkApiQueryIntegrationTest extends AbstractBulkApiTestBase {

    @DataPoints
    public static ContentType[] getContentTypes() {
        return new ContentType[] {
            ContentType.XML,
            ContentType.CSV
        };
    }

    @Theory
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
        assertNotNull("Null batch query", batchInfo);
        assertNotNull("Null batch query id", batchInfo.getId());

        // test getRequest
        InputStream requestStream = template().requestBody("direct:getRequest", batchInfo, InputStream.class);
        assertNotNull("Null batch request", requestStream);

        // wait for batch to finish
        log.info("Waiting for query batch to finish...");
        while (!batchProcessed(batchInfo)) {
            // sleep 5 seconds
            Thread.sleep(5000);
            // check again
            batchInfo = getBatchInfo(batchInfo);
        }
        log.info("Query finished with state " + batchInfo.getState());
        assertEquals("Query did not succeed", BatchStateEnum.COMPLETED, batchInfo.getState());

        // test getQueryResultList
        @SuppressWarnings("unchecked")
        List<String> resultIds = template().requestBody("direct:getQueryResultIds", batchInfo, List.class);
        assertNotNull("Null query result ids", resultIds);
        assertFalse("Empty result ids", resultIds.isEmpty());

        // test getQueryResult
        for (String resultId : resultIds) {
            InputStream results = template().requestBodyAndHeader("direct:getQueryResult", batchInfo,
                SalesforceEndpointConfig.RESULT_ID, resultId, InputStream.class);
            assertNotNull("Null query result", results);
        }

        // close the test job
        template().requestBody("direct:closeJob", jobInfo, JobInfo.class);
    }

}