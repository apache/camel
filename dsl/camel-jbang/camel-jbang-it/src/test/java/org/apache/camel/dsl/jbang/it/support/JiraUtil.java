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
package org.apache.camel.dsl.jbang.it.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

public abstract class JiraUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JiraUtil.class);
    private static final String ISSUE_ENDPOINT
            = "https://issues.apache.org/jira/rest/api/latest/issue/%s?fields=resolution,fixVersions";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static List<String> solvedStatuses = List.of("Fixed");

    public static boolean isIssueSolved(final String issue, final String inVersion) {
        try (InputStream response = httpClient.send(HttpRequest
                .newBuilder(new URI(String.format(ISSUE_ENDPOINT, issue)))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build(),
                HttpResponse.BodyHandlers.ofInputStream()).body()) {
            final Map fields = (Map) new ObjectMapper()
                    .readValue(response, Map.class).get("fields");
            return Optional.ofNullable(fields.get("resolution"))
                    .map(r -> solvedStatuses.contains(((Map) r).get("name")))
                    .isPresent()
                    &&
                    Optional.ofNullable(fields.get("fixVersions"))
                            .map(f -> ((List) f).stream().map(fv -> ((Map) fv).get("name"))
                                    .anyMatch(fv -> VersionHelper.isGE(inVersion, (String) fv)))
                            .get();
        } catch (IOException | InterruptedException | URISyntaxException e) {
            LOGGER.warn("unable to verify Jira issue", e);
            return true; //consider it as solved, so ready to be tested
        }
    }
}
