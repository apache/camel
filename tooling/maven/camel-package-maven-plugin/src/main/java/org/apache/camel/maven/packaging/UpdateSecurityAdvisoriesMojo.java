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
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;
import org.apache.camel.tooling.model.SecurityAdvisoryParser;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

/**
 * Syncs the published Apache Camel CVE security advisories (the sources behind
 * <a href="https://camel.apache.org/security/">camel.apache.org/security</a>, maintained as Markdown files with YAML
 * front matter in the camel-website git repository) into a JSON file shipped with camel-catalog, the same way the known
 * releases are synced by {@code update-camel-releases}.
 */
@Mojo(name = "update-security-advisories", threadSafe = true, defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class UpdateSecurityAdvisoriesMojo extends AbstractGeneratorMojo {

    private static final String GIT_SECURITY_URL
            = "https://api.github.com/repos/apache/camel-website/contents/content/security";
    private static final Pattern CVE_ID = Pattern.compile("CVE-(\\d{4})-(\\d+)");

    /**
     * The output directory for the generated catalog advisories file
     */
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources/org/apache/camel/catalog/advisories")
    protected File outDir;

    @Inject
    public UpdateSecurityAdvisoriesMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (outDir == null) {
            outDir = new File(project.getBasedir(), "src/generated/resources");
        }

        try {
            getLog().info("Updating Camel security advisories from camel-website");
            List<String> links = fetchAdvisoryLinks();
            List<SecurityAdvisoryModel> advisories = processAdvisories(links);
            if (advisories.isEmpty()) {
                throw new MojoExecutionException("No published security advisories found in " + GIT_SECURITY_URL);
            }
            advisories.sort(Comparator.comparingLong(UpdateSecurityAdvisoriesMojo::cveOrdinal));
            getLog().info("Found " + advisories.size() + " published security advisories");

            JsonArray arr = new JsonArray();
            for (SecurityAdvisoryModel advisory : advisories) {
                arr.add(JsonMapper.asJsonObject(advisory));
            }
            String json = Jsoner.serialize(arr);
            json = Jsoner.prettyPrint(json, 4);

            Path path = outDir.toPath();
            updateResource(path, "camel-security-advisories.json", json);
            addResourceDirectory(path);
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }
    }

    private List<SecurityAdvisoryModel> processAdvisories(List<String> urls) throws Exception {
        List<SecurityAdvisoryModel> answer = new ArrayList<>();

        try (HttpClient hc = HttpClient.newHttpClient()) {
            for (String url : urls) {
                SecurityAdvisoryModel model = SecurityAdvisoryParser.parse(CamelWebsiteContent.download(hc, url));
                if (model != null) {
                    answer.add(model);
                }
            }
        }

        return answer;
    }

    private List<String> fetchAdvisoryLinks() throws Exception {
        try (HttpClient hc = HttpClient.newHttpClient()) {
            return CamelWebsiteContent.downloadUrls(hc, GIT_SECURITY_URL,
                    name -> SecurityAdvisoryParser.ADVISORY_FILE.matcher(name).matches());
        }
    }

    private static long cveOrdinal(SecurityAdvisoryModel advisory) {
        Matcher matcher = CVE_ID.matcher(advisory.getCve() == null ? "" : advisory.getCve());
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1)) * 1_000_000L + Long.parseLong(matcher.group(2));
        }
        return 0;
    }
}
