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
package org.apache.camel.component.solr;

import java.util.concurrent.TimeUnit;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.LBHttp2SolrClient;

public class SolrClientHandlerLbHttp extends SolrClientHandler {

    public SolrClientHandlerLbHttp(SolrConfiguration solrConfiguration) {
        super(solrConfiguration);
    }

    protected SolrClient getSolrClient() {
        Http2SolrClient.Builder solrClientBuilder = new Http2SolrClient.Builder();
        if (solrConfiguration.getConnectionTimeout() != null) {
            solrClientBuilder.withConnectionTimeout(solrConfiguration.getConnectionTimeout(), TimeUnit.MILLISECONDS);
        }
        if (solrConfiguration.getIdleTimeout() != null) {
            solrClientBuilder.withIdleTimeout(solrConfiguration.getIdleTimeout(), TimeUnit.MILLISECONDS);
        }

        LBHttp2SolrClient.Builder builder = new LBHttp2SolrClient.Builder(
                solrClientBuilder.build(), getUrlListFrom(solrConfiguration).toArray(new String[0]));
        return builder.build();
    }

}
