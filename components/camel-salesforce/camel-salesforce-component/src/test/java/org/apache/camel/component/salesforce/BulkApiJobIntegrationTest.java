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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.api.dto.bulk.JobInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.JobStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulk.OperationEnum;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertSame;

public class BulkApiJobIntegrationTest extends AbstractBulkApiTestBase {

    @BeforeEach
    public void setupProfileWithHardDelete() throws IOException {
        final SalesforceLoginConfig loginConfig = LoginConfigHelper.getLoginConfig();

        template().requestBodyAndHeader("salesforce:apexCall/UpdateProfile?apexMethod=PATCH&sObjectClass=java.lang.String",
                null,
                SalesforceEndpointConfig.APEX_QUERY_PARAM_PREFIX + "username", loginConfig.getUserName());
    }

    @ParameterizedTest
    @MethodSource("getJobs")
    public void testJobLifecycle(JobInfo jobInfo, String operationName) throws Exception {
        log.info("Testing Job lifecycle for {} of type {}", jobInfo.getOperation(), jobInfo.getContentType());

        // test create
        jobInfo = createJob(jobInfo);

        // test get
        jobInfo = template().requestBody("direct:getJob", jobInfo, JobInfo.class);
        assertSame(JobStateEnum.OPEN, jobInfo.getState(), "Job should be OPEN");

        // test close
        jobInfo = template().requestBody("direct:closeJob", jobInfo, JobInfo.class);
        assertSame(JobStateEnum.CLOSED, jobInfo.getState(), "Job should be CLOSED");

        // test abort
        jobInfo = template().requestBody("direct:abortJob", jobInfo, JobInfo.class);
        assertSame(JobStateEnum.ABORTED, jobInfo.getState(), "Job should be ABORTED");
    }

    // test jobs for testJobLifecycle
    public static Iterable<Object[]> getJobs() {
        final List<JobInfo> result = new ArrayList<>();

        // insert XML
        final JobInfo insertXml = new JobInfo();
        insertXml.setObject(Merchandise__c.class.getSimpleName());
        insertXml.setContentType(ContentType.XML);
        insertXml.setOperation(OperationEnum.INSERT);
        result.add(insertXml);

        // insert CSV
        JobInfo insertCsv = new JobInfo();
        insertCsv = new JobInfo();
        insertCsv.setObject(Merchandise__c.class.getSimpleName());
        insertCsv.setContentType(ContentType.CSV);
        insertCsv.setOperation(OperationEnum.INSERT);
        result.add(insertCsv);

        // update CSV
        final JobInfo updateCsv = new JobInfo();
        updateCsv.setObject(Merchandise__c.class.getSimpleName());
        updateCsv.setContentType(ContentType.CSV);
        updateCsv.setOperation(OperationEnum.UPDATE);
        result.add(updateCsv);

        // upsert CSV
        final JobInfo upsertCsv = new JobInfo();
        upsertCsv.setObject(Merchandise__c.class.getSimpleName());
        upsertCsv.setContentType(ContentType.CSV);
        upsertCsv.setOperation(OperationEnum.UPSERT);
        upsertCsv.setExternalIdFieldName("Name");
        result.add(upsertCsv);

        // delete CSV
        final JobInfo deleteCsv = new JobInfo();
        deleteCsv.setObject(Merchandise__c.class.getSimpleName());
        deleteCsv.setContentType(ContentType.CSV);
        deleteCsv.setOperation(OperationEnum.DELETE);
        result.add(deleteCsv);

        // hard delete CSV
        final JobInfo hardDeleteCsv = new JobInfo();
        hardDeleteCsv.setObject(Merchandise__c.class.getSimpleName());
        hardDeleteCsv.setContentType(ContentType.CSV);
        hardDeleteCsv.setOperation(OperationEnum.HARD_DELETE);
        result.add(hardDeleteCsv);

        // query CSV
        final JobInfo queryCsv = new JobInfo();
        queryCsv.setObject(Merchandise__c.class.getSimpleName());
        queryCsv.setContentType(ContentType.CSV);
        queryCsv.setOperation(OperationEnum.QUERY);
        result.add(queryCsv);

        return result.stream().map(j -> new Object[] { j, j.getOperation().name() }).collect(Collectors.toList());
    }
}
