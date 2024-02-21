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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.DeleteSObjectResult;
import org.apache.camel.component.salesforce.api.dto.SaveSObjectResult;
import org.apache.camel.component.salesforce.api.dto.bulkv2.JobStateEnum;
import org.apache.camel.component.salesforce.api.dto.bulkv2.OperationEnum;
import org.apache.camel.component.salesforce.api.dto.bulkv2.QueryJob;
import org.apache.camel.component.salesforce.api.dto.bulkv2.QueryJobs;
import org.apache.camel.component.salesforce.dto.generated.Contact;
import org.apache.camel.component.salesforce.dto.generated.QueryRecordsContact;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("BusyWait")
public class BulkApiV2QueryJobManualIT extends AbstractSalesforceTestBase {

    private static final String LAST_NAME = "CamelBulkTest";

    @BeforeEach
    void setup() {
        createContacts();
    }

    @AfterEach
    void teardown() {
        deleteContacts();
    }

    @Test
    public void testQueryLifecycle() throws Exception {
        QueryJob job = new QueryJob();
        job.setOperation(OperationEnum.QUERY);
        job.setQuery("SELECT Id, LastName FROM Contact");

        job = template().requestBody("salesforce:bulk2CreateQueryJob", job, QueryJob.class);
        assertNotNull(job.getId(), "JobId");

        job = template().requestBodyAndHeader("salesforce:bulk2GetQueryJob", "", "jobId",
                job.getId(), QueryJob.class);

        // wait for job to finish
        while (job.getState() != JobStateEnum.JOB_COMPLETE) {
            Thread.sleep(2000);
            job = template().requestBodyAndHeader("salesforce:bulk2GetQueryJob", "", "jobId",
                    job.getId(), QueryJob.class);
        }

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("jobId", job.getId());
        exchange.getIn().setHeader("maxRecords", 1);
        template().send("salesforce:bulk2GetQueryJobResults?maxRecords=1",
                exchange);
        InputStream is = exchange.getIn().getBody(InputStream.class);
        assertNotNull(is, "Query Job results");
        List<String> results = IOUtils.readLines(is, StandardCharsets.UTF_8);
        assertTrue(results.size() > 0, "Query Job results");
        assertTrue(exchange.getIn().getHeaders().containsKey("Sforce-Locator"));
        String locator = exchange.getIn().getHeader("Sforce-Locator", String.class);
        assertNotNull(locator);

        exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("jobId", job.getId());
        exchange.getIn().setHeader("locator", locator);
        template().send("salesforce:bulk2GetQueryJobResults",
                exchange);
        is = exchange.getIn().getBody(InputStream.class);
        assertNotNull(is, "Query Job results");
        results = IOUtils.readLines(is, StandardCharsets.UTF_8);
        assertTrue(results.size() > 0, "Query Job results");
        assertNotNull(exchange.getIn().getHeader("Sforce-Locator"));
    }

    @Test
    public void testQueryAllLifecycle() throws Exception {
        QueryJob job = new QueryJob();
        job.setOperation(OperationEnum.QUERY_ALL);
        job.setQuery("SELECT Id, LastName FROM Contact");

        job = template().requestBody("salesforce:bulk2CreateQueryJob", job, QueryJob.class);
        assertNotNull(job.getId(), "JobId");

        job = template().requestBodyAndHeader("salesforce:bulk2GetQueryJob", "", "jobId",
                job.getId(), QueryJob.class);

        // wait for job to finish
        while (job.getState() != JobStateEnum.JOB_COMPLETE) {
            Thread.sleep(2000);
            job = template().requestBodyAndHeader("salesforce:bulk2GetQueryJob", "", "jobId",
                    job.getId(), QueryJob.class);
        }

        InputStream is = template().requestBodyAndHeader("salesforce:bulk2GetQueryJobResults",
                "", "jobId", job.getId(), InputStream.class);
        assertNotNull(is, "Query Job results");
        List<String> results = IOUtils.readLines(is, StandardCharsets.UTF_8);
        assertTrue(results.size() > 0, "Query Job results");
    }

    @Test
    public void testAbort() {
        QueryJob job = new QueryJob();
        job.setOperation(OperationEnum.QUERY);
        job.setQuery("SELECT Id, LastName FROM Contact");

        job = template().requestBody("salesforce:bulk2CreateQueryJob", job, QueryJob.class);
        assertNotNull(job.getId(), "JobId");

        template().sendBodyAndHeader("salesforce:bulk2AbortQueryJob", "", "jobId", job.getId());

        job = template().requestBody("salesforce:bulk2GetQueryJob", job, QueryJob.class);
        assertTrue(job.getState() == JobStateEnum.ABORTED || job.getState() == JobStateEnum.FAILED,
                "Expected job to be aborted or failed.");
    }

    @Test
    public void testDelete() throws InterruptedException {
        QueryJob job = new QueryJob();
        job.setOperation(OperationEnum.QUERY);
        job.setQuery("SELECT Id, LastName FROM Contact");

        job = template().requestBody("salesforce:bulk2CreateQueryJob", job, QueryJob.class);
        assertNotNull(job.getId(), "JobId");

        job = template().requestBody("salesforce:bulk2GetQueryJob", job, QueryJob.class);
        int i = 0;
        while (job.getState() != JobStateEnum.JOB_COMPLETE) {
            i++;
            if (i == 100) {
                throw new IllegalStateException("Job failed to reach JOB_COMPLETE status.");
            }
            Thread.sleep(2000);
            job = template().requestBody("salesforce:bulk2GetQueryJob", job, QueryJob.class);
        }

        template().sendBodyAndHeader("salesforce:bulk2DeleteQueryJob", "", "jobId", job.getId());

        final QueryJob finalJob = job;
        CamelExecutionException ex = Assertions.assertThrows(CamelExecutionException.class,
                () -> template().requestBody("salesforce:bulk2GetQueryJob", finalJob, QueryJob.class));
        assertEquals(SalesforceException.class, ex.getCause().getClass());
        SalesforceException sfEx = (SalesforceException) ex.getCause();
        assertEquals(404, sfEx.getStatusCode());
    }

    @Test
    public void testGetAll() {
        QueryJobs jobs = template().requestBody("salesforce:bulk2GetAllQueryJobs", "",
                QueryJobs.class);
        assertNotNull(jobs);
    }

    private void createContacts() {
        List<Contact> contacts = new ArrayList<>(2);
        IntStream.range(0, 2).forEach(i -> {
            Contact c = new Contact();
            c.setLastName(LAST_NAME);
            c.setFirstName("TestFirst");
            contacts.add(c);
        });
        final List<SaveSObjectResult> results = (List<SaveSObjectResult>) template.requestBody(
                "salesforce:compositeCreateSObjectCollections", contacts);
        results.forEach(r -> assertTrue(r.getSuccess()));
    }

    private void deleteContacts() {
        final List<Contact> records
                = template.requestBody("salesforce:query?sObjectClass=" + QueryRecordsContact.class.getName(),
                        "SELECT Id FROM Contact WHERE LastName = '" + LAST_NAME + "'", QueryRecordsContact.class).getRecords();
        final List<DeleteSObjectResult> results
                = (List<DeleteSObjectResult>) template.requestBody("salesforce:compositeDeleteSObjectCollections",
                        records.stream().map(contact -> contact.getId()).collect(Collectors.toList()));
        results.forEach(r -> assertTrue(r.getSuccess()));
    }
}
