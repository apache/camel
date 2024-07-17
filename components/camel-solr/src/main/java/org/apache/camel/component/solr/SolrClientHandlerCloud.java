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

import java.util.List;
import java.util.Optional;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;

public class SolrClientHandlerCloud extends SolrClientHandler {

    public SolrClientHandlerCloud(SolrConfiguration solrConfiguration) {
        super(solrConfiguration);
    }

    protected SolrClient getSolrClient() {
        Optional<String> zkChrootOptional = Optional.ofNullable(solrConfiguration.getZkChroot());
        List<String> urlList = getUrlListFrom(solrConfiguration);
        CloudSolrClient.Builder builder = new CloudSolrClient.Builder(
                urlList,
                zkChrootOptional);
        if (solrConfiguration.getConnectionTimeout() != null) {
            builder.withConnectionTimeout(solrConfiguration.getConnectionTimeout());
        }
        if (solrConfiguration.getSoTimeout() != null) {
            builder.withSocketTimeout(solrConfiguration.getSoTimeout());
        }
        if (solrConfiguration.getHttpClient() != null) {
            builder.withHttpClient(solrConfiguration.getHttpClient());
        }
        CloudSolrClient cloudSolrClient = builder.build();
        if (solrConfiguration.getCollection() != null && !solrConfiguration.getCollection().isEmpty()) {
            cloudSolrClient.setDefaultCollection(solrConfiguration.getCollection());
        }
        return cloudSolrClient;
    }

}
