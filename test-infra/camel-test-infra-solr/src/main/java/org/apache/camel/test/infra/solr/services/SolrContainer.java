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

package org.apache.camel.test.infra.solr.services;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.solr.common.SolrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class SolrContainer extends GenericContainer<SolrContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(SolrContainer.class);

    public static final String CONTAINER_NAME = "solr";

    public static final String SOLR_DFT_COLLECTION = "collection1";
    public static final String[] SOLR_CONTAINER_COMMANDS = new String[] { "solr-precreate", SOLR_DFT_COLLECTION };

    public SolrContainer() {
        super(LocalPropertyResolver.getProperty(SolrLocalContainerInfraService.class, SolrProperties.SOLR_CONTAINER));

        this.withNetworkAliases(CONTAINER_NAME)
                .withEnv("SOLR_OPTS", "-Xss500k")
                .withExposedPorts(SolrProperties.DEFAULT_PORT)
                .waitingFor(Wait.forHttp("/solr/admin/info/health"));
    }

    public SolrContainer(String imageName) {
        super(DockerImageName.parse(imageName));
    }

    public static SolrContainer initContainer(String networkAlias) {
        return new SolrContainer()
                .withNetworkAliases(networkAlias)
                .withEnv("SOLR_OPTS", "-Dsolr.environment=test,label=camel-solr-test-infra,color=sandybrown")
                .withEnv("GC_LOG_OPTS", "-verbose:")
                .withAccessToHost(true)
                .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix(CONTAINER_NAME))
                .withExposedPorts(SolrProperties.DEFAULT_PORT)
                .withCommand(SOLR_CONTAINER_COMMANDS)
                .waitingFor(Wait.forHttp("/solr/" + SOLR_DFT_COLLECTION + "/admin/ping?docker-ping"));
    }

}
