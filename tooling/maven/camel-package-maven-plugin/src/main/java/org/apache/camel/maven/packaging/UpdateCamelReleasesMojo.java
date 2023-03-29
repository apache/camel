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

import java.io.File;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.ReleaseModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Unfortunately we do not have a release timestamp for every Camel release published to maven. So we need to grab the
 * dates from camel-website git repository.
 */
@Mojo(name = "update-camel-releases", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class UpdateCamelReleasesMojo extends AbstractGeneratorMojo {

    private static final String GIT_CAMEL_URL = "https://api.github.com/repos/apache/camel-website/contents/content/releases/";
    private static final String GIT_CAMEL_QUARKUS_URL
            = "https://api.github.com/repos/apache/camel-website/contents/content/releases/q/";

    /**
     * The output directory for generated file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/releases")
    protected File outDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (outDir == null) {
            outDir = new File(project.getBasedir(), "src/generated/resources");
        }

        try {
            getLog().info("Updating Camel release information from camel-website");
            List<String> links = fetchCamelReleaseLinks(GIT_CAMEL_URL);
            updateCamelRelease("Camel", links, "camel-releases.json");

            links = fetchCamelReleaseLinks(GIT_CAMEL_QUARKUS_URL);
            updateCamelRelease("Camel Quarkus", links, "camel-quarkus-releases.json");
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }
    }

    private void updateCamelRelease(String kind, List<String> links, String fileName) throws Exception {
        List<ReleaseModel> releases = processReleases(links);
        releases.sort(Comparator.comparing(ReleaseModel::getVersion));
        getLog().info("Found " + releases.size() + " " + kind + " releases");

        JsonArray arr = new JsonArray();
        for (ReleaseModel r : releases) {
            JsonObject jo = JsonMapper.asJsonObject(r);
            arr.add(jo);
        }
        String json = Jsoner.serialize(arr);
        json = Jsoner.prettyPrint(json, 4);

        Path path = outDir.toPath();
        updateResource(path, fileName, json);
        addResourceDirectory(path);
    }

    private List<ReleaseModel> processReleases(List<String> urls) throws Exception {
        List<ReleaseModel> answer = new ArrayList<>();

        HttpClient hc = HttpClient.newHttpClient();
        for (String url : urls) {
            HttpResponse<String> res = hc.send(HttpRequest.newBuilder(new URI(url)).timeout(Duration.ofSeconds(20)).build(),
                    HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                ReleaseModel model = new ReleaseModel();
                LineNumberReader lr = new LineNumberReader(new StringReader(res.body()));
                String line = lr.readLine();
                while (line != null) {
                    if (line.startsWith("date:")) {
                        model.setDate(line.substring(5).trim());
                    } else if (line.startsWith("version:")) {
                        model.setVersion(line.substring(8).trim());
                    } else if (line.startsWith("eol:")) {
                        model.setEol(line.substring(4).trim());
                    } else if (line.startsWith("kind:")) {
                        model.setKind(line.substring(5).trim());
                    } else if (line.startsWith("jdk:")) {
                        String s = line.substring(4).trim();
                        if (s.startsWith("[") && s.endsWith("]")) {
                            s = s.substring(1, s.length() - 1);
                        }
                        model.setJdk(s);
                    }
                    line = lr.readLine();
                }
                if (model.getVersion() != null) {
                    answer.add(model);
                }
            }
        }

        return answer;
    }

    private List<String> fetchCamelReleaseLinks(String gitUrl) throws Exception {
        List<String> answer = new ArrayList<>();

        // use JDK http client to call github api
        HttpClient hc = HttpClient.newHttpClient();
        HttpResponse<String> res = hc.send(HttpRequest.newBuilder(new URI(gitUrl)).timeout(Duration.ofSeconds(20)).build(),
                HttpResponse.BodyHandlers.ofString());

        // follow redirect
        if (res.statusCode() == 302) {
            String loc = res.headers().firstValue("location").orElse(null);
            if (loc != null) {
                res = hc.send(HttpRequest.newBuilder(new URI(loc)).timeout(Duration.ofSeconds(20)).build(),
                        HttpResponse.BodyHandlers.ofString());
            }
        }

        if (res.statusCode() == 200) {
            JsonArray root = (JsonArray) Jsoner.deserialize(res.body());
            for (Object o : root) {
                JsonObject jo = (JsonObject) o;
                String name = jo.getString("name");
                if (name != null && name.startsWith("release-")) {
                    String url = jo.getString("download_url");
                    if (url != null) {
                        answer.add(url);
                    }
                }
            }
        }

        return answer;
    }

}
