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

package org.apache.camel.component.spring.batch;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;

@Component("spring-batch")
public class SpringBatchComponent extends DefaultComponent {

    @Metadata(autowired = true)
    private JobLauncher jobLauncher;

    @Metadata(autowired = true)
    private JobRegistry jobRegistry;

    public SpringBatchComponent() {}

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SpringBatchEndpoint endpoint = new SpringBatchEndpoint(uri, this, jobLauncher, remaining, jobRegistry);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (jobLauncher == null) {
            jobLauncher = getCamelContext()
                    .getRegistry()
                    .lookupByNameAndType(SpringBatchConstants.DEFAULT_JOB_LAUNCHER_REF_NAME, JobLauncher.class);
        }
    }

    public JobLauncher getJobLauncher() {
        return jobLauncher;
    }

    /**
     * Explicitly specifies a JobLauncher to be used.
     */
    public void setJobLauncher(JobLauncher jobLauncher) {
        this.jobLauncher = jobLauncher;
    }

    public JobRegistry getJobRegistry() {
        return jobRegistry;
    }

    /**
     * Explicitly specifies a JobRegistry to be used.
     */
    public void setJobRegistry(JobRegistry jobRegistry) {
        this.jobRegistry = jobRegistry;
    }
}
