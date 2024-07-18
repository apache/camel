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

import org.apache.camel.util.ObjectHelper;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;

public class SolrClientHandlerHttp extends SolrClientHandler {

    public SolrClientHandlerHttp(SolrConfiguration solrConfiguration) {
        super(solrConfiguration);
    }

    protected SolrClient getSolrClient() {
        HttpSolrClient.Builder builder = new HttpSolrClient.Builder(getFirstUrlFromList());
        if (!ObjectHelper.isEmpty(solrConfiguration.getConnectionTimeout())) {
            builder.withConnectionTimeout(solrConfiguration.getConnectionTimeout());
        }
        if (!ObjectHelper.isEmpty(solrConfiguration.getSoTimeout() != null)) {
            builder.withSocketTimeout(solrConfiguration.getSoTimeout());
        }
        if (!ObjectHelper.isEmpty(solrConfiguration.getHttpClient())) {
            builder.withHttpClient(solrConfiguration.getHttpClient());
        }
        if (!ObjectHelper.isEmpty(solrConfiguration.getAllowCompression())) {
            builder.allowCompression(solrConfiguration.getAllowCompression());
        }
        if (!ObjectHelper.isEmpty(solrConfiguration.getFollowRedirects())) {
            builder.withFollowRedirects(solrConfiguration.getFollowRedirects());
        }
        HttpSolrClient httpSolrClient = builder.build();
        return httpSolrClient;
    }

}
