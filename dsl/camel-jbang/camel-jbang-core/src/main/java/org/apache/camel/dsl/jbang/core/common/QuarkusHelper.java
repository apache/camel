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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Helper for resolving Quarkus platform information from the Quarkus registry.
 */
public final class QuarkusHelper {

    public static final String QUARKUS_PLATFORM_URL_PROPERTY = "camel.jbang.quarkus.platform.url";
    public static final String DEFAULT_QUARKUS_PLATFORM_URL = "https://registry.quarkus.io/client/platforms";

    private QuarkusHelper() {
    }

    /**
     * Returns the Quarkus platform registry URL, honoring the system property {@value #QUARKUS_PLATFORM_URL_PROPERTY}
     * if set.
     */
    public static String getQuarkusPlatformUrl() {
        return System.getProperty(QUARKUS_PLATFORM_URL_PROPERTY, DEFAULT_QUARKUS_PLATFORM_URL);
    }

    /**
     * Resolves the actual Quarkus platform version for each row by fetching the Quarkus platform registry and matching
     * the Camel Quarkus major.minor version against stream IDs.
     *
     * @param rows                 the list of rows to enrich with Quarkus platform versions
     * @param runtimeVersionFunc   function to extract the runtime (Camel Quarkus) version from a row
     * @param quarkusVersionSetter consumer to set the resolved Quarkus platform version on a row
     */
    public static <T> void resolveQuarkusPlatformVersions(
            List<T> rows,
            Function<T, String> runtimeVersionFunc,
            BiConsumer<T, String> quarkusVersionSetter) {

        try {
            HttpClient hc = HttpClient.newHttpClient();
            HttpResponse<String> res = hc.send(
                    HttpRequest.newBuilder(new URI(getQuarkusPlatformUrl()))
                            .timeout(Duration.ofSeconds(2))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                JsonObject json = (JsonObject) Jsoner.deserialize(res.body());
                JsonArray platforms = json.getCollection("platforms");
                if (platforms == null || platforms.isEmpty()) {
                    return;
                }
                JsonObject platform = platforms.getMap(0);
                JsonArray streams = platform.getCollection("streams");
                if (streams == null) {
                    return;
                }

                // keep the row with the highest runtime version per major.minor stream
                BinaryOperator<T> keepLatest = (a, b) -> VersionHelper.compare(
                        runtimeVersionFunc.apply(a), runtimeVersionFunc.apply(b)) >= 0 ? a : b;

                Map<String, T> latestPerStream = rows.stream()
                        .filter(row -> runtimeVersionFunc.apply(row) != null)
                        .collect(Collectors.toMap(
                                row -> VersionHelper.getMajorMinorVersion(runtimeVersionFunc.apply(row)),
                                Function.identity(),
                                keepLatest));

                // match each major.minor against registry streams and set the quarkus version
                latestPerStream.forEach((majorMinor, row) -> findQuarkusCoreVersion(streams, majorMinor)
                        .ifPresent(version -> quarkusVersionSetter.accept(row, version)));
            }
        } catch (Exception e) {
            // ignore - if the registry is not reachable within 2 seconds, skip
        }
    }

    /**
     * Finds the quarkus-core-version from the registry streams that matches the given major.minor stream ID.
     */
    private static Optional<String> findQuarkusCoreVersion(JsonArray streams, String majorMinor) {
        return streams.stream()
                .map(s -> (JsonObject) s)
                .filter(stream -> majorMinor.equals(stream.getString("id")))
                .findFirst()
                .map(stream -> (JsonArray) stream.getCollection("releases"))
                .filter(releases -> !releases.isEmpty())
                .map(releases -> (JsonObject) releases.getMap(0))
                .map(release -> release.getString("quarkus-core-version"));
    }
}
