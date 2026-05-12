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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.apache.camel.dsl.jbang.core.commands.version.VersionList.CamelAndRuntimeVersions;
import org.apache.camel.tooling.maven.MavenArtifact;
import org.apache.camel.tooling.maven.MavenDownloader;
import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.json.DeserializationException;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.artifact.versioning.ComparableVersion;

import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.QUARKUS_EXTENSION_REGISTRY_BASE_URI;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.QUARKUS_GROUP_ID;
import static org.apache.camel.dsl.jbang.core.common.CamelJBangConstants.QUARKUS_VERSION;

/**
 * Helper for resolving Quarkus platform information from the Quarkus registry.
 */
public final class QuarkusHelper {

    public static final String QUARKUS_PLATFORM_URL_PROPERTY = "camel.jbang.quarkus.platform.url";

    private QuarkusHelper() {
    }

    public static Stream<CamelAndRuntimeVersions> listQuarkusPlatformVersions(
            Function<MavenGav, MavenArtifact> mavenResolver, String quarkusExtensioRegistryBaseUri) {
        JsonArray streams = fetchPlatformStreams(quarkusExtensioRegistryBaseUri, false);
        if (streams == null || streams.isEmpty()) {
            return Stream.empty();
        }
        return listQuarkusPlatformVersions(streams, mavenResolver);
    }

    static Stream<CamelAndRuntimeVersions> listQuarkusPlatformVersions(
            JsonArray streams,
            Function<MavenGav, MavenArtifact> mavenResolver) {
        return streams.stream()
                .map(s -> (JsonObject) s)
                .map(PlatformStream::of)
                .flatMap(platformStream -> platformStream.platformReleases().stream())
                .map(platformRelease -> {
                    List<String> versions
                            = platformRelease.findManagedVersions(mavenResolver, Ga.CAMEL_DIRECT, Ga.CAMEL_QUARKUS_DIRECT);
                    return new CamelAndRuntimeVersions(
                            versions.get(0), platformRelease.quarkusCamelBomGav.getVersion(), versions.get(1));
                });
    }

    /**
     * Finds the newest Quarkus platform BOM {@code g:a:v} using the same or newer {@code major.minor} Camel version as
     * the specified {@code camelVersion} by searching in Quarkus platform registry or returns the specified
     * {@code buildTimeQuarkusVersion} if no compatible Platform version can be found.
     * <p>
     * This is used by export/run commands to query the registry for the correct platform BOM version instead of using
     * the build-time constant. The registry may have a newer compatible version.
     *
     * @param  camelVersion                   if specified, the value of {@code --camel-version} CLI parameter or the
     *                                        Camel version of the currently running camel-jbang. Must not be
     *                                        {@code null}
     * @param  quarkusExtensioRegistryBaseUri
     * @param  downloader                     A {@link MavenDownloader} to use for accessing
     * @return                                the resolved platform BOM version from the registry, or the original
     *                                        buildTimeVersion if resolution fails
     */
    public static QuarkusPlatformBom findQuarkusPlatformBom(
            String camelVersion, Function<MavenGav, MavenArtifact> mavenResolver, String quarkusExtensioRegistryBaseUri) {
        if (camelVersion == null) {
            camelVersion = RuntimeType.main.version();
        }

        JsonArray streams = fetchPlatformStreams(quarkusExtensioRegistryBaseUri, true);
        if (streams == null || streams.isEmpty()) {
            return null;
        }
        Optional<QuarkusPlatformBom> resolved
                = findPlatformBom(streams, new MajorMinor(camelVersion), mavenResolver, quarkusExtensioRegistryBaseUri);
        return resolved.orElse(null);
    }

    public static String resolveCamelVersionFromQuarkusCamelBom(
            MavenGav quarkusCamelBom, Function<MavenGav, MavenArtifact> mavenResolver) {
        Path file = mavenResolver.apply(quarkusCamelBom).getFile().toPath();
        XPath xPath = XPathFactory.newInstance().newXPath();
        String expr = managedDependencyVersionXPath("org.apache.camel", "camel-direct");
        try (InputStream in = Files.newInputStream(file)) {
            String camelVersion = (String) xPath.evaluate(expr, new InputSource(in), XPathConstants.STRING);
            return camelVersion;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        } catch (XPathExpressionException e) {
            throw new RuntimeException("Could not evaluate " + expr + " on file " + file);
        }
    }

    /**
     * Fetches the platform streams array from the Quarkus platform registry.
     *
     * @return the streams JsonArray, or null if the registry is unreachable or the response is invalid
     */
    private static JsonArray fetchPlatformStreams(String quarkusExtensioRegistryBaseUri, boolean ltsAndLatestOnly) {
        final String quarkusPlatformUrl = quarkusExtensioRegistryBaseUri
                                          + (ltsAndLatestOnly ? "/client/platforms" : "/client/platforms/all")
                                          + (quarkusExtensioRegistryBaseUri.startsWith("file://") ? ".json" : "");
        try {
            final URI uri = new URI(quarkusPlatformUrl);
            if (uri.getScheme().equals("file")) {
                return deserialize(Files.readString(Path.of(uri)));
            }
            HttpClient hc = HttpClient.newHttpClient();
            HttpResponse<String> res = hc.send(
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(2))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                return deserialize(res.body());
            }
            return new JsonArray();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted when fetching from " + quarkusPlatformUrl, e);
        } catch (IOException e) {
            throw new RuntimeException("Could not fetch from " + quarkusPlatformUrl, e);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unvalid URI " + quarkusPlatformUrl, e);
        } catch (DeserializationException e) {
            throw new RuntimeException("Unvalid JSON input from " + quarkusPlatformUrl, e);
        }
    }

    private static JsonArray deserialize(String jsonString) throws DeserializationException {
        JsonObject json = (JsonObject) Jsoner.deserialize(jsonString);
        JsonArray platforms = json.getCollection("platforms");
        if (platforms == null || platforms.isEmpty()) {
            return null;
        }
        JsonObject platform = platforms.getMap(0);
        return platform.getCollection("streams");
    }

    /**
     *
     * @param  streams    the streams array from the registry
     * @param  majorMinor the major.minor version to match (e.g., "3.15")
     * @param  fieldName  the field name to extract from the release ("version" or "quarkus-core-version")
     * @return            the version string, or empty if not found
     */
    static Optional<QuarkusPlatformBom> findPlatformBom(
            JsonArray streams, MajorMinor wantedCamelVersion, Function<MavenGav, MavenArtifact> mavenResolver,
            String quarkusExtensioRegistryBaseUri) {
        return streams.stream()
                .map(s -> (JsonObject) s)
                .map(PlatformStream::of)
                .map(platformStream -> platformStream.platformReleases().stream().findFirst().orElse(null))
                .filter(platformRelease -> platformRelease != null)
                // stream of PlatformReleases having a quarkus-camel-bom
                .map(platformRelease -> CamelVersionInPlatformRelease.of(platformRelease, mavenResolver,
                        wantedCamelVersion, quarkusExtensioRegistryBaseUri))
                .sorted()
                .map(camelInPlatform -> camelInPlatform.toQuarkusPlatformBom())
                .findFirst();
    }

    public static String managedDependencyVersionXPath(String groupId, String artifactId) {
        return anyNs("project", "dependencyManagement", "dependencies", "dependency")
               + "[*[local-name()='groupId']/text()='" + groupId + "' and *[local-name()='artifactId']/text()='"
               + artifactId + "']"
               + anyNs("version")
               + "/text()";
    }

    /**
     * A generator of XPath 1.0 "any namespace" selector, such as {@code /*[local-name()='foo']/*[local-name()='bar']}.
     * In XPath 2.0, this would be just {@code /*:foo/*:bar}, but as of Java 25, there is only XPath 1.0 available in
     * the JDK so we have to use this workaround.
     *
     * @param  elements namespace-less element names
     * @return          am XPath 1.0 style selector
     */
    static String anyNs(String... elements) {
        StringBuilder sb = new StringBuilder();
        for (String e : elements) {
            sb.append("/*[local-name()='").append(e).append("']");
        }
        return sb.toString();
    }

    /**
     * Maven coordinates of Quarkus BOM, Quarkus Camel BOM and Quarkus Maven Plugin
     */
    public static record QuarkusPlatformBom(
            String groupId,
            String version,
            String camelVersion,
            String quarkusExtensioRegistryBaseUri) {

        /**
         * Put {@link #groupId} and {@link #version} to the given {@link Properties}.
         *
         * @param properties the destination
         */
        public void putTo(BiConsumer<String, String> properties) {
            properties.accept(QUARKUS_GROUP_ID, groupId);
            properties.accept(QUARKUS_VERSION, version);
            properties.accept(QUARKUS_EXTENSION_REGISTRY_BASE_URI, quarkusExtensioRegistryBaseUri);
        }

        /**
         * @return the Maven coordinates of Quarkus Camel Platform BOM
         */
        public MavenGav quarkusCamelBom() {
            return MavenGav.fromCoordinates(groupId, "quarkus-camel-bom", version, "pom", null);
        }

        /**
         * @return the Maven coordinates of Quarkus Platform BOM
         */
        public MavenGav quarkusBom() {
            return MavenGav.fromCoordinates(groupId, "quarkus-bom", version, "pom", null);
        }

        /**
         * @return the Maven coordinates of Quarkus Maven Plugin
         */
        public MavenGav quarkusMavenPlugin() {
            return MavenGav.fromCoordinates(groupId, "quarkus-maven-plugin", version, "pom", null);
        }
    }

    private record PlatformStream(String id, List<PlatformRelease> platformReleases) {
        static PlatformStream of(JsonObject json) {
            List<PlatformRelease> releases = json.getCollectionOrDefault("releases", List.of()).stream()
                    .map(o -> (JsonObject) o)
                    .map(PlatformRelease::of)
                    .filter(platformRelease -> platformRelease != null)
                    .toList();
            return new PlatformStream(Objects.requireNonNull(json.getString("id"), "stream.id"), List.copyOf(releases));
        }
    }

    private record PlatformRelease(ComparableVersion platformVersion, MavenGav quarkusCamelBomGav) {
        PlatformRelease(ComparableVersion platformVersion, MavenGav quarkusCamelBomGav) {
            this.platformVersion = Objects.requireNonNull(platformVersion, "platformVersion");
            this.quarkusCamelBomGav = Objects.requireNonNull(quarkusCamelBomGav, "quarkusCamelBomGav");
        }

        static PlatformRelease of(JsonObject json) {
            MavenGav quarkusCamelBomGav = json.getCollectionOrDefault("member-boms", List.of()).stream()
                    .map(o -> (String) o)
                    .filter(gav -> gav.contains(":quarkus-camel-bom:"))
                    .findFirst()
                    .map(gav -> {
                        String[] parts = gav.split(":");
                        // the extension registry uses a rather unusual format g:a:[c]:t:v, so we have to parse it manually
                        return MavenGav.fromCoordinates(parts[0], parts[1], parts[4], parts[3], parts[2]);
                    })
                    .orElse(null);
            if (quarkusCamelBomGav == null) {
                /* Some platform releases do not contain Camel Quarkus */
                return null;
            }
            return new PlatformRelease(
                    new ComparableVersion(Objects.requireNonNull(json.getString("version"), "release.version")),
                    quarkusCamelBomGav);
        }

        public List<String> findManagedVersions(Function<MavenGav, MavenArtifact> mavenResolver, Ga... gas) {
            final MavenArtifact artifact = mavenResolver.apply(quarkusCamelBomGav);
            Path file = artifact.getFile().toPath();
            if (!Files.isRegularFile(file)) {
                throw new IllegalStateException(file + " should exist for " + quarkusCamelBomGav.toMavenResolverString());
            }
            XPath xPath = XPathFactory.newInstance().newXPath();
            DocumentBuilderFactory dbf = XmlHelper.createDocumentBuilderFactory();
            Document dom;
            try (InputStream in = Files.newInputStream(file)) {
                DocumentBuilder db = dbf.newDocumentBuilder();
                dom = db.parse(in);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            } catch (ParserConfigurationException | SAXException e) {
                throw new RuntimeException("Could not parse file " + file);
            }
            return Stream.of(gas)
                    .map(ga -> managedDependencyVersionXPath(ga.groupId, ga.artifactId))
                    .map(expr -> {
                        try {
                            return (String) xPath.evaluate(expr, dom, XPathConstants.STRING);
                        } catch (XPathExpressionException e) {
                            throw new RuntimeException("Could not evaluate " + expr + " on file " + file);
                        }
                    })
                    .toList();
        }
    }

    record CamelVersionInPlatformRelease(String camelVersion, MajorMinor camelMajorMinor, BigInteger versionDistance,
            ComparableVersion platformVersion,
            MavenGav quarkusCamelPlatfromGav,
            String quarkusExtensioRegistryBaseUri) implements Comparable<CamelVersionInPlatformRelease> {

        private static final Comparator<CamelVersionInPlatformRelease> COMPARATOR
                = Comparator.comparing((CamelVersionInPlatformRelease rel) -> rel.versionDistance())
                        .thenComparing(CamelVersionInPlatformRelease::platformVersion, Comparator.reverseOrder());

        static CamelVersionInPlatformRelease of(
                PlatformRelease platformRelease,
                Function<MavenGav, MavenArtifact> mavenResolver,
                MajorMinor wantedCamelMajorMinor,
                String quarkusExtensioRegistryBaseUri) {
            String camelVersion = platformRelease.findManagedVersions(mavenResolver, Ga.CAMEL_DIRECT).get(0);
            MajorMinor camelMajorMinor = new MajorMinor(camelVersion);
            BigInteger distance = camelMajorMinor.distanceTo(wantedCamelMajorMinor);
            return new CamelVersionInPlatformRelease(
                    camelVersion,
                    camelMajorMinor,
                    distance,
                    platformRelease.platformVersion,
                    platformRelease.quarkusCamelBomGav,
                    quarkusExtensioRegistryBaseUri);
        }

        public QuarkusPlatformBom toQuarkusPlatformBom() {
            return new QuarkusPlatformBom(
                    quarkusCamelPlatfromGav.getGroupId(), quarkusCamelPlatfromGav.getVersion(), camelVersion,
                    quarkusExtensioRegistryBaseUri);
        }

        @Override
        public int compareTo(CamelVersionInPlatformRelease o) {
            return COMPARATOR.compare(this, o);
        }
    }

    static record Ga(String groupId, String artifactId) {

        public static final Ga CAMEL_DIRECT = new Ga("org.apache.camel", "camel-direct");
        public static final Ga CAMEL_QUARKUS_DIRECT = new Ga("org.apache.camel.quarkus", "camel-quarkus-direct");
    }

    static class MajorMinor {
        private final String source;
        private final int major;
        private final int minor;
        private final int majorMinor;

        public MajorMinor(String version) {
            this.source = Objects.requireNonNull(version, "version");
            String[] segments = version.split("\\.");
            this.major = segments.length >= 1 ? Integer.parseInt(segments[0]) : 0;
            this.minor = segments.length >= 2 ? Integer.parseInt(segments[1]) : 0;
            if ((major & 0xFFFF0000) != 0) {
                throw new IllegalArgumentException("Cannot handle major longer than 16 bits; found" + major);
            }
            if ((minor & 0xFFFF0000) != 0) {
                throw new IllegalArgumentException("Cannot handle minor longer than 16 bits; found" + minor);
            }
            this.majorMinor = (major << 16) | minor;
        }

        @Override
        public String toString() {
            return source;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof MajorMinor) && source.equals(((MajorMinor) o).source);
        }

        public int getMajor() {
            return major;
        }

        public int getMinor() {
            return minor;
        }

        @Override
        public int hashCode() {
            return source.hashCode();
        }

        /**
         * A distance between this {@link MajorMinor} and other {@link MajorMinor} can be used as a key for ordering.
         *
         * The returned value of each of the following version groups is guaranteed to be larger than the one of the
         * previous group.
         * <ol>
         * <li>perfect match -> 0
         * <li>same major, newer other minor, closer is higher
         * <li>same major, older other minor, newer is higher
         * <li>other newer major, closer is higher
         * <li>other older major, newer is higher
         * </ol>
         * Example: for {@code thisVersion} 2.3, the distances computed for the following {@code otherVersion} would
         * give the following ordering:
         * <ul>
         * <li>2.3 (perfect match)
         * <li>2.4 (same major, closest newer minor)
         * <li>2.5 (same major, further newer minor)
         * <li>2.2 (same major, older minor)
         * <li>2.1 (same major, even older minor)
         * <li>3.0 (closest newer major)
         * <li>3.1 (less close newer major)
         * <li>4.0 (even less close newer major)
         * <li>4.1 (even less close newer major)
         * <li>1.2 (closest older major)
         * <li>1.1 (less close older major)
         * <li>0.2 (even less close older major)
         * <li>0.1 (even less close older major)
         * </ul>
         *
         * @param  thisVersion
         * @param  otherVersion
         * @return
         */
        BigInteger distanceTo(MajorMinor other) {
            if (this.equals(other)) {
                return BigInteger.ZERO;
            }

            int distance = other.majorMinor - majorMinor;
            if (major == other.major) {
                // if major is the same, then distance has maximum length of 16 bits
                if (minor <= other.minor) {
                    // case 1: same major, newer minor
                    // occupy the right most 16 bytes
                    return BigInteger.valueOf(distance);
                } else {
                    // case 2: same major, older minor
                    // make it more distant by shifting left by 16 bits
                    // we occupy the right most 32 bytes (16 bit value shifted by 16)
                    return BigInteger.valueOf(Math.abs(distance)).shiftLeft(16);
                }
            } else if (major < other.major) {
                // case 3: newer major
                // make it more distant than cases 1 and 2 by shifting left
                // we have to shift by 32 so that we do not clash with cases 1 and 2
                // we occupy the right most 64 bytes (32 bit value shifted by 32)
                return BigInteger.valueOf(distance).shiftLeft(32);
            } else {
                // case 4: older major
                // make it more distant than cases 1, 2 and 3 by shifting left
                // we have to shift by 64 so that we do not clash with cases 1, 2 and 3
                // we occupy the right most 80 bytes (32 bit value shifted by 64) a long value would not be enough
                return BigInteger.valueOf(Math.abs(distance)).shiftLeft(64);
            }
        }
    }

}
