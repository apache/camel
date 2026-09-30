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
package org.apache.camel.component.openfga;

import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.util.URISupport;

/**
 * Readiness check for the OpenFGA server a producer asks its questions of.
 * <p/>
 * The component fails closed, so an OpenFGA server that cannot be reached fails every exchange through the route. This
 * check probes the server's {@code /healthz} endpoint so that an unavailable decision point is visible before traffic
 * starts failing, rather than only in the error logs afterwards.
 */
public class OpenFgaProducerHealthCheck extends AbstractHealthCheck {

    private final String apiUrl;
    private final String apiToken;
    private final String storeId;
    private final SSLContext sslContext;

    public OpenFgaProducerHealthCheck(String apiUrl, String apiToken, String storeId, String id,
                                      SSLContext sslContext) {
        // the id is built from the endpoint URI so that two endpoints sharing a store stay distinct, but that URI
        // carries the credentials in the clear and the id is published in the health output, so sanitize it
        super("camel", "producer:openfga-" + URISupport.sanitizeUri(id));
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
        this.storeId = storeId;
        this.sslContext = sslContext;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        OpenFgaHealthProbe.probe(builder, apiUrl, apiToken, storeId, sslContext);
    }
}
