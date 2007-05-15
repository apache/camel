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
package org.apache.camel.bam;

import static org.apache.camel.util.ObjectHelper.notNull;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.FromBuilder;
import org.springframework.orm.jpa.JpaTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A builder of a process definition
 *
 * @version $Revision: $
 */
public abstract class ProcessBuilder extends RouteBuilder {
    private JpaTemplate jpaTemplate;
    private List<ActivityBuilder> activityBuilders = new ArrayList<ActivityBuilder>();
    private Class entityType = ProcessInstance.class;
    private ProcessDefinition process = new ProcessDefinition();

    protected ProcessBuilder(JpaTemplate jpaTemplate) {
        this.jpaTemplate = jpaTemplate;
    }

    public ActivityBuilder activity(String endpointUri) {
        return activity(endpoint(endpointUri));
    }

    public ActivityBuilder activity(Endpoint endpoint) {
        ActivityBuilder answer = new ActivityBuilder(this, endpoint);
        activityBuilders.add(answer);
        return answer;
    }

    /**
     * Sets the process entity type used to perform state management
     */
    public ProcessBuilder entityType(Class entityType) {
        this.entityType = entityType;
        return this;
    }


    public Processor createActivityProcessor(ActivityBuilder activityBuilder) {
        notNull(jpaTemplate, "jpaTemplate");
        return new JpaBamProcessor(getEntityType(), activityBuilder.getCorrelationExpression(), activityBuilder.getActivity(), getJpaTemplate());
    }

    // Properties
    //-----------------------------------------------------------------------
    public List<ActivityBuilder> getActivityBuilders() {
        return activityBuilders;
    }

    public Class getEntityType() {
        return entityType;
    }


    public JpaTemplate getJpaTemplate() {
        return jpaTemplate;
    }

    public void setJpaTemplate(JpaTemplate jpaTemplate) {
        this.jpaTemplate = jpaTemplate;
    }


    public ProcessDefinition getProcess() {
        return process;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void populateRoutes(List<Route> routes) throws Exception {
        for (ActivityBuilder builder : activityBuilders) {
            Endpoint from = builder.getEndpoint();
            Processor processor = builder.createProcessor();
            if (processor == null) {
                throw new IllegalArgumentException("No processor created for ActivityBuilder: " + builder);
            }
            routes.add(new Route(from, processor));
        }
    }}
