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
package org.apache.camel.dsl.jbang.export;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.export.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.export.common.CatalogLoader;
import org.apache.camel.dsl.jbang.export.common.ExportHelper;
import org.apache.camel.dsl.jbang.export.common.PathUtils;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;
import org.apache.camel.dsl.jbang.export.common.RuntimeUtil;
import org.apache.camel.dsl.jbang.export.common.TemplateHelper;
import org.apache.camel.dsl.jbang.export.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.util.CamelCaseOrderedProperties;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.dsl.jbang.export.common.ExportHelper.exportPackageName;

/**
 * Exports to a Camel Spring Boot Maven project.
 */
class SpringBootExporter extends Exporter {

    SpringBootExporter(ExportRequest request, ExportContext context) {
        super(request, context);
        if (this.request.pomTemplateName == null) {
            this.request.pomTemplateName = "spring-boot-pom.ftl";
        }
    }

    @Override
    public Integer export() throws Exception {
        try {
            MavenGav.parseStrictGav(request.gav);
        } catch (IllegalArgumentException e) {
            printer().printErr("--gav must be in syntax: groupId:artifactId:version");
            return 1;
        }
        if (request.parentPom != null) {
            try {
                MavenGav.parseStrictGav(request.parentPom);
            } catch (IllegalArgumentException e) {
                printer().printErr("--parent-pom must be in syntax: groupId:artifactId:version");
                return 1;
            }
        }
        String[] ids = request.gav.split(":");

        request.exportBaseDir = request.exportBaseDir != null ? request.exportBaseDir : Path.of(".");
        Path profile = request.exportBaseDir.resolve("application.properties");

        // the settings file has information what to export
        Path settings = runSettingsFile();
        if (request.mavenResolver.fresh() || !request.files.isEmpty() || !Files.exists(settings)) {
            // allow to automatic build
            printer().println("Generating fresh run data");
            int silent = runSilently(request.ignoreLoadingError, request.lazyBean, request.verbose);
            if (silent != 0) {
                return silent;
            }
        } else {
            printer().println("Reusing existing run data");
        }

        printer().println("Exporting as Spring Boot project to: " + request.exportDir);

        // use a temporary work dir
        Path buildDir = buildDir();
        PathUtils.deleteDirectory(buildDir);
        Files.createDirectories(buildDir);

        Path srcJavaDirRoot = buildDir.resolve("src/main/java");
        String srcPackageName = exportPackageName(ids[0], ids[1], request.packageName);
        Path srcJavaDir;
        if (srcPackageName == null) {
            srcJavaDir = srcJavaDirRoot;
        } else {
            srcJavaDir = srcJavaDirRoot.resolve(srcPackageName.replace('.', File.separatorChar));
        }
        Files.createDirectories(srcJavaDir);
        Path srcResourcesDir = buildDir.resolve("src/main/resources");
        Files.createDirectories(srcResourcesDir);
        Path srcCamelResourcesDir = buildDir.resolve("src/main/resources/camel");
        Path srcKameletsResourcesDir = buildDir.resolve("src/main/resources/kamelets");
        // copy application properties files
        copyApplicationPropertiesFiles(srcResourcesDir);

        // copy source files
        copySourceFiles(settings, profile, srcJavaDirRoot, srcJavaDir,
                srcResourcesDir, srcCamelResourcesDir,
                srcKameletsResourcesDir, srcPackageName);

        // create main class
        createMainClassSource(srcJavaDir, srcPackageName, request.mainClassname);
        // gather dependencies
        final Set<String> deps = resolveDependencies(settings, profile);
        // copy local lib JARs
        copyLocalLibDependencies(deps);
        // copy from settings to profile
        Map<String, String> devProfile = new LinkedHashMap<>();
        copySettingsAndProfile(settings, profile, srcResourcesDir, prop -> {
            if (!hasModeline(settings)) {
                prop.remove("camel.main.modeline");
            }
            // ensure spring-boot keeps running if no HTTP server included
            boolean http = deps.stream().anyMatch(s -> s.contains("mvn:org.apache.camel:camel-platform-http"));
            if (!http) {
                prop.put("camel.main.run-controller", "true");
            }
            // are we using http then enable embedded HTTP server (if not explicit configured already)
            if (!prop.containsKey("server.port")) {
                int port = httpServerPort(settings);
                if (port == -1 && http) {
                    port = 8080;
                }
                if (port != -1 && port != 8080) {
                    prop.put("server.port", port);
                }
            }
            if (!prop.containsKey("management.server.port")) {
                request.port = ExportHelper.httpManagementPort(settings);
                if (request.port != -1) {
                    prop.put("management.server.port", request.port);
                }
            }
            if (request.hawtio) {
                // spring boot needs these options configured to support hawtio
                prop.setProperty("management.endpoints.web.exposure.include",
                        exposeActuatorEndpoints(prop.getProperty("management.endpoints.web.exposure.include"),
                                "hawtio", "jolokia"));
                prop.setProperty("spring.jmx.enabled", "true");
                prop.setProperty("hawtio.authenticationEnabled", "false");
            }
            // developer console (--console) is the camel actuator endpoint (/actuator/camel)
            if (settingsFlag(settings, CamelJBangConstants.CONSOLE)) {
                addConsoleProperties(prop, devProfile, Map.of("camel.main.devConsoleEnabled", "true"));
                // the camel endpoint must be exposed, keeping what is already exposed as the profile replaces the
                // value. With --observe the starter exposes health and prometheus as its default, which an explicit
                // list would replace, so keep them too (the console is then at /observe/camel)
                String exposed = prop.getProperty("management.endpoints.web.exposure.include");
                if (exposed == null && request.observe) {
                    exposed = "health,prometheus";
                }
                exposed = exposeActuatorEndpoints(exposed, "camel");
                if (isConsoleInApplicationProperties()) {
                    prop.put("management.endpoints.web.exposure.include", exposed);
                } else {
                    devProfile.put("management.endpoints.web.exposure.include", exposed);
                }
            }
            return prop;
        });
        writeDevProfileProperties(srcResourcesDir, devProfile);
        createMavenPom(settings, profile, buildDir.resolve("pom.xml"), deps);
        if (request.mavenWrapper) {
            copyMavenWrapper();
        }
        if (request.camelWrapper) {
            copyCamelWrapper();
        }
        if (request.docker) {
            copyDockerFiles(buildDir.toString());
        }
        String appJar = "target" + File.separator + ids[1] + "-" + ids[2] + ".jar";
        copyReadme(buildDir.toString(), appJar);
        if (request.cleanExportDir || !ExportHelper.isCurrentDirOrParent(request.exportDir)) {
            // cleaning current dir can be a bit dangerous so only clean if explicit enabled
            // otherwise always clean export-dir to avoid stale data
            if (request.cleanExportDir) {
                String absPath = Paths.get(request.exportDir).toAbsolutePath().toString();
                if (!request.yes && !exportContext.confirm("Are you sure you want to delete " + absPath + "?")) {
                    return 1;
                }
            }
            ExportHelper.cleanExportDir(request.exportDir);
        }
        // copy to export dir and remove work dir
        PathUtils.copyDirectory(buildDir, Paths.get(request.exportDir));
        createDeferredSymlinks(buildDir, Paths.get(request.exportDir));
        PathUtils.deleteDirectory(buildDir);

        return 0;
    }

    private void createMavenPom(Path settings, Path profile, Path pom, Set<String> deps) throws Exception {
        String[] ids = request.gav.split(":");

        Properties prop = new CamelCaseOrderedProperties();
        RuntimeUtil.loadProperties(prop, settings);
        String sbVersion = request.camelSpringBootVersion != null
                ? request.camelSpringBootVersion
                : (request.camelVersion == null ? RuntimeType.main.version() : request.camelVersion);
        String repos = getMavenRepositories(settings, prop, sbVersion);

        CamelCatalog catalog
                = CatalogLoader.loadSpringBootCatalog(repos, sbVersion, sbVersion, null, request.mavenResolver.download());
        if (ObjectHelper.isEmpty(request.camelVersion)) {
            request.camelVersion = catalog.getLoadedVersion();
        }
        if (ObjectHelper.isEmpty(request.camelVersion)) {
            request.camelVersion = VersionHelper.extractCamelVersion();
        }

        // Check if catalog provides a custom template (backward compatibility)
        // Also check old .tmpl name for catalogs that haven't been updated
        InputStream catalogTemplate = catalog.loadResource("camel-jbang", request.pomTemplateName);
        if (catalogTemplate == null) {
            catalogTemplate = catalog.loadResource("camel-jbang", request.pomTemplateName.replace(".ftl", ".tmpl"));
        }
        if (catalogTemplate != null) {
            // Catalog provides a custom template - use legacy regex-based processing
            String context = IOHelper.loadText(catalogTemplate);
            IOHelper.close(catalogTemplate);
            context = processLegacyPomTemplate(context, ids, repos, deps, catalog);
            IOHelper.writeText(context, Files.newOutputStream(pom));
            return;
        }

        // Build template data model
        List<Map<String, Object>> depList = buildSpringBootDependencyList(deps, catalog);

        Map<String, Object> model = new HashMap<>();
        model.put("GroupId", ids[0]);
        model.put("ArtifactId", ids[1]);
        model.put("Version", ids[2]);
        model.put("SpringBootVersion", request.springBootVersion);
        model.put("JavaVersion", request.javaVersion);
        model.put("CamelSpringBootVersion",
                Objects.requireNonNullElseGet(request.camelSpringBootVersion, () -> request.camelVersion));
        model.put("ProjectBuildOutputTimestamp", this.getBuildMavenProjectDate());
        model.put("BuildProperties", formatBuildProperties());
        model.put("Repositories", buildRepositoryList(repos));
        model.put("Dependencies", depList);
        model.put("JibMavenPluginVersion", jibMavenPluginVersion(settings, prop));
        enrichParentPom(model);

        String context = TemplateHelper.processTemplate(request.pomTemplateName, model);
        IOHelper.writeText(context, Files.newOutputStream(pom));
    }

    private List<Map<String, Object>> buildSpringBootDependencyList(Set<String> deps, CamelCatalog catalog) {
        List<MavenGav> gavs = new ArrayList<>();
        for (String dep : deps) {
            MavenGav gav = parseMavenGav(dep);
            String gid = gav.getGroupId();
            String aid = gav.getArtifactId();

            // transform to camel-spring-boot starter GAV
            if ("org.apache.camel".equals(gid)) {
                ArtifactModel<?> am = catalog.modelFromMavenGAV("org.apache.camel.springboot", aid + "-starter", null);
                if (am != null) {
                    gav.setGroupId(am.getGroupId());
                    gav.setArtifactId(am.getArtifactId());
                    gav.setVersion(null);
                } else {
                    gav.setVersion(request.camelVersion);
                }
            }
            if ("org.springframework.boot".equals(gid)) {
                gav.setVersion(null);
            }
            gavs.add(gav);
        }

        gavs.sort(ExportHelper.mavenGavComparator());

        List<Map<String, Object>> result = new ArrayList<>();
        for (MavenGav gav : gavs) {
            Map<String, Object> dep = new HashMap<>();
            dep.put("groupId", gav.getGroupId());
            dep.put("artifactId", gav.getArtifactId());
            dep.put("version", gav.getVersion());
            dep.put("scope", gav.getScope());
            dep.put("isLib", "lib".equals(gav.getPackaging()));
            dep.put("isKameletsUtils", "camel-kamelets-utils".equals(gav.getArtifactId()));
            result.add(dep);
        }
        return result;
    }

    /**
     * Legacy processing for catalog-provided templates that use {{ .Name }} syntax.
     */
    private String processLegacyPomTemplate(
            String context, String[] ids, String repos, Set<String> deps, CamelCatalog catalog) {
        context = context.replaceAll("\\{\\{ \\.GroupId }}", ids[0]);
        context = context.replaceAll("\\{\\{ \\.ArtifactId }}", ids[1]);
        context = context.replaceAll("\\{\\{ \\.Version }}", ids[2]);
        context = context.replaceAll("\\{\\{ \\.SpringBootVersion }}", request.springBootVersion);
        context = context.replaceAll("\\{\\{ \\.JavaVersion }}", request.javaVersion);
        context = context.replaceAll("\\{\\{ \\.CamelVersion }}", request.camelVersion);
        context = context.replaceAll("\\{\\{ \\.CamelSpringBootVersion }}",
                Objects.requireNonNullElseGet(request.camelSpringBootVersion, () -> request.camelVersion));
        context = context.replaceFirst("\\{\\{ \\.ProjectBuildOutputTimestamp }}", this.getBuildMavenProjectDate());
        context = replaceBuildProperties(context);

        if (repos == null || repos.isEmpty()) {
            context = context.replaceFirst("\\{\\{ \\.MavenRepositories }}", "");
        } else {
            String s = legacyMavenRepositoriesAsPomXml(repos);
            context = context.replaceFirst("\\{\\{ \\.MavenRepositories }}", s);
        }

        List<MavenGav> gavs = new ArrayList<>();
        for (String dep : deps) {
            MavenGav gav = parseMavenGav(dep);
            String gid = gav.getGroupId();
            String aid = gav.getArtifactId();
            if ("org.apache.camel".equals(gid)) {
                ArtifactModel<?> am = catalog.modelFromMavenGAV("org.apache.camel.springboot", aid + "-starter", null);
                if (am != null) {
                    gav.setGroupId(am.getGroupId());
                    gav.setArtifactId(am.getArtifactId());
                    gav.setVersion(null);
                } else {
                    gav.setVersion(request.camelVersion);
                }
            }
            if ("org.springframework.boot".equals(gid)) {
                gav.setVersion(null);
            }
            gavs.add(gav);
        }
        gavs.sort(ExportHelper.mavenGavComparator());

        StringBuilder sb = new StringBuilder();
        for (MavenGav gav : gavs) {
            sb.append("        <dependency>\n");
            sb.append("            <groupId>").append(gav.getGroupId()).append("</groupId>\n");
            sb.append("            <artifactId>").append(gav.getArtifactId()).append("</artifactId>\n");
            if (gav.getVersion() != null) {
                sb.append("            <version>").append(gav.getVersion()).append("</version>\n");
            }
            if (gav.getScope() != null) {
                sb.append("            <scope>").append(gav.getScope()).append("</scope>\n");
            }
            if ("lib".equals(gav.getPackaging())) {
                sb.append("            <scope>system</scope>\n");
                sb.append("            <systemPath>\\$\\{project.basedir}/lib/").append(gav.getArtifactId()).append("-")
                        .append(gav.getVersion()).append(".jar</systemPath>\n");
            } else if ("camel-kamelets-utils".equals(gav.getArtifactId())) {
                sb.append("            <exclusions>\n");
                sb.append("                <exclusion>\n");
                sb.append("                    <groupId>org.apache.camel</groupId>\n");
                sb.append("                    <artifactId>*</artifactId>\n");
                sb.append("                </exclusion>\n");
                sb.append("            </exclusions>\n");
            }
            sb.append("        </dependency>\n");
        }
        context = context.replaceFirst("\\{\\{ \\.CamelDependencies }}", sb.toString());
        return context;
    }

    /**
     * Legacy method for backward compatibility with catalog-provided templates.
     */
    private static String legacyMavenRepositoriesAsPomXml(String repos) {
        List<Map<String, Object>> repoList = buildRepositoryList(repos);
        StringBuilder sb = new StringBuilder();
        sb.append("    <repositories>\n");
        for (Map<String, Object> r : repoList) {
            sb.append("        <repository>\n");
            sb.append("            <id>").append(r.get("id")).append("</id>\n");
            sb.append("            <url>").append(r.get("url")).append("</url>\n");
            if (Boolean.TRUE.equals(r.get("isSnapshot"))) {
                sb.append("            <releases>\n");
                sb.append("                <enabled>false</enabled>\n");
                sb.append("            </releases>\n");
                sb.append("            <snapshots>\n");
                sb.append("                <enabled>true</enabled>\n");
                sb.append("            </snapshots>\n");
            }
            sb.append("        </repository>\n");
        }
        sb.append("    </repositories>\n");
        sb.append("    <pluginRepositories>\n");
        for (Map<String, Object> r : repoList) {
            sb.append("        <pluginRepository>\n");
            sb.append("            <id>plugin-").append(r.get("id")).append("</id>\n");
            sb.append("            <url>").append(r.get("url")).append("</url>\n");
            if (Boolean.TRUE.equals(r.get("isSnapshot"))) {
                sb.append("            <releases>\n");
                sb.append("                <enabled>false</enabled>\n");
                sb.append("            </releases>\n");
                sb.append("            <snapshots>\n");
                sb.append("                <enabled>true</enabled>\n");
                sb.append("            </snapshots>\n");
            }
            sb.append("        </pluginRepository>\n");
        }
        sb.append("    </pluginRepositories>\n");
        return sb.toString();
    }

    @Override
    protected String getDockerfileTemplateName() {
        return "Dockerfile-spring-boot";
    }

    @Override
    protected Set<String> resolveDependencies(Path settings, Path profile) throws Exception {
        Set<String> answer = super.resolveDependencies(settings, profile);

        // remove out of the box dependencies
        answer.removeIf(s -> s.contains("camel-core"));

        boolean http = answer.stream().anyMatch(s -> s.contains("mvn:org.apache.camel:camel-platform-http"));
        if (hasOpenapi(answer) && !http) {
            // include http server if using openapi
            answer.add("mvn:org.apache.camel:camel-platform-http");
        }
        if (request.hawtio) {
            answer.add("mvn:org.apache.camel:camel-management");
            String hawtioArtifact = request.springBootVersion.startsWith("4.") ? "hawtio-springboot4" : "hawtio-springboot";
            answer.add("mvn:io.hawt:" + hawtioArtifact + ":" + request.hawtioVersion);
        }
        if (settingsFlag(settings, CamelJBangConstants.CONSOLE)) {
            // developer console (--console) as actuator endpoint (camel-console-starter)
            answer.add("mvn:org.apache.camel:camel-console");
            answer.add("mvn:org.apache.camel:camel-management");
        }

        return answer;
    }

    /**
     * The value of {@code management.endpoints.web.exposure.include} with the given actuator endpoint ids added to the
     * ids that are already exposed (without duplicates).
     */
    static String exposeActuatorEndpoints(String exposed, String... ids) {
        Set<String> answer = new LinkedHashSet<>();
        if (exposed != null) {
            if (exposed.contains("*")) {
                // all endpoints are exposed already
                return exposed;
            }
            for (String id : exposed.split(",")) {
                if (!id.isBlank()) {
                    answer.add(id.trim());
                }
            }
        }
        answer.addAll(Arrays.asList(ids));
        return String.join(",", answer);
    }

    private void createMainClassSource(Path srcJavaDir, String packageName, String mainClassname) throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("PackageName", packageName);
        model.put("MainClassname", mainClassname);

        String content = TemplateHelper.processTemplate("spring-boot-main.ftl", model);
        IOHelper.writeText(content, Files.newOutputStream(srcJavaDir.resolve(mainClassname + ".java")));
    }

    @Override
    protected void adjustJavaSourceFileLine(String line, OutputStream fos) throws Exception {
        if (line.startsWith("public class")
                && (line.contains("RouteBuilder") || line.contains("EndpointRouteBuilder"))) {
            fos.write("import org.springframework.stereotype.Component;\n\n"
                    .getBytes(StandardCharsets.UTF_8));
            fos.write("@Component\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    protected String applicationPropertyLine(String key, String value) {
        if (key.startsWith("camel.server.") || key.startsWith("camel.management.")) {
            // skip "camel.server." or "camel.management." as this is for camel-main only
            return null;
        }
        boolean camel44orOlder
                = request.camelSpringBootVersion != null && VersionHelper.isGE("4.4.0", request.camelSpringBootVersion);
        if (camel44orOlder) {
            // camel.main.x should be renamed to camel.springboot.x (for camel 4.4.x or older)
            if (key.startsWith("camel.main.")) {
                key = "camel.springboot." + key.substring(11);
            }
        }
        return super.applicationPropertyLine(key, value);
    }

}
