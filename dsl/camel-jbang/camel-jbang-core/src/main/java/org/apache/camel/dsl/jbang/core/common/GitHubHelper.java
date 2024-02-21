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
package org.apache.camel.dsl.jbang.core.common;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.StringJoiner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.FileUtil;

public final class GitHubHelper {

    private GitHubHelper() {
    }

    public static String asGithubSingleUrl(String url) {
        // strip https://github.com/
        url = url.substring(19);
        // https://github.com/apache/camel-k-examples/blob/main/generic-examples/languages/routes.kts
        // https://raw.githubusercontent.com/apache/camel-kamelets-examples/main/jbang/hello-java/Hey.java
        // https://github.com/apache/camel-kamelets-examples/blob/main/jbang/hello-java/Hey.java
        url = url.replaceFirst("/", ":");
        url = url.replaceFirst("/", ":");
        url = url.replaceFirst("tree/", "");
        url = url.replaceFirst("blob/", "");
        url = url.replaceFirst("/", ":");
        return "github:" + url;
    }

    public static void fetchGithubUrls(String url, StringJoiner all) throws Exception {
        doFetchGithubUrls(url, null, null, null, all);
    }

    public static void fetchGithubUrls(String url, StringJoiner routes, StringJoiner kamelets, StringJoiner properties)
            throws Exception {
        doFetchGithubUrls(url, routes, kamelets, properties, null);
    }

    private static void doFetchGithubUrls(
            String url, StringJoiner routes, StringJoiner kamelets, StringJoiner properties,
            StringJoiner all)
            throws Exception {

        // this is a directory, so we need to query github which files are there and filter them

        // strip https://github.com/
        url = url.substring(19);

        String[] parts = url.split("/");
        if (parts.length < 5) {
            return;
        }

        String org = parts[0];
        String repo = parts[1];
        String action = parts[2];
        String branch = parts[3];
        String path;
        String wildcard = null;
        StringJoiner sj = new StringJoiner("/");
        for (int i = 4; i < parts.length; i++) {
            if (i == parts.length - 1) {
                // last element uses wildcard to filter which files to include
                if (parts[i].contains("*")) {
                    wildcard = parts[i];
                    break;
                }
            }
            sj.add(parts[i]);
        }
        path = sj.toString();

        if ("tree".equals(action)) {
            // https://api.github.com/repos/apache/camel-k-examples/contents/examples/generic-examples
            url = "https://api.github.com/repos/" + org + "/" + repo + "/contents/" + path;
            if (!"main".equals(branch) && !"master".equals(branch)) {
                url = url + "?ref=" + branch;
            }
        }

        resolveGithubAsRawFiles(url, wildcard, routes, kamelets, properties, all);
    }

    private static void resolveGithubAsRawFiles(
            String url, String wildcard, StringJoiner routes, StringJoiner kamelets, StringJoiner properties, StringJoiner all)
            throws Exception {

        // use JDK http client to call github api
        HttpClient hc = HttpClient.newHttpClient();
        HttpResponse<String> res = hc.send(HttpRequest.newBuilder(new URI(url)).timeout(Duration.ofSeconds(20)).build(),
                HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() == 200) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(res.body());
            for (JsonNode c : root) {
                String name = c.get("name").asText();
                String ext = FileUtil.onlyExt(name, false);
                boolean match = wildcard == null || AntPathMatcher.INSTANCE.match(wildcard, name, false);
                if (match) {
                    if (kamelets != null && "kamelet.yaml".equalsIgnoreCase(ext)) {
                        String htmlUrl = c.get("html_url").asText();
                        String u = asGithubSingleUrl(htmlUrl);
                        kamelets.add(u);
                    } else if (properties != null && "properties".equalsIgnoreCase(ext)) {
                        String htmlUrl = c.get("html_url").asText();
                        String u = asGithubSingleUrl(htmlUrl);
                        properties.add(u);
                    } else if (routes != null) {
                        if ("java".equalsIgnoreCase(ext) || "xml".equalsIgnoreCase(ext)
                                || "yaml".equalsIgnoreCase(ext)
                                || "groovy".equalsIgnoreCase(ext) || "js".equalsIgnoreCase(ext) || "jsh".equalsIgnoreCase(ext)
                                || "kts".equalsIgnoreCase(ext)) {
                            String htmlUrl = c.get("html_url").asText();
                            String u = asGithubSingleUrl(htmlUrl);
                            routes.add(u);
                        }
                    } else if (all != null) {
                        String htmlUrl = c.get("html_url").asText();
                        String u = asGithubSingleUrl(htmlUrl);
                        all.add(u);
                    }
                }
            }
        }
    }

}
