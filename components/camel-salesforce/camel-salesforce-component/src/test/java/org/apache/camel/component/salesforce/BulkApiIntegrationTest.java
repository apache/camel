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
package org.apache.camel.component.salesforce;

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.salesforce.api.dto.bulk.ContentType;
import org.apache.camel.component.salesforce.api.dto.bulk.JobInfo;
import org.apache.camel.component.salesforce.api.dto.bulk.OperationEnum;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Test;

public class BulkApiIntegrationTest extends AbstractBulkApiTestBase {

    @Test
    public void testRetry() throws Exception {
        SalesforceComponent sf = context().getComponent("salesforce", SalesforceComponent.class);
        String accessToken = sf.getSession().getAccessToken();

        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setSslContext(new SSLContextParameters().createSSLContext(context));
        HttpClient httpClient = new HttpClient(sslContextFactory);
        httpClient.setConnectTimeout(60000);
        httpClient.start();

        String uri = sf.getLoginConfig().getLoginUrl() + "/services/oauth2/revoke?token=" + accessToken;
        Request logoutGet = httpClient.newRequest(uri)
            .method(HttpMethod.GET)
            .timeout(1, TimeUnit.MINUTES);

        ContentResponse response = logoutGet.send();
        assertEquals(HttpStatus.OK_200, response.getStatus());

        JobInfo jobInfo = new JobInfo();
        jobInfo.setOperation(OperationEnum.INSERT);
        jobInfo.setContentType(ContentType.CSV);
        jobInfo.setObject(Merchandise__c.class.getSimpleName());
        createJob(jobInfo);
    }
}