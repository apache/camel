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
package org.apache.camel.component.workday.producer;

import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.workday.WorkdayConfiguration;
import org.apache.camel.component.workday.WorkdayEndpoint;
import org.apache.camel.component.workday.auth.AuthClientForIntegration;
import org.apache.camel.component.workday.auth.AuthenticationClient;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultProducer;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.StatusLine;

/**
 * The Workday Default producer.
 */
public abstract class WorkdayDefaultProducer extends DefaultProducer {

    @Metadata(description = "The workday URL", javaType = "String")
    public static final String WORKDAY_URL_HEADER = "CamelWorkdayURL";

    private final WorkdayEndpoint endpoint;

    private final AuthenticationClient authenticationClient;

    public WorkdayDefaultProducer(WorkdayEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.authenticationClient = new AuthClientForIntegration(this.endpoint.getWorkdayConfiguration());
    }

    @Override
    public WorkdayEndpoint getEndpoint() {
        return (WorkdayEndpoint) super.getEndpoint();
    }

    public void process(Exchange exchange) throws Exception {
        PoolingHttpClientConnectionManager httpClientConnectionManager
                = endpoint.getWorkdayConfiguration().getHttpConnectionManager();
        CloseableHttpClient httpClient = HttpClientBuilder.create().setConnectionManager(httpClientConnectionManager).build();
        String workdayUri = prepareUri(endpoint.getWorkdayConfiguration());

        HttpGet httpGet = new HttpGet(workdayUri);
        this.authenticationClient.configure(httpClient, httpGet);

        try {
            httpClient.execute(
                    httpGet,
                    httpResponse -> {
                        if (httpResponse.getCode() != HttpStatus.SC_OK) {
                            throw new IllegalStateException(
                                    "Got the invalid http status value '" + new StatusLine(httpResponse)
                                                            + "' as the result of the RAAS '"
                                                            + workdayUri + "'");
                        }

                        try {
                            String report = getEndpoint().getCamelContext().getTypeConverter().mandatoryConvertTo(String.class,
                                    httpResponse.getEntity().getContent());

                            if (report.isEmpty()) {
                                throw new IllegalStateException(
                                        "Got the unexpected value '" + report + "' as the result of the report '" + workdayUri
                                                                + "'");
                            }

                            exchange.getIn().setBody(report);
                            exchange.getIn().setHeader(WORKDAY_URL_HEADER, workdayUri);
                            return null;
                        } catch (NoTypeConversionAvailableException e) {
                            throw new RuntimeCamelException(e);
                        }
                    });
        } catch (RuntimeCamelException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    public abstract String prepareUri(WorkdayConfiguration configuration) throws Exception;

}
