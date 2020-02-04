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
package org.apache.camel.component.workday;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.component.workday.auth.AuthClientForIntegration;
import org.apache.camel.component.workday.auth.AutheticationClient;
import org.apache.camel.support.DefaultProducer;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Workday Report producer.
 */
public class WorkdayReportProducer extends DefaultProducer {

    public static final String WORKDAY_URL_HEADER = "CamelWorkdayURL";
    public static final String WORKDAY_RASS_URL_TEMPALTE = "https://%s/ccx/service/customreport2/%s%s";

    private static final Logger LOG = LoggerFactory.getLogger(WorkdayReportProducer.class);

    private WorkdayEndpoint endpoint;

    private AutheticationClient autheticationClient;

    public WorkdayReportProducer(WorkdayEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.autheticationClient = new AuthClientForIntegration(this.endpoint.getWorkdayConfiguration());
    }

    @Override
    public WorkdayEndpoint getEndpoint() {
        return (WorkdayEndpoint)super.getEndpoint();
    }

    public void process(Exchange exchange) throws Exception {
        PoolingHttpClientConnectionManager httpClientConnectionManager = endpoint.getWorkdayConfiguration().getHttpConnectionManager();
        CloseableHttpClient httpClient = HttpClientBuilder.create().setConnectionManager(httpClientConnectionManager).build();
        String workdayUri = prepareUri(endpoint.getWorkdayConfiguration());

        HttpGet httpGet = new HttpGet(workdayUri);
        this.autheticationClient.configure(httpClient, httpGet);

        CloseableHttpResponse httpResponse = httpClient.execute(httpGet);

        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new IllegalStateException("Got the invalid http status value '" + httpResponse.getStatusLine() + "' as the result of the RAAS '" + workdayUri + "'");
        }

        String report = getEndpoint().getCamelContext().getTypeConverter().mandatoryConvertTo(String.class, httpResponse.getEntity().getContent());

        if (report.isEmpty()) {
            throw new IllegalStateException("Got the unexpected value '" + report + "' as the result of the report '" + workdayUri + "'");
        }

        exchange.getIn().setBody(report);
        exchange.getIn().setHeader(WORKDAY_URL_HEADER, workdayUri);
    }

    public String prepareUri(WorkdayConfiguration configuration) {
        Map<String, Object> parameters = configuration.getParameters();
        StringBuilder stringBuilder = new StringBuilder(configuration.getPath());
        stringBuilder.append("?");
        if (parameters.size() > 0) {
            String params = parameters.keySet().stream().map(k -> k + "=" + parameters.get(k)).collect(Collectors.joining("&"));
            stringBuilder.append(params);
            stringBuilder.append("&");
        }

        stringBuilder.append("format=");
        stringBuilder.append(configuration.getReportFormat());
        String uriString = String.format(WORKDAY_RASS_URL_TEMPALTE, configuration.getHost(), configuration.getTenant(), stringBuilder.toString());

        return uriString;
    }

}
