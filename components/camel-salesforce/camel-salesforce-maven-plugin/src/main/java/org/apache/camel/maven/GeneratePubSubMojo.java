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
package org.apache.camel.maven;

import java.io.File;

import org.apache.camel.component.salesforce.codegen.AbstractSalesforceExecution;
import org.apache.camel.component.salesforce.codegen.GeneratePubSubExecution;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "generatePubSub", requiresProject = false, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GeneratePubSubMojo extends AbstractSalesforceMojo {

    @Parameter(property = "camelSalesforce.pubSubHost", required = true, defaultValue = "api.pubsub.salesforce.com")
    String pubSubHost;

    @Parameter(property = "camelSalesforce.pubSubPort", required = true, defaultValue = "7443")
    Integer pubSubPort;

    @Parameter(property = "camelSalesforce.pubSubOutputDirectory",
               defaultValue = "${project.build.directory}/generated-sources/camel-salesforce")
    File outputDirectory;

    @Parameter
    String[] topics;

    GeneratePubSubExecution execution = new GeneratePubSubExecution();

    @Override
    protected AbstractSalesforceExecution getSalesforceExecution() {
        return execution;
    }

    @Override
    protected void setup() {
        super.setup();
        execution.setTopics(topics);
        execution.setPubSubHost(pubSubHost);
        execution.setPubSubPort(pubSubPort);
        execution.setOutputDirectory(outputDirectory);
        execution.setup();
    }
}
