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
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.bulkv2.Job;
import org.apache.camel.component.salesforce.api.dto.bulkv2.JobStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulkv2.Jobs;
import org.apache.camel.component.salesforce.api.dto.bulkv2.OperationEnum;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("BusyWait")
public class BulkApiV2IngestJobManualIT extends AbstractSalesforceTestBase {

    @Test
    public void testLifecycle() throws Exception {
        Job job = new Job();
        job.setObject("Contact");
        job.setOperation(OperationEnum.INSERT);

        job = template().requestBody("salesforce:bulk2CreateJob", job, Job.class);
        assertNotNull(job.getId(), "JobId");

        job = template().requestBody("salesforce:bulk2GetJob", job, Job.class);
        assertSame(JobStateEnum.OPEN, job.getState(), "Job state");

        Exchange exchange = new DefaultExchange(context());
        exchange.getIn().setBody("FirstName,LastName\nTestFirst,TestLast");
        exchange.getIn().setHeader("jobId", job.getId());
        template.send("salesforce:bulk2CreateBatch", exchange);
        assertNull(exchange.getException());

        job = template().requestBody("salesforce:bulk2GetJob", job, Job.class);
        assertSame(JobStateEnum.OPEN, job.getState(), "Job state");

        job = template().requestBodyAndHeader("salesforce:bulk2CloseJob", "", "jobId", job.getId(),
                Job.class);
        assertEquals(JobStateEnum.UPLOAD_COMPLETE, job.getState(), "Job state");

        // wait for job to finish
        while (job.getState() != JobStateEnum.JOB_COMPLETE) {
            Thread.sleep(2000);
            job = template().requestBodyAndHeader("salesforce:bulk2GetJob", "", "jobId",
                    job.getId(), Job.class);
        }

        InputStream is = template().requestBodyAndHeader("salesforce:bulk2GetSuccessfulResults",
                "", "jobId", job.getId(), InputStream.class);
        assertNotNull(is, "Successful results");
        List<String> successful = IOUtils.readLines(is, StandardCharsets.UTF_8);
        assertEquals(2, successful.size());
        assertTrue(successful.get(1).contains("TestFirst"));

        is = template().requestBodyAndHeader("salesforce:bulk2GetFailedResults",
                "", "jobId", job.getId(), InputStream.class);
        assertNotNull(is, "Failed results");
        List<String> failed = IOUtils.readLines(is, StandardCharsets.UTF_8);
        assertEquals(1, failed.size());

        is = template().requestBodyAndHeader("salesforce:bulk2GetUnprocessedRecords",
                "", "jobId", job.getId(), InputStream.class);
        assertNotNull(is, "Unprocessed records");
        List<String> unprocessed = IOUtils.readLines(is, StandardCharsets.UTF_8);
        assertEquals(1, unprocessed.size());
        assertEquals("FirstName,LastName", unprocessed.get(0));
    }

    @Test
    public void testAbort() {
        Job job = new Job();
        job.setObject("Contact");
        job.setOperation(OperationEnum.INSERT);
        job = createJob(job);

        job = template().requestBody("salesforce:bulk2GetJob", job, Job.class);
        assertSame(JobStateEnum.OPEN, job.getState(), "Job should be OPEN");

        template().sendBodyAndHeader("salesforce:bulk2AbortJob", "", "jobId", job.getId());

        job = template().requestBody("salesforce:bulk2GetJob", job, Job.class);
        assertSame(JobStateEnum.ABORTED, job.getState(), "Job state");
    }

    @Test
    public void testDelete() {
        Job job = new Job();
        job.setObject("Contact");
        job.setOperation(OperationEnum.INSERT);
        job = createJob(job);

        job = template().requestBody("salesforce:bulk2GetJob", job, Job.class);
        assertSame(JobStateEnum.OPEN, job.getState(), "Job should be OPEN");

        template().sendBodyAndHeader("salesforce:bulk2AbortJob", "", "jobId", job.getId());

        job = template().requestBody("salesforce:bulk2GetJob", job, Job.class);
        assertSame(JobStateEnum.ABORTED, job.getState(), "Job state");

        template().sendBodyAndHeader("salesforce:bulk2DeleteJob", "", "jobId", job.getId());

        final Job finalJob = job;
        CamelExecutionException ex = Assertions.assertThrows(CamelExecutionException.class,
                () -> template().requestBody("salesforce:bulk2GetJob", finalJob, Job.class));
        assertEquals(SalesforceException.class, ex.getCause().getClass());
        SalesforceException sfEx = (SalesforceException) ex.getCause();
        assertEquals(404, sfEx.getStatusCode());
    }

    @Test
    public void testGetAll() {
        Jobs jobs = template().requestBody("salesforce:bulk2GetAllJobs", "", Jobs.class);
        assertNotNull(jobs);
    }

    private Job createJob(Job job) {
        job = template().requestBody("salesforce:bulk2CreateJob", job, Job.class);
        assertNotNull(job.getId(), "Missing JobId");
        return job;
    }
}
