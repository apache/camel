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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.telemetry.Span;

public class GoogleSecretManagerSpanDecorator extends AbstractSpanDecorator {

    static final String SECRET_MANAGER_OPERATION = "operation";
    static final String SECRET_MANAGER_SECRET_ID = "secretId";
    static final String SECRET_MANAGER_VERSION_ID = "versionId";

    /**
     * Constants copied from {@link org.apache.camel.component.google.secret.manager.GoogleSecretManagerConstants}. Only
     * the secret identifier (name) and version are tagged, never the secret value.
     */
    static final String OPERATION = "CamelGoogleSecretManagerOperation";
    static final String SECRET_ID = "CamelGoogleSecretManagerSecretId";
    static final String VERSION_ID = "CamelGoogleSecretManagerVersionId";

    @Override
    public String getComponent() {
        return "google-secret-manager";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.google.secret.manager.GoogleSecretManagerComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        Object operation = exchange.getIn().getHeader(OPERATION);
        if (operation != null) {
            span.setTag(SECRET_MANAGER_OPERATION, operation.toString());
        }

        String secretId = exchange.getIn().getHeader(SECRET_ID, String.class);
        if (secretId != null) {
            span.setTag(SECRET_MANAGER_SECRET_ID, secretId);
        }

        String versionId = exchange.getIn().getHeader(VERSION_ID, String.class);
        if (versionId != null) {
            span.setTag(SECRET_MANAGER_VERSION_ID, versionId);
        }
    }
}
