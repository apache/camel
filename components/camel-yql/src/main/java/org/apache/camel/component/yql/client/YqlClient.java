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
package org.apache.camel.component.yql.client;

import java.net.URI;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YqlClient {

    private static final Logger LOG = LoggerFactory.getLogger(YqlClient.class);
    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

    public YqlResponse get(final String query, final String format, final boolean diagnostics, final String callback) throws Exception {

        final URI uri = new URIBuilder()
                .setScheme("http")
                .setHost("query.yahooapis.com")
                .setPath("/v1/public/yql")
                .setParameter("format", format)
                .setParameter("diagnostics", Boolean.toString(diagnostics))
                .setParameter("env", "store://datatables.org/alltableswithkeys")
                .setParameter("callback", callback)
                .setParameter("q", query)
                .build();

        LOG.debug("YQL query: {}", uri);

        final HttpGet httpget = new HttpGet(uri);
        try (final CloseableHttpResponse response = HTTP_CLIENT.execute(httpget)) {
            final YqlResponse yqlResponse = YqlResponse.builder()
                    .httpRequest(uri.toString())
                    .status(response.getStatusLine().getStatusCode())
                    .body(EntityUtils.toString(response.getEntity()))
                    .build();
            LOG.debug("YQL response: {}", yqlResponse.getBody());
            return yqlResponse;
        }
    }
}