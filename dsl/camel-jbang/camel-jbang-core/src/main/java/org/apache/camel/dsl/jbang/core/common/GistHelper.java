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
import org.apache.camel.util.FileUtil;

public final class GistHelper {

    private GistHelper() {
    }

    public static String asGistSingleUrl(String url) {
        if (url.startsWith("https://gist.githubusercontent.com/")) {
            url = url.substring(35);
        } else if (url.startsWith("https://gist.github.com/")) {
            url = url.substring(24);
        }

        // https://gist.github.com/davsclaus/477ddff5cdeb1ae03619aa544ce47e92
        // https://gist.githubusercontent.com/davsclaus/477ddff5cdeb1ae03619aa544ce47e92/raw/cd1be96034748e42e43879a4d27ed297752b6115/mybeer.xml
        url = url.replaceFirst("/", ":");
        url = url.replaceFirst("/raw/", ":");
        url = url.replaceFirst("/", ":");
        return "gist:" + url;
    }

    public static void fetchGistUrls(String url, StringJoiner all) throws Exception {
        doFetchGistUrls(url, null, null, null, all);
    }

    public static void fetchGistUrls(String url, StringJoiner routes, StringJoiner kamelets, StringJoiner properties)
            throws Exception {
        doFetchGistUrls(url, routes, kamelets, properties, null);
    }

    private static void doFetchGistUrls(
            String url, StringJoiner routes, StringJoiner kamelets, StringJoiner properties,
            StringJoiner all)
            throws Exception {

        // a gist can have one or more files
        // https://gist.github.com/davsclaus/477ddff5cdeb1ae03619aa544ce47e92

        // strip https://gist.github.com/
        url = url.substring(24);

        String[] parts = url.split("/");
        if (parts.length < 2) {
            return;
        }
        String gid = parts[1];

        url = "https://api.github.com/gists/" + gid;

        resolveGistAsRawFiles(url, routes, kamelets, properties, all);
    }

    private static void resolveGistAsRawFiles(
            String url, StringJoiner routes, StringJoiner kamelets, StringJoiner properties, StringJoiner all)
            throws Exception {

        // use JDK http client to call github api
        HttpClient hc = HttpClient.newHttpClient();
        HttpResponse<String> res = hc.send(HttpRequest.newBuilder(new URI(url)).timeout(Duration.ofSeconds(20)).build(),
                HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() == 200) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(res.body());
            for (JsonNode c : root.get("files")) {
                String name = c.get("filename").asText();
                String ext = FileUtil.onlyExt(name, false);
                if (kamelets != null && "kamelet.yaml".equalsIgnoreCase(ext)) {
                    String rawUrl = c.get("raw_url").asText();
                    String u = asGistSingleUrl(rawUrl);
                    kamelets.add(u);
                } else if (properties != null && "properties".equalsIgnoreCase(ext)) {
                    String rawUrl = c.get("raw_url").asText();
                    String u = asGistSingleUrl(rawUrl);
                    properties.add(u);
                } else if (routes != null) {
                    if ("java".equalsIgnoreCase(ext) || "xml".equalsIgnoreCase(ext)
                            || "yaml".equalsIgnoreCase(ext)
                            || "groovy".equalsIgnoreCase(ext) || "js".equalsIgnoreCase(ext) || "jsh".equalsIgnoreCase(ext)
                            || "kts".equalsIgnoreCase(ext)) {
                        String rawUrl = c.get("raw_url").asText();
                        String u = asGistSingleUrl(rawUrl);
                        routes.add(u);
                    }
                } else if (all != null) {
                    String rawUrl = c.get("raw_url").asText();
                    String u = asGistSingleUrl(rawUrl);
                    all.add(u);
                }
            }
        }
    }

}
