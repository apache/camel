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
package org.apache.camel.maven.packaging;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Reads the content of the camel-website repository on GitHub, for the goals that sync the catalog with it
 * ({@code update-camel-releases}, {@code update-security-advisories}). A failure is an error, not an empty answer, so a
 * rate-limited or broken run never writes a shorter list into the catalog. The GitHub API is called with the
 * {@code GITHUB_TOKEN} environment variable when it is set, as in a GitHub workflow.
 */
final class CamelWebsiteContent {

    private static final String GITHUB_API = "https://api.github.com/";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private CamelWebsiteContent() {
    }

    /**
     * The download URLs of the files of a folder of the GitHub contents API whose name matches.
     *
     * @throws IOException when GitHub does not answer with the listing, or no file matches
     */
    static List<String> downloadUrls(HttpClient client, String folderUrl, Predicate<String> name)
            throws IOException, InterruptedException {
        HttpResponse<String> res = send(client, folderUrl);
        if (res.statusCode() != 200) {
            throw new IOException(
                    "GitHub answered with HTTP status " + res.statusCode() + " for " + folderUrl
                                  + (res.statusCode() == 403 || res.statusCode() == 429
                                          ? " (rate limited: set the GITHUB_TOKEN environment variable)" : ""));
        }
        List<String> answer = new ArrayList<>();
        try {
            JsonArray root = (JsonArray) Jsoner.deserialize(res.body());
            for (Object o : root) {
                JsonObject jo = (JsonObject) o;
                String fileName = jo.getString("name");
                String url = jo.getString("download_url");
                if (fileName != null && url != null && name.test(fileName)) {
                    answer.add(url);
                }
            }
        } catch (Exception e) {
            throw new IOException("GitHub answered with an unexpected listing for " + folderUrl, e);
        }
        if (answer.isEmpty()) {
            throw new IOException("No files found in " + folderUrl);
        }
        return answer;
    }

    /**
     * The text of a file.
     *
     * @throws IOException when it cannot be downloaded
     */
    static String download(HttpClient client, String url) throws IOException, InterruptedException {
        HttpResponse<String> res = send(client, url);
        if (res.statusCode() != 200) {
            throw new IOException("HTTP status " + res.statusCode() + " downloading " + url);
        }
        return res.body();
    }

    private static HttpResponse<String> send(HttpClient client, String url) throws IOException, InterruptedException {
        HttpResponse<String> res = client.send(request(url), HttpResponse.BodyHandlers.ofString());
        // follow a redirect
        if (res.statusCode() == 302) {
            String loc = res.headers().firstValue("location").orElse(null);
            if (loc != null) {
                res = client.send(request(loc), HttpResponse.BodyHandlers.ofString());
            }
        }
        return res;
    }

    static HttpRequest request(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT);
        String token = System.getenv("GITHUB_TOKEN");
        if (url.startsWith(GITHUB_API) && token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder.build();
    }
}
