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

package org.apache.camel.test.infra.azure.eventgrid.services;

import org.apache.camel.test.infra.common.services.InfrastructureService;

/**
 * An Event Grid custom topic that takes events in the CloudEvents 1.0 schema.
 */
public interface AzureEventGridInfraService extends InfrastructureService {

    /**
     * The endpoint the events of the topic are published to, for example
     * {@code https://localhost:4577/camel-topic-eventgrid/api/events}
     */
    String topicEndpoint();

    /**
     * An access key of the topic
     */
    String topicAccessKey();

    /**
     * The resource id of the topic, under which the management API keeps its event subscriptions, or null when it is
     * not known
     */
    String topicId();

    /**
     * The address of the management (Azure Resource Manager) API of the topic, or null when it is not known
     */
    String resourceManagerEndpoint();

    /**
     * A PKCS12 trust store with the certificate authority of the topic endpoint, to give to the JVM of a client with
     * {@code javax.net.ssl.trustStore}, or null when the JVM trusts that endpoint already
     */
    String trustStore();

    /**
     * The password of the trust store, or null when there is no trust store
     */
    String trustStorePassword();
}
