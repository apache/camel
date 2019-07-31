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
package org.apache.camel.component.spring.batch.support;

import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

public class CamelJobExecutionListener implements JobExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(CamelJobExecutionListener.class);

    private final ProducerTemplate producerTemplate;

    private final String endpointUri;

    public CamelJobExecutionListener(ProducerTemplate producerTemplate, String endpointUri) {
        this.producerTemplate = producerTemplate;
        this.endpointUri = endpointUri;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LOG.debug("sending before job execution event [{}]...", jobExecution);
        producerTemplate.sendBodyAndHeader(endpointUri, jobExecution, EventType.HEADER_KEY, EventType.BEFORE.name());
        LOG.debug("sent before job execution event");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        LOG.debug("sending after job execution event [{}]...", jobExecution);
        producerTemplate.sendBodyAndHeader(endpointUri, jobExecution, EventType.HEADER_KEY, EventType.AFTER.name());
        LOG.debug("sent after job execution event");
    }

    public enum EventType {

        BEFORE, AFTER;

        public static final String HEADER_KEY = "SPRING_BATCH_JOB_EVENT_TYPE";

    }

}
