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

    @Metadata(autowired = true,
              description = "The JobLauncher that runs the jobs of all endpoints, unless an endpoint sets its own. When not"
                            + " set, the component uses the single JobLauncher of the registry, or else the bean named"
                            + " jobLauncher.")
    private JobLauncher jobLauncher;
    @Metadata(autowired = true,
              description = "The JobRegistry in which the endpoints look up the job by its name, unless an endpoint sets its"
                            + " own. A job that is not in it, or every job when no JobRegistry is set, is looked up in the"
                            + " Camel registry as a Job bean.")
    private JobRegistry jobRegistry;

    public SpringBatchComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SpringBatchEndpoint endpoint = new SpringBatchEndpoint(
                uri, this, jobLauncher, remaining, jobRegistry);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (jobLauncher == null) {
            jobLauncher = getCamelContext().getRegistry()
                    .lookupByNameAndType(SpringBatchConstants.DEFAULT_JOB_LAUNCHER_REF_NAME, JobLauncher.class);
        }
    }

    public JobLauncher getJobLauncher() {
        return jobLauncher;
    }

    public void setJobLauncher(JobLauncher jobLauncher) {
        this.jobLauncher = jobLauncher;
    }

    public JobRegistry getJobRegistry() {
        return jobRegistry;
    }

    public void setJobRegistry(JobRegistry jobRegistry) {
        this.jobRegistry = jobRegistry;
    }
}
