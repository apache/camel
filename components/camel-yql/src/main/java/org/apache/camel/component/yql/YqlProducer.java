/**
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
package org.apache.camel.component.yql;

import org.apache.camel.Exchange;
import org.apache.camel.component.yql.client.YqlClient;
import org.apache.camel.component.yql.client.YqlResponse;
import org.apache.camel.component.yql.configuration.YqlConfiguration;
import org.apache.camel.component.yql.exception.YqlHttpException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.http.HttpStatus;

/**
 * A Producer that send messages to YQL
 */
public class YqlProducer extends DefaultProducer {

    static final String CAMEL_YQL_HTTP_STATUS = "CamelYqlHttpStatus";
    static final String CAMEL_YQL_HTTP_REQUEST = "CamelYqlHttpRequest";

    private final YqlEndpoint endpoint;
    private final YqlClient yqlClient;

    YqlProducer(final YqlEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.yqlClient = new YqlClient(endpoint.getHttpClient());
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        final YqlConfiguration configuration = endpoint.getConfiguration();
        final YqlResponse yqlResponse = yqlClient.get(configuration);

        if (configuration.isThrowExceptionOnFailure() && yqlResponse.getStatus() != HttpStatus.SC_OK) {
            throw YqlHttpException.failedWith(yqlResponse.getStatus(), yqlResponse.getBody(), yqlResponse.getHttpRequest());
        }

        exchange.getIn().setHeader(CAMEL_YQL_HTTP_STATUS, yqlResponse.getStatus());
        exchange.getIn().setHeader(CAMEL_YQL_HTTP_REQUEST, yqlResponse.getHttpRequest());
        exchange.getIn().setBody(yqlResponse.getBody());
    }
}
