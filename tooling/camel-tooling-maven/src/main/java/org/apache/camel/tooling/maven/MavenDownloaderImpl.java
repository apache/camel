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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.tooling.maven.support.DIRegistry;
import org.apache.camel.util.StopWatch;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultModelCacheFactory;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.ModelCacheFactory;
import org.apache.maven.repository.internal.PluginsMetadataGeneratorFactory;
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory;
import org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.crypto.DefaultSettingsDecrypter;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.DefaultSettingsWriter;
import org.apache.maven.settings.io.SettingsReader;
import org.apache.maven.settings.io.SettingsWriter;
import org.apache.maven.settings.validation.DefaultSettingsValidator;
import org.apache.maven.settings.validation.SettingsValidator;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.impl.Deployer;
import org.eclipse.aether.impl.Installer;
import org.eclipse.aether.impl.LocalRepositoryProvider;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.impl.RemoteRepositoryFilterManager;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.impl.UpdatePolicyAnalyzer;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.internal.impl.DefaultArtifactResolver;
import org.eclipse.aether.internal.impl.DefaultChecksumPolicyProvider;
import org.eclipse.aether.internal.impl.DefaultDeployer;
import org.eclipse.aether.internal.impl.DefaultFileProcessor;
import org.eclipse.aether.internal.impl.DefaultInstaller;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.DefaultLocalPathPrefixComposerFactory;
import org.eclipse.aether.internal.impl.DefaultLocalRepositoryProvider;
import org.eclipse.aether.internal.impl.DefaultMetadataResolver;
import org.eclipse.aether.internal.impl.DefaultOfflineController;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;
import org.eclipse.aether.internal.impl.DefaultRepositoryConnectorProvider;
import org.eclipse.aether.internal.impl.DefaultRepositoryEventDispatcher;
import org.eclipse.aether.internal.impl.DefaultRepositoryLayoutProvider;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.eclipse.aether.internal.impl.DefaultRepositorySystemLifecycle;
import org.eclipse.aether.internal.impl.DefaultTrackingFileManager;
import org.eclipse.aether.internal.impl.DefaultTransporterProvider;
import org.eclipse.aether.internal.impl.DefaultUpdateCheckManager;
import org.eclipse.aether.internal.impl.DefaultUpdatePolicyAnalyzer;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.internal.impl.LocalPathComposer;
import org.eclipse.aether.internal.impl.LocalPathPrefixComposerFactory;
import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory;
import org.eclipse.aether.internal.impl.TrackingFileManager;
import org.eclipse.aether.internal.impl.checksum.DefaultChecksumAlgorithmFactorySelector;
import org.eclipse.aether.internal.impl.checksum.Md5ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.Sha1ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.Sha256ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.checksum.Sha512ChecksumAlgorithmFactory;
import org.eclipse.aether.internal.impl.collect.DefaultDependencyCollector;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegate;
import org.eclipse.aether.internal.impl.collect.bf.BfDependencyCollector;
import org.eclipse.aether.internal.impl.collect.df.DfDependencyCollector;
import org.eclipse.aether.internal.impl.filter.DefaultRemoteRepositoryFilterManager;
import org.eclipse.aether.internal.impl.synccontext.DefaultSyncContextFactory;
import org.eclipse.aether.internal.impl.synccontext.named.NameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NameMappers;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapterFactory;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapterFactoryImpl;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalSemaphoreNamedLockFactory;
import org.eclipse.aether.named.providers.NoopNamedLockFactory;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.ProxySelector;
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
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumAlgorithmFactorySelector;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.ChecksumExtractor;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.transport.http.Nexus2ChecksumExtractor;
import org.eclipse.aether.transport.http.XChecksumChecksumExtractor;
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.eclipse.aether.util.graph.manager.ClassicDependencyManager;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaDependencyContextRefiner;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.NearestVersionSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;
import org.eclipse.aether.util.graph.traverser.FatArtifactTraverser;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.ConservativeAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.eclipse.aether.util.repository.JreProxySelector;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

/**
 * The only class in Camel that deals with all these DI mechanisms of maven-resolver library.
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

    private boolean mavenCentralEnabled = true;
    private boolean mavenApacheSnapshotEnabled = true;

    private RepositoryResolver repositoryResolver;

    private RepositorySystem repositorySystem;
    private RepositorySystemSession repositorySystemSession;

    // repositories to be used with maven-resolver, read from settings and configuration parameters.
    // These are processed according to mirror/proxy configuration
    private final List<RemoteRepository> remoteRepositories = new ArrayList<>();
    // pre-configured Maven Central repository
    private RemoteRepository centralRepository;
    // pre-configured Maven Central repository with mirror/proxy configuration
    private RemoteRepository centralResolutionRepository;
    // pre-configured Apache Snapshots repository in case it's needed on demand
    private RemoteRepository apacheSnapshotsRepository;
    // pre-configured Apache Snapshots repository with mirror/proxy configuration
    private RemoteRepository apacheSnapshotsResolutionRepository;
    private RepositoryPolicy defaultPolicy;

    // the necessary, thin DI container to configure Maven Resolver
    private DIRegistry registry;

    // settings.xml and settings-security.xml locations to be passed to MavenDownloader from camel-tooling-maven
    private String mavenSettings;
    private String mavenSettingsSecurity;
    // comma-separated list of additional repositories to use
    private String repos;
    private boolean fresh;
    private boolean offline;
    private RemoteArtifactDownloadListener remoteArtifactDownloadListener;
    private boolean apacheSnapshotsIncluded;
    private AtomicInteger customRepositoryCounter = new AtomicInteger(1);
    private Settings settings;

    public MavenDownloaderImpl() {
    }

    public MavenDownloaderImpl(RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession,
                               Settings settings) {
        this.repositorySystem = repositorySystem;
        this.repositorySystemSession = repositorySystemSession;
        this.settings = settings;
    }

    @Override
    protected void doBuild() {
        // prepare all services that don't change when resolving Maven artifacts

        repositoryResolver = new DefaultRepositoryResolver();
        repositoryResolver.build();

        // Aether/maven-resolver configuration used without Shrinkwrap
        // and without deprecated:
        //  - org.eclipse.aether.impl.DefaultServiceLocator
        //  - org.apache.maven.repository.internal.MavenRepositorySystemUtils.newServiceLocator()

        registry = new DIRegistry();
        final Properties systemProperties = new Properties();
        // MNG-5670 guard against ConcurrentModificationException
        // MNG-6053 guard against key without value
        synchronized (System.getProperties()) {
            systemProperties.putAll(System.getProperties());
        }

        // locations of settings.xml and settings-security.xml
        validateMavenSettingsLocations();
        if (repositorySystem == null) {
            repositorySystem = configureRepositorySystem(registry, systemProperties, mavenSettingsSecurity, offline);
        }

        // read the settings if not provided
        Settings settings = this.settings == null
                ? mavenConfiguration(registry, repositorySystem, systemProperties, mavenSettings) : this.settings;
        if (offline) {
            LOG.info("MavenDownloader in offline mode");
            settings.setOffline(true);
        }

        if (repositorySystemSession == null) {
            // prepare the Maven session (local repository was configured within the settings)
            // this object is thread safe - it uses configurable download pool
            repositorySystemSession = configureRepositorySystemSession(registry, systemProperties,
                    settings, new File(settings.getLocalRepository()));
        }
        defaultPolicy = fresh ? POLICY_FRESH : POLICY_DEFAULT;

        // process repositories - both from settings.xml and from --repos option. All are subject to
        // mirroring and proxying (handled by org.eclipse.aether.RepositorySystem#newResolutionRepositories())
        List<RemoteRepository> originalRepositories = configureDefaultRepositories(settings);

        remoteRepositories.addAll(repositorySystem.newResolutionRepositories(repositorySystemSession,
                originalRepositories));

        // mirroring/proxying Maven Central
        if (centralRepository == null && !remoteRepositories.isEmpty()) {
            for (RemoteRepository repo : remoteRepositories) {
                if ("central".equals(repo.getId())) {
                    centralRepository = repo;
                    break;
                } else if (repo.getHost().startsWith("repo1.maven.org") || repo.getHost().startsWith("repo2.maven.org")) {
                    centralRepository = repo;
                    break;
                }
            }
        }
        centralResolutionRepository = centralRepository;

        if (mavenApacheSnapshotEnabled && !apacheSnapshotsIncluded) {
            // process apache snapshots even if it's not present in remoteRepositories, because it
            // may be used on demand for each download/resolution request
            apacheSnapshotsResolutionRepository = repositorySystem.newResolutionRepositories(repositorySystemSession,
                    Collections.singletonList(apacheSnapshotsRepository)).get(0);
        }
    }

    @Override
    protected void doInit() {
    }

    @Override
    protected void doStop() throws Exception {
        if (registry != null) {
            registry.close();
        }
    }

    @Override
    public void setRemoteArtifactDownloadListener(RemoteArtifactDownloadListener remoteArtifactDownloadListener) {
        this.remoteArtifactDownloadListener = remoteArtifactDownloadListener;
    }

    @Override
    public List<MavenArtifact> resolveArtifacts(
            List<String> dependencyGAVs,
            Set<String> extraRepositories, boolean transitively, boolean useApacheSnapshots)
            throws MavenResolutionException {
        return resolveArtifacts(null, dependencyGAVs, extraRepositories, transitively, useApacheSnapshots);
    }

    @Override
    public List<MavenArtifact> resolveArtifacts(
            String rootGav,
            List<String> dependencyGAVs, Set<String> extraRepositories,
            boolean transitively, boolean useApacheSnapshots)
            throws MavenResolutionException {
        ArtifactTypeRegistry artifactTypeRegistry = repositorySystemSession.getArtifactTypeRegistry();

        final List<ArtifactRequest> requests = new ArrayList<>(dependencyGAVs.size());
        CollectRequest collectRequest = new CollectRequest();
        List<RemoteRepository> repositories = new ArrayList<>(remoteRepositories);
        if (extraRepositories != null) {
            // simply configure them in addition to the default repositories
            List<RemoteRepository> extraRemoteRepositories = new ArrayList<>();
            // read them
            configureRepositories(extraRemoteRepositories, extraRepositories);
            // proxy/mirror them
            repositories.addAll(repositorySystem.newResolutionRepositories(repositorySystemSession,
                    extraRemoteRepositories));
        }
        if (mavenApacheSnapshotEnabled && useApacheSnapshots && !apacheSnapshotsIncluded) {
            repositories.add(apacheSnapshotsResolutionRepository);
        }

        collectRequest.setRepositories(repositories);

        for (String depId : dependencyGAVs) {
            ArtifactRequest ar = new ArtifactRequest();
            ar.setRepositories(repositories);
            MavenGav gav = MavenGav.parseGav(depId);
            Artifact artifact = new DefaultArtifact(
                    gav.getGroupId(), gav.getArtifactId(), gav.getClassifier(),
                    gav.getPackaging(), gav.getVersion(), artifactTypeRegistry.get(gav.getPackaging()));
            ar.setArtifact(artifact);
            requests.add(ar);

            Dependency dependency = new Dependency(ar.getArtifact(), "compile", false);
            if (Objects.nonNull(rootGav) && !rootGav.isEmpty()) {
                MavenGav rootMavenGav = MavenGav.parseGav(depId);
                Artifact rootArtifact = new DefaultArtifact(
                        rootMavenGav.getGroupId(), rootMavenGav.getArtifactId(),
                        rootMavenGav.getClassifier(), rootMavenGav.getPackaging(),
                        rootMavenGav.getVersion(), artifactTypeRegistry.get(rootMavenGav.getPackaging()));
                collectRequest.setRoot(new Dependency(rootArtifact, "compile", false));
            }
            collectRequest.addDependency(dependency);
            //collectRequest.addManagedDependency(...);
        }

        if (remoteArtifactDownloadListener != null && repositorySystemSession instanceof DefaultRepositorySystemSession) {
            DefaultRepositorySystemSession drss = (DefaultRepositorySystemSession) repositorySystemSession;
            drss.setRepositoryListener(new AbstractRepositoryListener() {
                private final StopWatch watch = new StopWatch();

                @Override
                public void artifactDownloading(RepositoryEvent event) {
                    watch.restart();

                    if (event.getArtifact() != null) {
                        Artifact a = event.getArtifact();

                        ArtifactRepository ar = event.getRepository();
                        String url = ar instanceof RemoteRepository ? ((RemoteRepository) ar).getUrl() : null;
                        String id = ar != null ? ar.getId() : null;
                        String version = a.isSnapshot() ? a.getBaseVersion() : a.getVersion();
                        remoteArtifactDownloadListener.artifactDownloading(a.getGroupId(), a.getArtifactId(), version,
                                id, url);
                    }
                }

                @Override
                public void artifactDownloaded(RepositoryEvent event) {
                    if (event.getArtifact() != null) {
                        Artifact a = event.getArtifact();

                        ArtifactRepository ar = event.getRepository();
                        String url = ar instanceof RemoteRepository ? ((RemoteRepository) ar).getUrl() : null;
                        String id = ar != null ? ar.getId() : null;
                        long elapsed = watch.takenAndRestart();
                        String version = a.isSnapshot() ? a.getBaseVersion() : a.getVersion();
                        remoteArtifactDownloadListener.artifactDownloaded(a.getGroupId(), a.getArtifactId(), version,
                                id, url, elapsed);
                    }
                }
            });
        }

        if (transitively) {
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, new AcceptAllDependencyFilter());
            try {
                DependencyResult dependencyResult
                        = repositorySystem.resolveDependencies(repositorySystemSession, dependencyRequest);

                return dependencyResult.getArtifactResults().stream()
                        .map(dr -> {
                            Artifact a = dr.getArtifact();
                            MavenGav gav = MavenGav.fromCoordinates(a.getGroupId(), a.getArtifactId(),
                                    a.getVersion(), a.getExtension(), a.getClassifier());
                            return new MavenArtifact(gav, a.getFile());
                        })
                        .collect(Collectors.toList());
            } catch (DependencyResolutionException e) {
                MavenResolutionException mre = new MavenResolutionException(e.getMessage(), e);
                repositories.forEach(r -> mre.getRepositories().add(r.getUrl()));
                throw mre;
            }
        } else {
            try {
                List<ArtifactResult> artifactResults
                        = repositorySystem.resolveArtifacts(repositorySystemSession, requests);

                return artifactResults.stream()
                        .map(dr -> {
                            Artifact a = dr.getArtifact();
                            MavenGav gav = MavenGav.fromCoordinates(a.getGroupId(), a.getArtifactId(),
                                    a.getVersion(), a.getExtension(), a.getClassifier());
                            return new MavenArtifact(gav, a.getFile());
                        })
                        .collect(Collectors.toList());
            } catch (ArtifactResolutionException e) {
                MavenResolutionException mre = new MavenResolutionException(e.getMessage(), e);
                repositories.forEach(r -> mre.getRepositories().add(r.getUrl()));
                throw mre;
            }
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
                List<RemoteRepository> customResolutionRepository
                        = repositorySystem.newResolutionRepositories(repositorySystemSession,
                                Collections.singletonList(custom));

                req.setRepository(customResolutionRepository.get(0));
            }

            req.setFavorLocalRepository(false);
            req.setMetadata(new DefaultMetadata(groupId, artifactId, "maven-metadata.xml", Metadata.Nature.RELEASE));

            List<MetadataResult> result = repositorySystem.resolveMetadata(repositorySystemSession, List.of(req));
            for (MetadataResult mr : result) {
                if (mr.isResolved() && mr.getMetadata().getFile() != null) {
                    File f = mr.getMetadata().getFile();
                    if (f.exists() && f.isFile()) {
                        MetadataXpp3Reader reader = new MetadataXpp3Reader();
                        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(f))) {
                            org.apache.maven.artifact.repository.metadata.Metadata md = reader.read(is);
                            List<String> versions = md.getVersioning().getVersions();
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
            return gavs;
        } catch (Exception e) {
            String msg = "Cannot resolve available versions in " + req.getRepository().getUrl();
            MavenResolutionException mre = new MavenResolutionException(msg, e);
            mre.getRepositories().add(req.getRepository().getUrl());
            throw mre;
        }
    }

    @Override
    public MavenDownloader customize(String localRepository, int connectTimeout, int requestTimeout) {
        MavenDownloaderImpl copy = new MavenDownloaderImpl();
        copy.repositorySystem = repositorySystem;
        copy.remoteRepositories.addAll(remoteRepositories);
        copy.apacheSnapshotsRepository = apacheSnapshotsRepository;
        copy.apacheSnapshotsResolutionRepository = apacheSnapshotsResolutionRepository;
        copy.defaultPolicy = defaultPolicy;
        copy.registry = registry;
        copy.mavenSettings = mavenSettings;
        copy.mavenSettingsSecurity = mavenSettingsSecurity;
        copy.repos = repos;
        copy.fresh = fresh;
        copy.apacheSnapshotsIncluded = apacheSnapshotsIncluded;
        copy.customRepositoryCounter = customRepositoryCounter;
        copy.repositoryResolver = repositoryResolver;
        copy.offline = offline;

        LocalRepositoryManagerFactory lrmFactory = registry.lookupByClass(LocalRepositoryManagerFactory.class);

        // session can't be shared
        DefaultRepositorySystemSession rssCopy = new DefaultRepositorySystemSession(repositorySystemSession);
        rssCopy.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, connectTimeout);
        rssCopy.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, requestTimeout);
        try {
            rssCopy.setLocalRepositoryManager(lrmFactory.newInstance(rssCopy,
                    new LocalRepository(localRepository)));
        } catch (NoLocalRepositoryManagerException e) {
            LOG.warn(e.getMessage(), e);
        }
        copy.repositorySystemSession = rssCopy;

        return copy;
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
            String m2settings = System.getProperty("user.home") + File.separator + ".m2"
                                + File.separator + "settings.xml";
            if (new File(m2settings).isFile()) {
                mavenSettings = m2settings;
            }
        } else {
            if (!new File(mavenSettings).isFile()) {
                LOG.warn("Can't access {}. Skipping Maven settings.xml configuration.", mavenSettings);
            }
            mavenSettings = null;
        }

        if (!skip) {
            if (mavenSettingsSecurity == null) {
                // implicit security settings
                String m2settingsSecurity = System.getProperty("user.home") + File.separator + ".m2"
                                            + File.separator + "settings-security.xml";
                if (new File(m2settingsSecurity).isFile()) {
                    mavenSettingsSecurity = m2settingsSecurity;
                }
            } else {
                if (!new File(mavenSettingsSecurity).isFile()) {
                    LOG.warn("Can't access {}. Skipping Maven settings-settings.xml configuration.",
                            mavenSettingsSecurity);
                }
                mavenSettingsSecurity = null;
            }
        }
    }

    /**
     * Configure entire {@link RepositorySystem} service
     */
    RepositorySystem configureRepositorySystem(
            DIRegistry registry,
            Properties systemProperties, String settingsSecurityLocation, boolean offline) {
        basicRepositorySystemConfiguration(registry);
        transportConfiguration(registry, systemProperties);
        settingsConfiguration(registry, settingsSecurityLocation);

        return registry.lookupByClass(RepositorySystem.class);
    }

    /**
     * Configure the basic, necessary requirements of {@link RepositorySystem} in {@link DIRegistry}
     */
    private static void basicRepositorySystemConfiguration(DIRegistry registry) {
        // this is the first one registered in DefaultServiceLocator - what follows up is BFS dependencies
        registry.bind(RepositorySystem.class, DefaultRepositorySystem.class);

        // level 1 requirements of org.eclipse.aether.internal.impl.DefaultRepositorySystem
        registry.bind(VersionResolver.class, DefaultVersionResolver.class);
        registry.bind(VersionRangeResolver.class, DefaultVersionRangeResolver.class);
        registry.bind(ArtifactResolver.class, DefaultArtifactResolver.class);
        registry.bind(MetadataResolver.class, DefaultMetadataResolver.class);
        registry.bind(ArtifactDescriptorReader.class, DefaultArtifactDescriptorReader.class);
        registry.bind(DependencyCollector.class, DefaultDependencyCollector.class);
        registry.bind(Installer.class, DefaultInstaller.class);
        registry.bind(Deployer.class, DefaultDeployer.class);
        registry.bind(LocalRepositoryProvider.class, DefaultLocalRepositoryProvider.class);
        registry.bind(SyncContextFactory.class, DefaultSyncContextFactory.class);
        registry.bind(RemoteRepositoryManager.class, DefaultRemoteRepositoryManager.class);

        // level 2 requirements of org.eclipse.aether.internal.impl.DefaultRepositorySystem

        // remaining requirements of org.apache.maven.repository.internal.DefaultVersionResolver
        registry.bind(RepositoryEventDispatcher.class, DefaultRepositoryEventDispatcher.class);

        // remaining requirements of org.eclipse.aether.internal.impl.DefaultArtifactResolver
        registry.bind(FileProcessor.class, DefaultFileProcessor.class);
        registry.bind(UpdateCheckManager.class, DefaultUpdateCheckManager.class);
        registry.bind(RepositoryConnectorProvider.class, DefaultRepositoryConnectorProvider.class);
        registry.bind(OfflineController.class, DefaultOfflineController.class);

        // remaining requirements of org.apache.maven.repository.internal.DefaultArtifactDescriptorReader

        // model builder has a lot of @Inject fields, so let's switch to what ServiceLocator version
        // of DefaultArtifactDescriptorReader is doing in DefaultArtifactDescriptorReader.initService()
        // also, org.apache.maven.model.building.DefaultModelBuilder uses @Inject on fields, which are not
        // handled yet
        //        registry.bind(ModelBuilder.class, DefaultModelBuilder.class);
        registry.bind("modelBuilder", ModelBuilder.class, new DefaultModelBuilderFactory().newInstance());

        // remaining requirements of org.eclipse.aether.internal.impl.collect.DefaultDependencyCollector
        registry.bind(DependencyCollectorDelegate.class, DfDependencyCollector.class); // aether.collector.impl=df
        registry.bind(DependencyCollectorDelegate.class, BfDependencyCollector.class); // aether.collector.impl=bf

        // remaining requirements of org.eclipse.aether.internal.impl.DefaultInstaller
        registry.bind(MetadataGeneratorFactory.class, SnapshotMetadataGeneratorFactory.class);
        registry.bind(MetadataGeneratorFactory.class, VersionsMetadataGeneratorFactory.class);
        // Maven 3.9.x
        registry.bind(MetadataGeneratorFactory.class, PluginsMetadataGeneratorFactory.class);

        // remaining requirements of org.eclipse.aether.internal.impl.DefaultLocalRepositoryProvider
        registry.bind(LocalRepositoryManagerFactory.class, EnhancedLocalRepositoryManagerFactory.class);
        //registry.bind(LocalRepositoryManagerFactory.class, SimpleLocalRepositoryManagerFactory.class);

        // remaining requirements of org.eclipse.aether.internal.impl.synccontext.DefaultSyncContextFactory
        registry.bind(NamedLockFactoryAdapterFactory.class, NamedLockFactoryAdapterFactoryImpl.class);

        // remaining requirements of org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager
        registry.bind(UpdatePolicyAnalyzer.class, DefaultUpdatePolicyAnalyzer.class);
        registry.bind(ChecksumPolicyProvider.class, DefaultChecksumPolicyProvider.class);

        // remaining levels of requirements of org.eclipse.aether.internal.impl.DefaultRepositorySystem

        // requirements of org.eclipse.aether.internal.impl.DefaultUpdateCheckManager
        registry.bind(TrackingFileManager.class, DefaultTrackingFileManager.class);

        // requirements of org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapterFactoryImpl
        registry.bind(NamedLockFactory.class, FileLockNamedLockFactory.class);
        registry.bind(NamedLockFactory.class, LocalReadWriteLockNamedLockFactory.class);
        registry.bind(NamedLockFactory.class, NoopNamedLockFactory.class);
        registry.bind(NamedLockFactory.class, LocalSemaphoreNamedLockFactory.class);
        registry.bind(NameMappers.GAECV_NAME, NameMapper.class, NameMappers.gaecvNameMapper());
        registry.bind(NameMappers.GAV_NAME, NameMapper.class, NameMappers.gavNameMapper());
        registry.bind(NameMappers.STATIC_NAME, NameMapper.class, NameMappers.staticNameMapper());
        registry.bind(NameMappers.DISCRIMINATING_NAME, NameMapper.class, NameMappers.discriminatingNameMapper());
        registry.bind(NameMappers.FILE_GAV_NAME, NameMapper.class, NameMappers.fileGavNameMapper());
        registry.bind(NameMappers.FILE_HGAV_NAME, NameMapper.class, NameMappers.fileHashingGavNameMapper());

        // requirements of org.apache.maven.repository.internal.DefaultVersionResolver (these are deprecated)
        // no longer needed for Maven 3.9+ (see: MNG-7247)
        //registry.bind(org.eclipse.aether.impl.SyncContextFactory.class,
        //        org.eclipse.aether.internal.impl.synccontext.legacy.DefaultSyncContextFactory.class);

        // requirements of org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory
        registry.bind(LocalPathComposer.class, DefaultLocalPathComposer.class);
        registry.bind(LocalPathPrefixComposerFactory.class, DefaultLocalPathPrefixComposerFactory.class);

        // additional services
        //registry.bind(org.eclipse.aether.spi.log.LoggerFactory.class, Slf4jLoggerFactory.class);

        // resolver 1.9.x
        registry.bind(RemoteRepositoryFilterManager.class, DefaultRemoteRepositoryFilterManager.class);
        registry.bind(RepositorySystemLifecycle.class, DefaultRepositorySystemLifecycle.class);

        // resolver 1.9.x + maven 3.9.x
        registry.bind(ModelCacheFactory.class, DefaultModelCacheFactory.class);

        // not used / optional
        //  - org.eclipse.aether.internal.impl.DefaultArtifactResolver.setArtifactResolverPostProcessors()
        //  - org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory.setProvidedChecksumSources()
        //  - org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource.class
        //     - org.eclipse.aether.internal.impl.filter.GroupIdRemoteRepositoryFilterSource.class
        //     - org.eclipse.aether.internal.impl.filter.PrefixesRemoteRepositoryFilterSource.class
        //  - org.eclipse.aether.spi.checksums.TrustedChecksumsSource.class
    }

    /**
     * Configure the transport related requirements of {@link RepositorySystem} in {@link DIRegistry}
     */
    private static void transportConfiguration(DIRegistry registry, Properties systemProperties) {
        // in order to resolve the artifacts we need some connector factories
        registry.bind(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        // repository connectory factory needs transporter provider(s)
        registry.bind(TransporterProvider.class, DefaultTransporterProvider.class);

        // and transport provider needs transport factories. there are several implementations, but one of them
        // is another indirect factory - the WagonTransporterFactory. However it was marked as _ancient_ with
        // Maven 3.9 / Maven Resolver 1.9, so we'll use the _native_ ones. Even if the wagon allows us to share
        // the http client (wagon) easier.
        //        registry.bind(TransporterFactory.class, WagonTransporterFactory.class);
        registry.bind(TransporterFactory.class, FileTransporterFactory.class);
        registry.bind(TransporterFactory.class, HttpTransporterFactory.class);

        // requirements of org.eclipse.aether.transport.http.HttpTransporterFactory
        // nexus2 - ETag: "{SHA1{d40d68ba1f88d8e9b0040f175a6ff41928abd5e7}}"
        registry.bind(ChecksumExtractor.class, Nexus2ChecksumExtractor.class);
        // x-checksum - x-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
        registry.bind(ChecksumExtractor.class, XChecksumChecksumExtractor.class);

        // requirements of org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
        registry.bind(RepositoryLayoutProvider.class, DefaultRepositoryLayoutProvider.class);
        // repository layout provider needs layout factory
        registry.bind(RepositoryLayoutFactory.class, Maven2RepositoryLayoutFactory.class);

        // requirements of org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory
        registry.bind(ChecksumAlgorithmFactorySelector.class, DefaultChecksumAlgorithmFactorySelector.class);
        // checksum algorithm factory selector needs at least MD5 and SHA1 algorithm factories
        registry.bind(ChecksumAlgorithmFactory.class, Md5ChecksumAlgorithmFactory.class);
        registry.bind(ChecksumAlgorithmFactory.class, Sha1ChecksumAlgorithmFactory.class);
        registry.bind(ChecksumAlgorithmFactory.class, Sha256ChecksumAlgorithmFactory.class);
        registry.bind(ChecksumAlgorithmFactory.class, Sha512ChecksumAlgorithmFactory.class);
    }

    /**
     * Configure the Maven services in {@link DIRegistry} needed to process {@link Settings Maven settings}
     */
    private static void settingsConfiguration(DIRegistry registry, String localSettingsSecurity) {
        // before getting/creating an org.eclipse.aether.RepositorySystemSession, we need settings as a source
        // of several configuration options/settings
        // and because settings may contain encrypted entries, we need security settings configuration too

        // org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher has @Inject parameter
        // for configuration file, so we're creating it manually
        //        registry.bind(SecDispatcher.class, DefaultSecDispatcher.class);
        // mind that
        // org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION
        // ("settings.security") is the master password too... (secret of Polichinelle)
        DefaultSecDispatcher securityDispatcher = new DefaultSecDispatcher(new DefaultPlexusCipher());
        securityDispatcher.setConfigurationFile(localSettingsSecurity);
        registry.bind("securityDispatcher", SecDispatcher.class, securityDispatcher);
        registry.bind(SettingsDecrypter.class, DefaultSettingsDecrypter.class);

        // we could use org.apache.maven.settings.building.DefaultSettingsBuilder directly, but it's better
        // to be consistent and use DI for that - especially because after
        // https://issues.apache.org/jira/browse/MNG-6680, DefaultSettingsBuilder is no longer annotated
        // with @org.codehaus.plexus.component.annotations.Component, but with @javax.inject.Named
        registry.bind(SettingsReader.class, DefaultSettingsReader.class);
        registry.bind(SettingsWriter.class, DefaultSettingsWriter.class);
        registry.bind(SettingsValidator.class, DefaultSettingsValidator.class);
        registry.bind(SettingsBuilder.class, DefaultSettingsBuilder.class);
    }

    /**
     * Using the configured {@link DIRegistry}, load {@link Settings Maven settings}
     */
    Settings mavenConfiguration(
            DIRegistry registry, RepositorySystem repositorySystem,
            Properties systemProperties, String mavenSettings) {
        // settings are important to configure the session later
        SettingsBuilder settingsBuilder = registry.lookupByClass(SettingsBuilder.class);
        SettingsBuildingRequest sbRequest = new DefaultSettingsBuildingRequest();
        sbRequest.setSystemProperties(systemProperties);
        if (mavenSettings != null) {
            sbRequest.setUserSettingsFile(new File(mavenSettings));
        }
        Settings settings;
        try {
            SettingsBuildingResult sbResult = settingsBuilder.build(sbRequest);
            settings = sbResult.getEffectiveSettings();
        } catch (SettingsBuildingException e) {
            LOG.warn("Problem reading settings file {}: {}. Falling back to defaults.",
                    mavenSettings, e.getMessage(), e);
            settings = new Settings();
        }

        // local repository in this order:
        // 1) -Dmaven.repo.local
        // 2) settings.xml
        // 3) ${user.home}/.m2/repository (if exists)
        // 4) /tmp/.m2/repository
        String localRepository = System.getProperty("maven.repo.local");
        if (localRepository == null || localRepository.isBlank()) {
            localRepository = settings.getLocalRepository();
        }
        if (localRepository == null || localRepository.isBlank()) {
            Path m2Repository = Paths.get(System.getProperty("user.home"), ".m2/repository");
            if (!m2Repository.toFile().isDirectory()) {
                m2Repository = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
                m2Repository.toFile().mkdirs();
            }
            localRepository = m2Repository.toString();
        }
        File m2Repository = new File(localRepository);
        settings.setLocalRepository(m2Repository.getAbsolutePath());

        // some parts of the settings may be encrypted:
        //  - settings.getServer("xxx").getPassphrase()
        //  - settings.getServer("xxx").getPassword()
        //  - settings.getProxies().get(N).getPassword()
        // so we have to use previously configured org.apache.maven.settings.crypto.SettingsDecrypter
        SettingsDecrypter decrypter = registry.lookupByClass(SettingsDecrypter.class);
        SettingsDecryptionRequest sdRequest = new DefaultSettingsDecryptionRequest(settings);
        SettingsDecryptionResult sdResult = decrypter.decrypt(sdRequest);
        settings.setProxies(sdResult.getProxies());
        settings.setServers(sdResult.getServers());

        // profile activation isn't implicit
        for (Map.Entry<String, Profile> entry : settings.getProfilesAsMap().entrySet()) {
            String name = entry.getKey();
            Profile profile = entry.getValue();
            if (profile.getActivation() != null && profile.getActivation().isActiveByDefault()) {
                settings.getActiveProfiles().add(name);
            }
            // TODO: handle other activation methods (file, JDK, property, OS)
        }

        return settings;
    }

    /**
     * Using the configured {@link DIRegistry}, obtain thread-safe {@link RepositorySystemSession} used to resolve and
     * download Maven dependencies.
     */
    RepositorySystemSession configureRepositorySystemSession(
            DIRegistry registry,
            Properties systemProperties, Settings settings, File localRepository) {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();

        // proxies are copied from the settings to proxy selector
        ProxySelector proxySelector;
        if (settings.getProxies().isEmpty()) {
            proxySelector = new JreProxySelector();
        } else {
            proxySelector = new DefaultProxySelector();
            for (Proxy proxy : settings.getProxies()) {
                if (proxy.isActive()) {
                    String nonProxyHosts = proxy.getNonProxyHosts();
                    org.eclipse.aether.repository.Proxy proxyConfig;
                    AuthenticationBuilder builder = new AuthenticationBuilder();
                    if (proxy.getUsername() != null) {
                        builder.addUsername(proxy.getUsername());
                        builder.addPassword(proxy.getPassword());
                    }
                    proxyConfig = new org.eclipse.aether.repository.Proxy(
                            proxy.getProtocol(), proxy.getHost(),
                            proxy.getPort(), builder.build());
                    ((DefaultProxySelector) proxySelector).add(proxyConfig, nonProxyHosts);
                }
            }
        }

        // process servers:
        //  - we'll extend MirrorSelector to provide mirror authentication
        //  - we want to set session configuration options for http headers and permissions
        // see maven-core:
        //    org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory.newRepositorySession()
        Map<String, Object> serverConfigurations = new HashMap<>();
        DefaultAuthenticationSelector baseAuthenticationSelector = new DefaultAuthenticationSelector();
        AuthenticationSelector authenticationSelector
                = new ConservativeAuthenticationSelector(baseAuthenticationSelector);

        int connectTimeout = ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT;
        int requestTimeout = ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT;

        for (Server server : settings.getServers()) {
            // no need to bother with null values
            Authentication auth = new AuthenticationBuilder()
                    .addPrivateKey(server.getPrivateKey(), server.getPassphrase())
                    .addUsername(server.getUsername())
                    .addPassword(server.getPassword())
                    .build();
            baseAuthenticationSelector.add(server.getId(), auth);

            // see private constants in org.eclipse.aether.transport.wagon.WagonTransporter
            if (server.getFilePermissions() != null) {
                serverConfigurations.put("aether.connector.perms.fileMode." + server.getId(),
                        server.getFilePermissions());
            }
            if (server.getDirectoryPermissions() != null) {
                serverConfigurations.put("aether.connector.perms.dirMode." + server.getId(),
                        server.getFilePermissions());
            }

            if (server.getConfiguration() instanceof Xpp3Dom) {
                // === pre maven 3.9 / maven-resolver 1.9:
                // this part is a generic configuration used by different Maven components
                //  - entire configuration is read by maven-core itself and passed as
                //    org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration object using
                //    "aether.connector.wagon.config.<repoId>" config property in
                //    org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory.newRepositorySession()
                //  - it's then processed in org.eclipse.aether.transport.wagon.WagonTransporter.connectWagon()
                //    by configured org.eclipse.aether.transport.wagon.WagonConfigurator. In `mvn` run, the
                //    configurator is org.eclipse.aether.internal.transport.wagon.PlexusWagonConfigurator which
                //    uses o.e.a.internal.transport.wagon.PlexusWagonConfigurator.WagonComponentConfigurator
                //  - the object to configure is an instance of org.apache.maven.wagon.Wagon and
                //    WagonComponentConfigurator simply uses reflection for nested properties
                //  - so for typical wagon-http scenario (used by Maven distribution itself), we can configure:
                //     - org.apache.maven.wagon.shared.http.AbstractHttpClientWagon.setBasicAuthScope()
                //     - org.apache.maven.wagon.shared.http.AbstractHttpClientWagon.setHttpConfiguration()
                //     - org.apache.maven.wagon.shared.http.AbstractHttpClientWagon.setHttpHeaders(Properties)
                //     - org.apache.maven.wagon.shared.http.AbstractHttpClientWagon.setInitialBackoffSeconds()
                //     - org.apache.maven.wagon.shared.http.AbstractHttpClientWagon.setProxyBasicAuthScope()
                //     - org.apache.maven.wagon.AbstractWagon.setInteractive()
                //     - org.apache.maven.wagon.AbstractWagon.setReadTimeout()
                //     - org.apache.maven.wagon.AbstractWagon.setTimeout()
                // see https://maven.apache.org/guides/mini/guide-http-settings.html
                //
                // the ultimate option is to configure org.apache.maven.wagon.shared.http.HttpConfiguration
                // object which is reflectively passed to a wagon
                //
                // however guide-http-settings.html still mentions <configuration>/<httpHeaders> and it's a bit
                // confusing...
                //  - org.eclipse.aether.transport.wagon.WagonTransporter.WagonTransporter() constructor
                //    gets a "aether.connector.http.headers.<repoId>" or "aether.connector.http.headers" config
                //    property, but I don't see anything that sets it in maven/maven-resolver/maven-wagon
                //  - this property is also checked by
                //    org.eclipse.aether.transport.http.HttpTransporter.HttpTransporter()
                //  - later, in org.eclipse.aether.transport.wagon.WagonTransporter.connectWagon(), full
                //    reflection-based org.eclipse.aether.transport.wagon.WagonConfigurator.configure() is used
                //    and wagon's "httpHeaders" field is overriden - previously it was set to the value
                //    of org.eclipse.aether.transport.wagon.WagonTransporter.headers which contained a User-Agent
                //    header set from "aether.connector.userAgent" property set by Maven...
                //
                // === maven 3.9 / maven-resolver 1.9:
                // As https://maven.apache.org/guides/mini/guide-resolver-transport.html says, the default transport
                // (the default transport used by Maven Resolver) changed from ancient Wagon to modern
                // maven-resolver-transport-http aka native HTTP transport.
                // org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory.newRepositorySession()
                // (org.apache.maven:maven-core) has changed considerably in Maven 3.9.0. Before 3.9.0,
                // org.apache.maven.settings.Server.getConfiguration() was taken and simply passed (wrapped in
                // org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration) as aether session config property
                // named "aether.connector.wagon.config.<serverId>"
                //
                // With maven 3.9 / maven-resolver 1.9, the same property is used, but the XML configuration is
                // "translated to proper resolver configuration properties as well", so additionally:
                // - <httpHeaders> is translated into Map set as "aether.connector.http.headers.<serverId>" property
                // - <connectTimeout> is translated into Integer set as "aether.connector.connectTimeout.<serverId>"
                // - <requestTimeout> is translated into Integer set as "aether.connector.requestTimeout.<serverId>"
                // - if <httpConfiguration>/<all>/<connectionTimeout> is found, a WARNING is printed:
                //   [WARNING] Settings for server <serverId> uses legacy format
                // - if <httpConfiguration>/<all>/<readTimeout> is found, a WARNING is printed:
                //   [WARNING] Settings for server <serverId> uses legacy format
                // (mind the translation: connectionTimeout->connectTimeout and readTimeout->requestTimeout
                //
                // all the properties are described here: https://maven.apache.org/resolver/configuration.html

                Map<String, String> headers = new LinkedHashMap<>();
                Xpp3Dom serverConfig = (Xpp3Dom) server.getConfiguration();

                // handle:
                //     <server>
                //      <id>my-server</id>
                //      <configuration>
                //        <httpHeaders>
                //          <property>
                //            <name>X-Asked-By</name>
                //            <value>Camel</value>
                //          </property>
                //        </httpHeaders>
                //      </configuration>
                //    </server>
                // see org.codehaus.plexus.component.configurator.converters.composite.PropertiesConverter
                Xpp3Dom httpHeaders = serverConfig.getChild("httpHeaders");
                if (httpHeaders != null) {
                    for (Xpp3Dom httpHeader : httpHeaders.getChildren("property")) {
                        Xpp3Dom name = httpHeader.getChild("name");
                        String headerName = name.getValue();
                        Xpp3Dom value = httpHeader.getChild("value");
                        String headerValue = value.getValue();
                        headers.put(headerName, headerValue);
                    }
                }
                serverConfigurations.put(ConfigurationProperties.HTTP_HEADERS + "." + server.getId(), headers);

                // DON'T handle (as it's pre-maven 3.9):
                //     <server>
                //      <id>my-server</id>
                //      <configuration>
                //        <httpConfiguration>
                //          <all>
                //            <connectionTimeout>5000</connectionTimeout>
                //            <readTimeout>10000</readTimeout>
                //          </all>
                //        </httpConfiguration>
                //      </configuration>
                //    </server>
                // see org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter
                // handle (maven 3.9+):
                //     <server>
                //      <id>my-server</id>
                //      <configuration>
                //        <connectTimeout>5000</connectTimeout>
                //        <requestTimeout>5000</requestTimeout>
                //      </configuration>
                //    </server>
                Xpp3Dom connectTimeoutNode = serverConfig.getChild("connectTimeout");
                if (connectTimeoutNode != null) {
                    try {
                        connectTimeout = Integer.parseInt(connectTimeoutNode.getValue());
                    } catch (NumberFormatException ignored) {
                    }
                }
                Xpp3Dom requestTimeoutNode = serverConfig.getChild("requestTimeout");
                if (requestTimeoutNode != null) {
                    try {
                        requestTimeout = Integer.parseInt(requestTimeoutNode.getValue());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        // mirror settings - Pax URL had something like AuthenticatedMirrorSelector which assigned
        // authentication to mirror-representing RemoteRepositories. But it's not required if we
        // properly use org.eclipse.aether.RepositorySystem#newResolutionRepositories()!
        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        for (Mirror mirror : settings.getMirrors()) {
            mirrorSelector.add(mirror.getId(), mirror.getUrl(), mirror.getLayout(), false, false,
                    mirror.getMirrorOf(), mirror.getMirrorOfLayouts());
        }

        // no more actual requirements, but we need more services when using
        // org.eclipse.aether.RepositorySystemSession
        LocalRepositoryManagerFactory lrmFactory = registry.lookupByClass(LocalRepositoryManagerFactory.class);

        try {
            session.setLocalRepositoryManager(lrmFactory.newInstance(session, new LocalRepository(localRepository)));
        } catch (NoLocalRepositoryManagerException e) {
            LOG.warn(e.getMessage(), e);
        }

        // more session configuration which is implicit with
        // org.apache.maven.repository.internal.MavenRepositorySystemUtils.newSession()
        session.setDependencyTraverser(new FatArtifactTraverser());
        session.setDependencyManager(new ClassicDependencyManager());
        // this is exactly what's done inside
        // org.jboss.shrinkwrap.resolver.impl.maven.MavenWorkingSessionImpl.resolveDependencies() - we don't
        // have to do it on each resolution attempt
        DependencySelector depFilter = new AndDependencySelector(
                new ScopeDependencySelector("test", "provided"),
                new OptionalDependencySelector(),
                new ExclusionDependencySelector());
        session.setDependencySelector(depFilter);
        DependencyGraphTransformer transformer = new ConflictResolver(
                new NearestVersionSelector(), new JavaScopeSelector(),
                new SimpleOptionalitySelector(), new JavaScopeDeriver());
        transformer = new ChainedDependencyGraphTransformer(transformer, new JavaDependencyContextRefiner());
        session.setDependencyGraphTransformer(transformer);

        DefaultArtifactTypeRegistry stereotypes = new DefaultArtifactTypeRegistry();
        stereotypes.add(new DefaultArtifactType("pom"));
        stereotypes.add(new DefaultArtifactType("maven-plugin", "jar", "", "java"));
        stereotypes.add(new DefaultArtifactType("jar", "jar", "", "java"));
        stereotypes.add(new DefaultArtifactType("ejb", "jar", "", "java"));
        stereotypes.add(new DefaultArtifactType("ejb-client", "jar", "client", "java"));
        stereotypes.add(new DefaultArtifactType("test-jar", "jar", "tests", "java"));
        stereotypes.add(new DefaultArtifactType("javadoc", "jar", "javadoc", "java"));
        stereotypes.add(new DefaultArtifactType("java-source", "jar", "sources", "java", false, false));
        stereotypes.add(new DefaultArtifactType("war", "war", "", "java", false, true));
        stereotypes.add(new DefaultArtifactType("ear", "ear", "", "java", false, true));
        stereotypes.add(new DefaultArtifactType("rar", "rar", "", "java", false, true));
        stereotypes.add(new DefaultArtifactType("par", "par", "", "java", false, true));
        session.setArtifactTypeRegistry(stereotypes);
        session.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(true, true));

        session.setUserProperties(null);
        session.setSystemProperties(systemProperties);
        // this allows passing -Dxxx=yyy as config properties
        session.setConfigProperties(systemProperties);

        // these properties may be externalized to camel-jbang properties
        session.setConfigProperty("aether.connector.basic.threads", "4");
        session.setConfigProperty("aether.collector.impl", "df"); // or "bf"

        // timeouts. see:
        //  - org.eclipse.aether.transport.http.HttpTransporter.HttpTransporter()
        //  - org.eclipse.aether.transport.wagon.WagonTransporter.connectWagon()
        session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, connectTimeout);
        session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, requestTimeout);

        // server headers configuration - for each <server> from the settings.xml
        serverConfigurations.forEach(session::setConfigProperty);

        // remaining customization of the session
        session.setProxySelector(proxySelector);
        session.setMirrorSelector(mirrorSelector);

        // explicit null global policies, so each repository can define its own
        session.setChecksumPolicy(null);
        session.setUpdatePolicy(null);

        // to associate authentications with remote repositories (also mirrored)
        session.setAuthenticationSelector(authenticationSelector);
        // offline mode selected using for example `camel run --download` option - should be online by default
        session.setOffline(offline);
        // controls whether repositories declared in artifact descriptors should be ignored during transitive
        // dependency collection
        session.setIgnoreArtifactDescriptorRepositories(true);
        // deprecated, no API replacement
        //            session.setFileTransformerManager(null);
        // not used
        //            session.setVersionFilter(null);
        //            session.setRepositoryListener(null);
        //            session.setTransferListener(null);
        //            session.setResolutionErrorPolicy(null);
        //            session.setData(null);
        //            session.setReadOnly();
        // could be useful to search through kamelet/jbang config
        session.setWorkspaceReader(null);

        session.setCache(new DefaultRepositoryCache());

        return session;
    }

    /**
     * <p>
     * Using the passed ({@code --repos} parameter or {@code camel.jbang.repos} option) and configured (in Maven
     * settings) repository locations, prepare a list of {@link RemoteRepository remote repositories} to be used during
     * Maven resolution. These repositories are <b>not yet</b> mirrored/proxied. Use
     * {@link RepositorySystem#newResolutionRepositories} first.
     * </p>
     *
     * <p>
     * This method is used during initialization of this {@link MavenDownloader}, but when invoking actual
     * download/resolve methods, we can use additional repositories.
     * </p>
     */
    List<RemoteRepository> configureDefaultRepositories(Settings settings) {
        List<RemoteRepository> repositories = new ArrayList<>();

        // a set to prevent duplicates, but do not store URLs directly (hashCode() may lead to DNS resolution!)
        Set<String> repositoryURLs = new HashSet<>();

        if (mavenCentralEnabled) {
            // add maven central first - always
            centralRepository = new RemoteRepository.Builder("central", "default", MAVEN_CENTRAL_REPO)
                    .setReleasePolicy(defaultPolicy)
                    .setSnapshotPolicy(POLICY_DISABLED)
                    .build();
            repositories.add(centralRepository);
        }

        if (mavenApacheSnapshotEnabled) {
            // configure Apache snapshots - to be used if needed
            apacheSnapshotsRepository = new RemoteRepository.Builder("apache-snapshot", "default", APACHE_SNAPSHOT_REPO)
                    .setReleasePolicy(POLICY_DISABLED)
                    .setSnapshotPolicy(defaultPolicy)
                    .build();
        }

        // and custom repos and remember URLs to not duplicate the repositories from the settings
        if (repos != null) {
            List<RemoteRepository> repositoriesFromConfiguration = new ArrayList<>();
            Set<String> urls = Arrays.stream(repos.split("\\s*,\\s*")).collect(Collectors.toSet());
            configureRepositories(repositoriesFromConfiguration, urls);
            for (RemoteRepository repo : repositoriesFromConfiguration) {
                if (repositoryURLs.add(repo.getUrl())) {
                    if (repo == apacheSnapshotsRepository) {
                        // record that Apache Snapshots repository is included in default (always used) repositories
                        apacheSnapshotsIncluded = true;
                    }
                    repositories.add(repo);
                }
            }
        }

        // then process the repositories from active profiles from external Maven settings
        for (String profile : settings.getActiveProfiles()) {
            Profile p = settings.getProfilesAsMap().get(profile);
            if (p != null) {
                for (Repository r : p.getRepositories()) {
                    try {
                        URL url = URI.create(r.getUrl()).toURL();
                        if (repositoryURLs.add(r.getUrl())) {
                            if (mavenApacheSnapshotEnabled && url.getHost().equals("repository.apache.org")
                                    && url.getPath().startsWith("/snapshots")) {
                                // record that Apache Snapshots repository is included in default (always used)
                                // repositories and used preconfigured instance of o.e.aether.repository.RemoteRepository
                                apacheSnapshotsIncluded = true;
                                repositories.add(apacheSnapshotsRepository);
                            } else {
                                RemoteRepository.Builder rb
                                        = new RemoteRepository.Builder(r.getId(), r.getLayout(), r.getUrl());
                                if (r.getReleases() == null) {
                                    // default (enabled) policy for releases
                                    rb.setPolicy(defaultPolicy);
                                } else {
                                    String updatePolicy = r.getReleases().getUpdatePolicy() == null
                                            ? RepositoryPolicy.UPDATE_POLICY_DAILY : r.getReleases().getUpdatePolicy();
                                    String checksumPolicy = r.getReleases().getChecksumPolicy() == null
                                            ? RepositoryPolicy.CHECKSUM_POLICY_WARN : r.getReleases().getChecksumPolicy();
                                    rb.setPolicy(new RepositoryPolicy(
                                            r.getReleases().isEnabled(),
                                            updatePolicy, checksumPolicy));
                                }
                                // if someone defines Apache snapshots repository, (s)he has to specify proper policy, sorry.
                                if (r.getSnapshots() == null) {
                                    // default (disabled) policy for releases
                                    rb.setSnapshotPolicy(POLICY_DISABLED);
                                } else {
                                    String updatePolicy = r.getSnapshots().getUpdatePolicy() == null
                                            ? RepositoryPolicy.UPDATE_POLICY_DAILY : r.getSnapshots().getUpdatePolicy();
                                    String checksumPolicy = r.getSnapshots().getChecksumPolicy() == null
                                            ? RepositoryPolicy.CHECKSUM_POLICY_WARN : r.getSnapshots().getChecksumPolicy();
                                    rb.setSnapshotPolicy(new RepositoryPolicy(
                                            r.getSnapshots().isEnabled(),
                                            updatePolicy, checksumPolicy));
                                }
                                repositories.add(rb.build());
                            }
                        }
                    } catch (MalformedURLException e) {
                        LOG.warn("Cannot use {} URL from Maven settings: {}. Skipping.", r.getUrl(), e.getMessage(), e);
                    }
                }
            }
        }

        return repositories;
    }

    /**
     * Helper method to translate a collection of Strings for remote repository URLs into actual instances of
     * {@link RemoteRepository} added to the passed {@code repositories}. We don't detect duplicates here, and we don't
     * do mirror/proxy processing of the repositories.
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
    public void setMavenSettingsLocation(String mavenSettings) {
        this.mavenSettings = mavenSettings;
    }

    @Override
    public void setMavenSettingsSecurityLocation(String mavenSettingsSecurity) {
        this.mavenSettingsSecurity = mavenSettingsSecurity;
    }

    @Override
    public RepositoryResolver getRepositoryResolver() {
        return repositoryResolver;
    }

    @Override
    public void setRepositoryResolver(RepositoryResolver repositoryResolver) {
        this.repositoryResolver = repositoryResolver;
    }

    @Override
    public void setRepos(String repos) {
        this.repos = repos;
    }

    @Override
    public void setFresh(boolean fresh) {
        this.fresh = fresh;
    }

    @Override
    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    @Override
    public boolean isMavenCentralEnabled() {
        return mavenCentralEnabled;
    }

    @Override
    public void setMavenCentralEnabled(boolean mavenCentralEnabled) {
        this.mavenCentralEnabled = mavenCentralEnabled;
    }

    @Override
    public boolean isMavenApacheSnapshotEnabled() {
        return mavenApacheSnapshotEnabled;
    }

    @Override
    public void setMavenApacheSnapshotEnabled(boolean mavenApacheSnapshotEnabled) {
        this.mavenApacheSnapshotEnabled = mavenApacheSnapshotEnabled;
    }

    private static class AcceptAllDependencyFilter implements DependencyFilter {
        @Override
        public boolean accept(DependencyNode node, List<DependencyNode> parents) {
            return true;
        }
    }

}
