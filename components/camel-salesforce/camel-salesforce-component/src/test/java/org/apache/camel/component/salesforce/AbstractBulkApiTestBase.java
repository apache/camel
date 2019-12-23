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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.bulk.BatchInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.BatchStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulk.JobInfo;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public abstract class AbstractBulkApiTestBase extends AbstractSalesforceTestBase {

    protected JobInfo createJob(JobInfo jobInfo) {
        jobInfo = template().requestBody("direct:createJob", jobInfo, JobInfo.class);
        assertNotNull("Missing JobId", jobInfo.getId());
        return jobInfo;
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // test createJob
                from("direct:createJob").to("salesforce://createJob");

                // test getJob
                from("direct:getJob").to("salesforce:getJob");

                // test closeJob
                from("direct:closeJob").to("salesforce:closeJob");

                // test abortJob
                from("direct:abortJob").to("salesforce:abortJob");

                // test createBatch
                from("direct:createBatch").to("salesforce:createBatch");

                // test getBatch
                from("direct:getBatch").to("salesforce:getBatch");

                // test getAllBatches
                from("direct:getAllBatches").to("salesforce:getAllBatches");

                // test getRequest
                from("direct:getRequest").to("salesforce:getRequest");

                // test getResults
                from("direct:getResults").to("salesforce:getResults");

                // test createBatchQuery
                from("direct:createBatchQuery")
                    .to("salesforce:createBatchQuery?sObjectQuery=SELECT Name, Description__c, Price__c, Total_Inventory__c FROM Merchandise__c WHERE Name LIKE '%25Bulk API%25'");

                // test getQueryResultIds
                from("direct:getQueryResultIds").to("salesforce:getQueryResultIds");

                // test getQueryResult
                from("direct:getQueryResult").to("salesforce:getQueryResult");

            }
        };
    }

    protected boolean batchProcessed(BatchInfo batchInfo) {
        BatchStateEnum state = batchInfo.getState();
        return !(state == BatchStateEnum.QUEUED || state == BatchStateEnum.IN_PROGRESS);
    }

    protected BatchInfo getBatchInfo(BatchInfo batchInfo) {
        batchInfo = template().requestBody("direct:getBatch", batchInfo, BatchInfo.class);

        assertNotNull("Null batch", batchInfo);
        assertNotNull("Null batch id", batchInfo.getId());

        return batchInfo;
    }
}
