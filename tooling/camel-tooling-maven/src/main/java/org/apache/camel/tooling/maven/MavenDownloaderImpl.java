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
package org.apache.camel.tooling.maven;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import eu.maveniverse.maven.mima.context.Runtimes;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.HomeHelper;
import org.apache.camel.util.StopWatch;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MIMA-based implementation of MavenDownloader. Uses MIMA (Minimal Maven) library to simplify Maven Resolver usage.
 * This eliminates ~650 lines of manual DIRegistry component wiring.
 */
public class MavenDownloaderImpl extends ServiceSupport implements MavenDownloader {

    public static final Logger LOG = LoggerFactory.getLogger(MavenDownloaderImpl.class);

    public static final String MAVEN_CENTRAL_REPO = "https://repo1.maven.org/maven2";
    public static final String APACHE_SNAPSHOT_REPO = "https://repository.apache.org/snapshots";

    private static final RepositoryPolicy POLICY_DEFAULT = new RepositoryPolicy(
            true, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_WARN);
    private static final RepositoryPolicy POLICY_FRESH = new RepositoryPolicy(
            true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_WARN);
    private static final RepositoryPolicy POLICY_DISABLED = new RepositoryPolicy(
            false, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_IGNORE);

    // Configuration
    private boolean mavenCentralEnabled = true;
    private boolean mavenApacheSnapshotEnabled = true;
    private String mavenSettings;
    private String mavenSettingsSecurity;
    private String repos;
    private boolean fresh;
    private boolean offline;
    private RemoteArtifactDownloadListener remoteArtifactDownloadListener;
    private RepositoryResolver repositoryResolver;

    // MIMA context (standalone mode only)
    private Context mimaContext;

    // Maven Resolver components
    private RepositorySystem repositorySystem;
    private RepositorySystemSession repositorySystemSession;
    private Settings settings;

    // Embedded mode flag
    private boolean embeddedMode;

    // Repositories
    private final List<RemoteRepository> remoteRepositories = new ArrayList<>();
    private RemoteRepository centralRepository;
    private RemoteRepository centralResolutionRepository;
    private RemoteRepository apacheSnapshotsRepository;
    private RemoteRepository apacheSnapshotsResolutionRepository;
    private RepositoryPolicy defaultPolicy;
    private boolean apacheSnapshotsIncluded;
    private AtomicInteger customRepositoryCounter = new AtomicInteger(1);

    /**
     * Default constructor for standalone mode. MIMA will be used to create RepositorySystem and
     * RepositorySystemSession.
     */
    public MavenDownloaderImpl() {
        this.embeddedMode = false;
    }

    /**
     * Constructor for embedded mode (Maven plugin context). When using this constructor, MIMA is not used - the
     * provided components are used directly.
     */
    public MavenDownloaderImpl(RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession) {
        this.repositorySystem = repositorySystem;
        this.repositorySystemSession = repositorySystemSession;
        this.embeddedMode = true;
    }

    /**
     * Constructor for embedded mode (Maven plugin context) with Settings. When using this constructor, MIMA is not used
     * - the provided components are used directly. Settings are used to extract repositories from active profiles.
     */
    public MavenDownloaderImpl(RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession,
                               Settings settings) {
        this.repositorySystem = repositorySystem;
        this.repositorySystemSession = repositorySystemSession;
        this.settings = settings;
        this.embeddedMode = true;
    }

    @Override
    protected void doBuild() {
        // Initialize repository resolver if not already set
        if (repositoryResolver == null) {
            repositoryResolver = new DefaultRepositoryResolver();
            repositoryResolver.build();
        }

        defaultPolicy = fresh ? POLICY_FRESH : POLICY_DEFAULT;

        if (embeddedMode) {
            LOG.debug("MavenDownloader in embedded mode (Maven plugin)");
            configureRepositoriesForEmbeddedMode();
        } else {
            LOG.debug("MavenDownloader in standalone mode (using MIMA)");
            configureMIMA();
        }
    }

    private void configureRepositoriesForEmbeddedMode() {
        // In embedded mode, repositories are configured from the Maven session
        // We just need to set up our custom repositories if specified
        List<RemoteRepository> originalRepositories = new ArrayList<>();

        if (mavenCentralEnabled) {
            centralRepository = new RemoteRepository.Builder("central", "default", MAVEN_CENTRAL_REPO)
                    .setReleasePolicy(defaultPolicy)
                    .setSnapshotPolicy(POLICY_DISABLED)
                    .build();
            originalRepositories.add(centralRepository);
        }

        if (mavenApacheSnapshotEnabled) {
            apacheSnapshotsRepository = new RemoteRepository.Builder("apache-snapshot", "default", APACHE_SNAPSHOT_REPO)
                    .setReleasePolicy(POLICY_DISABLED)
                    .setSnapshotPolicy(defaultPolicy)
                    .build();
        }

        // Add custom repositories from repos parameter
        if (repos != null) {
            Set<String> urls = Arrays.stream(repos.split("\\s*,\\s*")).collect(Collectors.toSet());
            configureRepositories(originalRepositories, urls);
        }

        // Add repositories from active profiles in settings.xml
        if (settings != null) {
            Set<String> repositoryURLs = originalRepositories.stream()
                    .map(RemoteRepository::getUrl)
                    .collect(Collectors.toSet());

            for (String profileId : settings.getActiveProfiles()) {
                org.apache.maven.settings.Profile profile = settings.getProfilesAsMap().get(profileId);
                if (profile != null) {
                    for (org.apache.maven.settings.Repository repo : profile.getRepositories()) {
                        try {
                            URL url = URI.create(repo.getUrl()).toURL();
                            if (repositoryURLs.add(repo.getUrl())) {
                                if (mavenApacheSnapshotEnabled && url.getHost().equals("repository.apache.org")
                                        && url.getPath().startsWith("/snapshots")) {
                                    // Use preconfigured Apache Snapshots repository
                                    apacheSnapshotsIncluded = true;
                                    originalRepositories.add(apacheSnapshotsRepository);
                                } else {
                                    RemoteRepository.Builder rb
                                            = new RemoteRepository.Builder(repo.getId(), repo.getLayout(), repo.getUrl());
                                    if (repo.getReleases() == null) {
                                        rb.setReleasePolicy(defaultPolicy);
                                    } else {
                                        String updatePolicy = repo.getReleases().getUpdatePolicy() == null
                                                ? RepositoryPolicy.UPDATE_POLICY_DAILY : repo.getReleases().getUpdatePolicy();
                                        String checksumPolicy = repo.getReleases().getChecksumPolicy() == null
                                                ? RepositoryPolicy.CHECKSUM_POLICY_WARN
                                                : repo.getReleases().getChecksumPolicy();
                                        rb.setReleasePolicy(new RepositoryPolicy(
                                                repo.getReleases().isEnabled(),
                                                updatePolicy, checksumPolicy));
                                    }
                                    if (repo.getSnapshots() == null) {
                                        rb.setSnapshotPolicy(POLICY_DISABLED);
                                    } else {
                                        String updatePolicy = repo.getSnapshots().getUpdatePolicy() == null
                                                ? RepositoryPolicy.UPDATE_POLICY_DAILY
                                                : repo.getSnapshots().getUpdatePolicy();
                                        String checksumPolicy = repo.getSnapshots().getChecksumPolicy() == null
                                                ? RepositoryPolicy.CHECKSUM_POLICY_WARN
                                                : repo.getSnapshots().getChecksumPolicy();
                                        rb.setSnapshotPolicy(new RepositoryPolicy(
                                                repo.getSnapshots().isEnabled(),
                                                updatePolicy, checksumPolicy));
                                    }
                                    originalRepositories.add(rb.build());
                                    LOG.debug("Added repository from settings.xml profile {}: {}", profileId, repo.getId());
                                }
                            }
                        } catch (Exception e) {
                            LOG.warn("Failed to add repository {} from profile {}: {}", repo.getId(), profileId,
                                    e.getMessage());
                        }
                    }
                }
            }
        }

        // Apply mirroring/proxying
        remoteRepositories.addAll(repositorySystem.newResolutionRepositories(repositorySystemSession,
                originalRepositories));

        // Find central repository after mirroring
        if (centralRepository != null && !remoteRepositories.isEmpty()) {
            for (RemoteRepository repo : remoteRepositories) {
                if ("central".equals(repo.getId())) {
                    centralResolutionRepository = repo;
                    break;
                }
            }
        }

        if (mavenApacheSnapshotEnabled && !apacheSnapshotsIncluded) {
            apacheSnapshotsResolutionRepository = repositorySystem.newResolutionRepositories(repositorySystemSession,
                    Collections.singletonList(apacheSnapshotsRepository)).get(0);
        }
    }

    private void configureMIMA() {
        // Validate and locate Maven settings files
        validateMavenSettingsLocations();

        // Create MIMA runtime - automatically selects embedded-maven or standalone-static
        Runtime mimaRuntime = Runtimes.INSTANCE.getRuntime();

        // Build context overrides for customization
        ContextOverrides.Builder overridesBuilder = ContextOverrides.create();

        // Configure Maven settings support
        if (mavenSettings != null || mavenSettingsSecurity != null) {
            // Enable user settings processing
            overridesBuilder.withUserSettings(true);

            // Override settings.xml location if specified
            if (mavenSettings != null) {
                overridesBuilder.withUserSettingsXmlOverride(new File(mavenSettings).toPath());
            }

            // Override settings-security.xml location if specified
            if (mavenSettingsSecurity != null) {
                overridesBuilder.withUserSettingsSecurityXmlOverride(new File(mavenSettingsSecurity).toPath());
            }
        }

        // Configure offline mode
        if (offline) {
            LOG.info("MavenDownloader in offline mode");
            overridesBuilder.offline(true);
        }

        // Configure fresh mode (update policy)
        if (fresh) {
            overridesBuilder.snapshotUpdatePolicy(ContextOverrides.SnapshotUpdatePolicy.ALWAYS);
        }

        // Create MIMA context - this replaces ~650 lines of DIRegistry code!
        mimaContext = mimaRuntime.create(overridesBuilder.build());

        // Get RepositorySystem and RepositorySystemSession from MIMA
        repositorySystem = mimaContext.repositorySystem();
        repositorySystemSession = mimaContext.repositorySystemSession();

        // Configure repositories (same as embedded mode)
        List<RemoteRepository> originalRepositories = new ArrayList<>();

        if (mavenCentralEnabled) {
            centralRepository = new RemoteRepository.Builder("central", "default", MAVEN_CENTRAL_REPO)
                    .setReleasePolicy(defaultPolicy)
                    .setSnapshotPolicy(POLICY_DISABLED)
                    .build();
            originalRepositories.add(centralRepository);
        }

        if (mavenApacheSnapshotEnabled) {
            apacheSnapshotsRepository = new RemoteRepository.Builder("apache-snapshot", "default", APACHE_SNAPSHOT_REPO)
                    .setReleasePolicy(POLICY_DISABLED)
                    .setSnapshotPolicy(defaultPolicy)
                    .build();
        }

        // Add custom repositories from repos parameter
        if (repos != null) {
            Set<String> urls = Arrays.stream(repos.split("\\s*,\\s*")).collect(Collectors.toSet());
            configureRepositories(originalRepositories, urls);
        }

        // Apply mirroring/proxying
        remoteRepositories.addAll(repositorySystem.newResolutionRepositories(repositorySystemSession,
                originalRepositories));

        // Find central repository after mirroring
        if (centralRepository != null && !remoteRepositories.isEmpty()) {
            for (RemoteRepository repo : remoteRepositories) {
                if ("central".equals(repo.getId())) {
                    centralResolutionRepository = repo;
                    break;
                }
            }
        }

        if (mavenApacheSnapshotEnabled && !apacheSnapshotsIncluded) {
            apacheSnapshotsResolutionRepository = repositorySystem.newResolutionRepositories(repositorySystemSession,
                    Collections.singletonList(apacheSnapshotsRepository)).get(0);
        }
    }

    private void validateMavenSettingsLocations() {
        if (mavenSettingsSecurity != null && !new File(mavenSettingsSecurity).isFile()) {
            LOG.warn("Can't access {}. Skipping Maven settings-security.xml configuration.", mavenSettingsSecurity);
            mavenSettingsSecurity = null;
        }

        boolean skip = false;
        if ("false".equalsIgnoreCase(mavenSettings)) {
            // no implicit settings
            mavenSettings = null;
            // disable the settings-security.xml too
            mavenSettingsSecurity = null;
            skip = true;
        } else if (mavenSettings == null) {
            // implicit settings
            String m2settings = HomeHelper.resolveHomeDir() + File.separator + ".m2"
                                + File.separator + "settings.xml";
            if (new File(m2settings).isFile()) {
                mavenSettings = m2settings;
            }
        } else {
            if (!new File(mavenSettings).isFile()) {
                LOG.warn("Can't access {}. Skipping Maven settings.xml configuration.", mavenSettings);
                mavenSettings = null;
            }
        }

        if (!skip) {
            if (mavenSettingsSecurity == null) {
                // implicit security settings
                String m2settingsSecurity = HomeHelper.resolveHomeDir() + File.separator + ".m2"
                                            + File.separator + "settings-security.xml";
                if (new File(m2settingsSecurity).isFile()) {
                    mavenSettingsSecurity = m2settingsSecurity;
                }
            } else {
                if (!new File(mavenSettingsSecurity).isFile()) {
                    LOG.warn("Can't access {}. Skipping Maven settings-security.xml configuration.",
                            mavenSettingsSecurity);
                    mavenSettingsSecurity = null;
                }
            }
        }
    }

    /**
     * Helper method to configure custom repositories from URLs. Translates repository URLs to RemoteRepository
     * instances.
     */
    private void configureRepositories(List<RemoteRepository> repositories, Set<String> urls) {
        urls.forEach(repo -> {
            try {
                repo = repositoryResolver.resolveRepository(repo);
                if (repo != null && !repo.isBlank()) {
                    URL url = URI.create(repo).toURL();
                    if (mavenCentralEnabled && url.getHost().equals("repo1.maven.org")) {
                        // Maven Central is always used, so skip it
                        return;
                    }
                    if (mavenApacheSnapshotEnabled && url.getHost().equals("repository.apache.org")
                            && url.getPath().contains("/snapshots")) {
                        // Apache Snapshots added, so we'll use our own definition of this repository
                        repositories.add(apacheSnapshotsRepository);
                        apacheSnapshotsIncluded = true;
                    } else {
                        // both snapshots and releases allowed for custom repos
                        String id = "custom" + customRepositoryCounter.getAndIncrement();
                        repositories.add(new RemoteRepository.Builder(id, "default", repo)
                                .setReleasePolicy(defaultPolicy)
                                .setSnapshotPolicy(defaultPolicy)
                                .build());
                    }
                }
            } catch (MalformedURLException e) {
                LOG.warn("Cannot use {} URL: {}. Skipping.", repo, e.getMessage(), e);
            }
        });
    }

    @Override
    protected void doInit() {
        // Nothing to do
    }

    @Override
    protected void doStop() throws Exception {
        if (mimaContext != null) {
            mimaContext.close();
        }
    }

    // ========== Resolution Methods ==========

    @Override
    public List<MavenArtifact> resolveArtifacts(
            List<String> dependencyGAVs, Set<String> extraRepositories,
            boolean transitively, boolean useApacheSnapshots)
            throws MavenResolutionException {
        return resolveArtifacts(null, dependencyGAVs, extraRepositories, transitively, useApacheSnapshots);
    }

    @Override
    public List<MavenArtifact> resolveArtifacts(
            String rootGav,
            List<String> dependencyGAVs, Set<String> extraRepositories,
            boolean transitively, boolean useApacheSnapshots)
            throws MavenResolutionException {

        StopWatch watch = new StopWatch();

        List<RemoteRepository> repositories = new ArrayList<>(remoteRepositories);
        if (useApacheSnapshots && apacheSnapshotsResolutionRepository != null) {
            repositories.add(apacheSnapshotsResolutionRepository);
        }

        // Add extra repositories if specified
        if (extraRepositories != null && !extraRepositories.isEmpty()) {
            List<RemoteRepository> extraRepos = new ArrayList<>();
            configureRepositories(extraRepos, extraRepositories);
            List<RemoteRepository> resolvedExtraRepos = repositorySystem.newResolutionRepositories(
                    repositorySystemSession, extraRepos);
            repositories.addAll(resolvedExtraRepos);
        }

        List<MavenArtifact> result = new ArrayList<>();

        try {
            if (transitively) {
                // Transitive resolution
                CollectRequest collectRequest = new CollectRequest();

                if (rootGav != null) {
                    collectRequest.setRoot(new Dependency(new DefaultArtifact(rootGav), null));
                }

                for (String gav : dependencyGAVs) {
                    collectRequest.addDependency(new Dependency(new DefaultArtifact(gav), null));
                }

                collectRequest.setRepositories(repositories);

                DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, new AcceptAllDependencyFilter());

                // Add download listener if configured
                if (remoteArtifactDownloadListener != null) {
                    DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(repositorySystemSession);
                    session.setRepositoryListener(new AbstractRepositoryListener() {
                        @Override
                        public void artifactDownloading(RepositoryEvent event) {
                            Artifact artifact = event.getArtifact();
                            RemoteRepository repo = event.getRepository() instanceof RemoteRepository
                                    ? (RemoteRepository) event.getRepository()
                                    : null;
                            remoteArtifactDownloadListener.artifactDownloading(artifact.getGroupId(),
                                    artifact.getArtifactId(), artifact.getVersion(),
                                    repo != null ? repo.getId() : "unknown",
                                    repo != null ? repo.getUrl() : "unknown");
                        }

                        @Override
                        public void artifactDownloaded(RepositoryEvent event) {
                            Artifact artifact = event.getArtifact();
                            RemoteRepository repo = event.getRepository() instanceof RemoteRepository
                                    ? (RemoteRepository) event.getRepository()
                                    : null;
                            remoteArtifactDownloadListener.artifactDownloaded(artifact.getGroupId(),
                                    artifact.getArtifactId(), artifact.getVersion(),
                                    repo != null ? repo.getId() : "unknown",
                                    repo != null ? repo.getUrl() : "unknown",
                                    0L); // elapsed time not available from event
                        }
                    });

                    DependencyResult dependencyResult = repositorySystem.resolveDependencies(session, dependencyRequest);
                    dependencyResult.getArtifactResults().forEach(ar -> {
                        Artifact artifact = ar.getArtifact();
                        MavenGav gav = MavenGav.fromCoordinates(
                                artifact.getGroupId(), artifact.getArtifactId(),
                                artifact.getVersion(), artifact.getExtension(), artifact.getClassifier());
                        result.add(new MavenArtifact(gav, artifact.getFile()));
                    });
                } else {
                    DependencyResult dependencyResult = repositorySystem.resolveDependencies(repositorySystemSession,
                            dependencyRequest);
                    dependencyResult.getArtifactResults().forEach(ar -> {
                        Artifact artifact = ar.getArtifact();
                        MavenGav gav = MavenGav.fromCoordinates(
                                artifact.getGroupId(), artifact.getArtifactId(),
                                artifact.getVersion(), artifact.getExtension(), artifact.getClassifier());
                        result.add(new MavenArtifact(gav, artifact.getFile()));
                    });
                }
            } else {
                // Non-transitive resolution
                List<ArtifactRequest> requests = new ArrayList<>();
                for (String gav : dependencyGAVs) {
                    ArtifactRequest request = new ArtifactRequest();
                    request.setArtifact(new DefaultArtifact(gav));
                    request.setRepositories(repositories);
                    requests.add(request);
                }

                List<ArtifactResult> results = repositorySystem.resolveArtifacts(repositorySystemSession, requests);
                results.forEach(ar -> {
                    Artifact artifact = ar.getArtifact();
                    MavenGav gav = MavenGav.fromCoordinates(
                            artifact.getGroupId(), artifact.getArtifactId(),
                            artifact.getVersion(), artifact.getExtension(), artifact.getClassifier());
                    result.add(new MavenArtifact(gav, artifact.getFile()));
                });
            }

            LOG.debug("Resolved {} artifacts in {}", result.size(), watch.taken());
            return result;

        } catch (DependencyResolutionException | ArtifactResolutionException e) {
            MavenResolutionException mre = new MavenResolutionException(e.getMessage(), e);
            repositories.forEach(r -> mre.getRepositories().add(r.getUrl()));
            throw mre;
        }
    }

    @Override
    public List<MavenGav> resolveAvailableVersions(String groupId, String artifactId, String repository)
            throws MavenResolutionException {
        MetadataRequest req = new MetadataRequest();
        List<MavenGav> gavs = new ArrayList<>();

        try {
            if (repository == null) {
                req.setRepository(centralResolutionRepository);
            } else {
                String id = "custom" + customRepositoryCounter.getAndIncrement();
                RemoteRepository custom = new RemoteRepository.Builder(id, "default", repository)
                        .setReleasePolicy(defaultPolicy)
                        .setSnapshotPolicy(defaultPolicy)
                        .build();

                // simply configure them in addition to the default repositories
                List<RemoteRepository> customResolutionRepository = repositorySystem.newResolutionRepositories(
                        repositorySystemSession, Collections.singletonList(custom));

                req.setRepository(customResolutionRepository.get(0));
            }

            req.setFavorLocalRepository(false);
            req.setMetadata(new org.eclipse.aether.metadata.DefaultMetadata(
                    groupId, artifactId, "maven-metadata.xml",
                    org.eclipse.aether.metadata.Metadata.Nature.RELEASE));

            List<MetadataResult> result = repositorySystem.resolveMetadata(repositorySystemSession, List.of(req));
            for (MetadataResult mr : result) {
                if (mr.isResolved() && mr.getMetadata().getFile() != null) {
                    File f = mr.getMetadata().getFile();
                    if (f.exists() && f.isFile()) {
                        MetadataXpp3Reader reader = new MetadataXpp3Reader();
                        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(f))) {
                            Metadata md = reader.read(is);
                            Versioning versioning = md.getVersioning();
                            if (versioning != null) {
                                List<String> versions = versioning.getVersions();
                                if (versions != null) {
                                    for (String v : versions) {
                                        if (v != null) {
                                            gavs.add(MavenGav.fromCoordinates(groupId, artifactId, v, null, null));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return gavs;
        } catch (Exception e) {
            String msg = "Cannot resolve available versions in " + req.getRepository().getUrl();
            MavenResolutionException mre = new MavenResolutionException(msg, e);
            mre.getRepositories().add(req.getRepository().getUrl());
            throw mre;
        }
    }

    // ========== Configuration Methods ==========

    @Override
    public void setMavenSettingsLocation(String mavenSettings) {
        this.mavenSettings = mavenSettings;
    }

    @Override
    public void setMavenSettingsSecurityLocation(String mavenSettingsSecurity) {
        this.mavenSettingsSecurity = mavenSettingsSecurity;
    }

    public void setRepos(String repos) {
        this.repos = repos;
    }

    public void setFresh(boolean fresh) {
        this.fresh = fresh;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public void setMavenCentralEnabled(boolean mavenCentralEnabled) {
        this.mavenCentralEnabled = mavenCentralEnabled;
    }

    public boolean isMavenCentralEnabled() {
        return mavenCentralEnabled;
    }

    public void setMavenApacheSnapshotEnabled(boolean mavenApacheSnapshotEnabled) {
        this.mavenApacheSnapshotEnabled = mavenApacheSnapshotEnabled;
    }

    public boolean isMavenApacheSnapshotEnabled() {
        return mavenApacheSnapshotEnabled;
    }

    @Override
    public MavenDownloader customize(String localRepository, int connectTimeout, int requestTimeout) {
        // Create a copy with most configuration shared
        MavenDownloaderImpl copy = new MavenDownloaderImpl();
        copy.repositorySystem = repositorySystem;
        copy.remoteRepositories.addAll(remoteRepositories);
        copy.apacheSnapshotsRepository = apacheSnapshotsRepository;
        copy.apacheSnapshotsResolutionRepository = apacheSnapshotsResolutionRepository;
        copy.defaultPolicy = defaultPolicy;
        copy.mavenSettings = mavenSettings;
        copy.mavenSettingsSecurity = mavenSettingsSecurity;
        copy.repos = repos;
        copy.fresh = fresh;
        copy.apacheSnapshotsIncluded = apacheSnapshotsIncluded;
        copy.customRepositoryCounter = customRepositoryCounter;
        copy.repositoryResolver = repositoryResolver;
        copy.offline = offline;

        // Create a new session with custom settings (session can't be shared)
        DefaultRepositorySystemSession rssCopy = new DefaultRepositorySystemSession(repositorySystemSession);
        rssCopy.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, connectTimeout);
        rssCopy.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, requestTimeout);

        // Set custom local repository
        try {
            LocalRepositoryManager lrm = repositorySystem.newLocalRepositoryManager(rssCopy,
                    new LocalRepository(localRepository));
            rssCopy.setLocalRepositoryManager(lrm);
        } catch (Exception e) {
            LOG.warn("Failed to set custom local repository: {}", e.getMessage(), e);
        }

        copy.repositorySystemSession = rssCopy;

        return copy;
    }

    public void setRepositoryResolver(RepositoryResolver repositoryResolver) {
        this.repositoryResolver = repositoryResolver;
    }

    public RepositoryResolver getRepositoryResolver() {
        return repositoryResolver;
    }

    public void setRemoteArtifactDownloadListener(RemoteArtifactDownloadListener remoteArtifactDownloadListener) {
        this.remoteArtifactDownloadListener = remoteArtifactDownloadListener;
    }

    // ========== Helper Classes ==========

    private static class AcceptAllDependencyFilter implements DependencyFilter {
        @Override
        public boolean accept(
                org.eclipse.aether.graph.DependencyNode node,
                List<org.eclipse.aether.graph.DependencyNode> parents) {
            return true;
        }
    }

}
