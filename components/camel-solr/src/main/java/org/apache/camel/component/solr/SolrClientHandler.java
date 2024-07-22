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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.camel.util.StringHelper;
import org.apache.solr.client.solrj.SolrClient;

public abstract class SolrClientHandler {

    public final SolrConfiguration solrConfiguration;

    public SolrClientHandler(SolrConfiguration solrConfiguration) {
        this.solrConfiguration = solrConfiguration;
    }

    protected abstract SolrClient getSolrClient();

    protected static Optional<String> getZkChrootFromUrl(String path) {
        path = StringHelper.removeStartingCharacters(path, '/');
        if (!path.contains("/")) {
            return Optional.empty();
        }
        // find first entry of csv that has /
        String pathWithZkChroot = Arrays.stream(path.split(",")).filter(s -> s.contains("/")).findFirst().orElse("");
        // don't consider as zkChroot when 1st subPath = 'solr'
        return Arrays.asList(pathWithZkChroot.split("/")).get(1).equals("solr")
                ? Optional.empty() : Optional.of(pathWithZkChroot.substring(pathWithZkChroot.indexOf('/')));
    }

    protected static String parseHostsFromUrl(String path, Optional<String> zkChroot) {
        String hostsPath = StringHelper.removeStartingCharacters(path, '/');
        return (zkChroot.isPresent()) ? hostsPath.substring(0, hostsPath.indexOf('/')) : hostsPath;
    }

    protected String getFirstUrlFromList() {
        return getUrlListFrom(solrConfiguration).get(0);
    }

    protected static List<String> getUrlListFrom(SolrConfiguration solrConfiguration) {
        String url = solrConfiguration.getZkHost() != null && !solrConfiguration.getZkHost().isEmpty()
                ? solrConfiguration.getZkHost() : solrConfiguration.getUrl();
        // add scheme when required
        List<String> urlList = Arrays
                .asList(url.split(","))
                .stream()
                .map(s -> solrConfiguration.getSolrScheme().getScheme().concat(s))
                .collect(Collectors.toList());
        // validate url syntax via parsing in URL instance
        for (String s : urlList) {
            try {
                // solrCloud requires addition of HTTP scheme to be able to consider it as a valid URL scheme
                new URL(
                        SolrConfiguration.SolrScheme.SOLRCLOUD.equals(solrConfiguration.getSolrScheme())
                                ? SolrConfiguration.SolrScheme.SOLR.getScheme().concat(s) : s);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(
                        String.format(
                                "Url '%s' not valid for endpoint with uri=%s",
                                s,
                                solrConfiguration.getSolrScheme().getUri()));
            }
        }
        return urlList;
    }

    /**
     * Signature defines parameters deciding whether or not to share the solrClient - sharing allowed: same signature -
     * sharing not allowed: different signature
     */
    public static String getSignature(SolrConfiguration solrConfiguration) {
        if (solrConfiguration.getSolrClient() != null) {
            return solrConfiguration.getSolrClient().toString();
        }
        StringBuilder sb = new StringBuilder();
        if (solrConfiguration.getSolrEndpoint() != null) {
            sb.append(solrConfiguration.getSolrEndpoint());
        }
        if (solrConfiguration.getUseConcurrentUpdateSolrClient()) {
            sb.append("_");
            sb.append(solrConfiguration.getUseConcurrentUpdateSolrClient());
        }
        return sb.toString();
    }

    /**
     * Allows to override solrClient configuration based on processing solrOperation
     */
    public static SolrConfiguration initializeFor(
            String solrOperation, SolrConfiguration solrConfiguration) {
        if (solrOperation == null) {
            return solrConfiguration;
        }
        switch (solrOperation) {
            case SolrConstants.OPERATION_INSERT_STREAMING:
                if (!SolrConfiguration.SolrScheme.SOLRCLOUD.equals(solrConfiguration.getSolrScheme())) {
                    SolrConfiguration newSolrConfiguration = solrConfiguration.deepCopy();
                    newSolrConfiguration.setUseConcurrentUpdateSolrClient(true);
                    return newSolrConfiguration;
                }
                return solrConfiguration;
            default:
                return solrConfiguration;
        }
    }

    protected static SolrClient getSolrClient(SolrConfiguration solrConfiguration) {
        // explicilty defined solrClient
        if (solrConfiguration.getSolrClient() != null) {
            return solrConfiguration.getSolrClient();
        }
        SolrClientHandler solrClientHandler = null;
        List<String> urlList = getUrlListFrom(solrConfiguration);
        // solrCloud scheme is set
        if (SolrConfiguration.SolrScheme.SOLRCLOUD.equals(solrConfiguration.getSolrScheme())) {
            solrClientHandler = new SolrClientHandlerCloud(solrConfiguration);
        } else {
            // more than 1 server provided:
            if (urlList.size() > 1) {
                solrClientHandler = new SolrClientHandlerLbHttp(solrConfiguration);
            } else {
                // config with ConcurrentUpdateSolrClient
                if (solrConfiguration.getUseConcurrentUpdateSolrClient()) {
                    solrClientHandler = new SolrClientHandlerConcurrentUpdate(solrConfiguration);
                } else {
                    // base HttpSolrClient
                    solrClientHandler = new SolrClientHandlerHttp(solrConfiguration);
                }
            }
        }
        return solrClientHandler.getSolrClient();
    }

}
