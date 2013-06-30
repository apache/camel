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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.api.dto.bulk.JobInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.JobStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulk.OperationEnum;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theory;

public class BulkApiJobIntegrationTest extends AbstractBulkApiTestBase {

    // test jobs for testJobLifecycle
    @DataPoints
    public static JobInfo[] getJobs() {
        JobInfo jobInfo = new JobInfo();

        // insert XML
        jobInfo.setObject(Merchandise__c.class.getSimpleName());
        jobInfo.setContentType(ContentType.XML);
        jobInfo.setOperation(OperationEnum.INSERT);

        List<JobInfo> result = new ArrayList<JobInfo>();
        result.add(jobInfo);

        // insert CSV
        jobInfo = new JobInfo();
        jobInfo.setObject(Merchandise__c.class.getSimpleName());
        jobInfo.setContentType(ContentType.CSV);
        jobInfo.setOperation(OperationEnum.INSERT);
        result.add(jobInfo);

        // update CSV
        jobInfo = new JobInfo();
        jobInfo.setObject(Merchandise__c.class.getSimpleName());
        jobInfo.setContentType(ContentType.CSV);
        jobInfo.setOperation(OperationEnum.UPDATE);
        result.add(jobInfo);

        // upsert CSV
        jobInfo = new JobInfo();
        jobInfo.setObject(Merchandise__c.class.getSimpleName());
        jobInfo.setContentType(ContentType.CSV);
        jobInfo.setOperation(OperationEnum.UPSERT);
        jobInfo.setExternalIdFieldName("Name");
        result.add(jobInfo);

        // delete CSV
        jobInfo = new JobInfo();
        jobInfo.setObject(Merchandise__c.class.getSimpleName());
        jobInfo.setContentType(ContentType.CSV);
        jobInfo.setOperation(OperationEnum.DELETE);
        result.add(jobInfo);

        // hard delete CSV
        jobInfo = new JobInfo();
        jobInfo.setObject(Merchandise__c.class.getSimpleName());
        jobInfo.setContentType(ContentType.CSV);
        jobInfo.setOperation(OperationEnum.HARD_DELETE);
        result.add(jobInfo);

        // query CSV
        jobInfo = new JobInfo();
        jobInfo.setObject(Merchandise__c.class.getSimpleName());
        jobInfo.setContentType(ContentType.CSV);
        jobInfo.setOperation(OperationEnum.QUERY);
        result.add(jobInfo);

        return result.toArray(new JobInfo[result.size()]);
    }

    @Theory
    public void testJobLifecycle(JobInfo jobInfo) throws Exception {
        log.info("Testing Job lifecycle for {} of type {}", jobInfo.getOperation(), jobInfo.getContentType());

        // test create
        jobInfo = createJob(jobInfo);

        // test get
        jobInfo = template().requestBody("direct:getJob", jobInfo, JobInfo.class);
        assertSame("Job should be OPEN", JobStateEnum.OPEN, jobInfo.getState());

        // test close
        jobInfo = template().requestBody("direct:closeJob", jobInfo, JobInfo.class);
        assertSame("Job should be CLOSED", JobStateEnum.CLOSED, jobInfo.getState());

        // test abort
        jobInfo = template().requestBody("direct:abortJob", jobInfo, JobInfo.class);
        assertSame("Job should be ABORTED", JobStateEnum.ABORTED, jobInfo.getState());
    }

}
