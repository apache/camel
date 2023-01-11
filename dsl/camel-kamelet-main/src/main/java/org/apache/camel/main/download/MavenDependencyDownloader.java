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
package org.apache.camel.main.download;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.main.injection.DIRegistry;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
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
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.file.FileWagon;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
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
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
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
import org.eclipse.aether.internal.impl.collect.DefaultDependencyCollector;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegate;
import org.eclipse.aether.internal.impl.collect.bf.BfDependencyCollector;
import org.eclipse.aether.internal.impl.collect.df.DfDependencyCollector;
import org.eclipse.aether.internal.impl.slf4j.Slf4jLoggerFactory;
import org.eclipse.aether.internal.impl.synccontext.DefaultSyncContextFactory;
import org.eclipse.aether.internal.impl.synccontext.named.GAVNameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactorySelector;
import org.eclipse.aether.internal.impl.synccontext.named.SimpleNamedLockFactorySelector;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
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
import org.eclipse.aether.transport.wagon.WagonConfigurator;
import org.eclipse.aether.transport.wagon.WagonProvider;
import org.eclipse.aether.transport.wagon.WagonTransporterFactory;
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

public class MavenDependencyDownloader extends ServiceSupport implements DependencyDownloader {

    public static final String MAVEN_CENTRAL_REPO = "https://repo1.maven.org/maven2";
    public static final String APACHE_SNAPSHOT_REPO = "https://repository.apache.org/snapshots";

    private static final Logger LOG = LoggerFactory.getLogger(MavenDependencyDownloader.class);
    private static final String CP = System.getProperty("java.class.path");

    private static final RepositoryPolicy POLICY_DEFAULT = new RepositoryPolicy(
            true, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_WARN);
    private static final RepositoryPolicy POLICY_FRESH = new RepositoryPolicy(
            true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_WARN);
    private static final RepositoryPolicy POLICY_DISABLED = new RepositoryPolicy(
            false, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_IGNORE);

    private String[] bootClasspath;
    private DownloadThreadPool threadPool;
    private DIRegistry registry;
    private ClassLoader classLoader;
    private CamelContext camelContext;
    private final Set<DownloadListener> downloadListeners = new LinkedHashSet<>();
    private final Set<ArtifactDownloadListener> artifactDownloadListeners = new LinkedHashSet<>();

    // repository URLs set from "camel.jbang.repos" property or --repos option.
    private String repos;
    private boolean fresh;

    private String mavenSettings;
    private String mavenSettingsSecurity;
    private RepositorySystem repositorySystem;
    private RepositorySystemSession repositorySystemSession;
    // actual repositories to be used with maven-resolver
    private final List<RemoteRepository> remoteRepositories = new ArrayList<>();
    private RemoteRepository apacheSnapshots;
    private boolean apacheSnapshotsIncluded;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void addDownloadListener(DownloadListener downloadListener) {
        CamelContextAware.trySetCamelContext(downloadListener, getCamelContext());
        downloadListeners.add(downloadListener);
    }

    @Override
    public void addArtifactDownloadListener(ArtifactDownloadListener downloadListener) {
        CamelContextAware.trySetCamelContext(downloadListener, getCamelContext());
        artifactDownloadListeners.add(downloadListener);
    }

    @Override
    public String getRepos() {
        return repos;
    }

    @Override
    public void setRepos(String repos) {
        this.repos = repos;
    }

    @Override
    public boolean isFresh() {
        return fresh;
    }

    @Override
    public void setFresh(boolean fresh) {
        this.fresh = fresh;
    }

    @Override
    public String getMavenSettings() {
        return mavenSettings;
    }

    @Override
    public void setMavenSettings(String mavenSettings) {
        this.mavenSettings = mavenSettings;
    }

    @Override
    public String getMavenSettingsSecurity() {
        return mavenSettingsSecurity;
    }

    @Override
    public void setMavenSettingsSecurity(String mavenSettingsSecurity) {
        this.mavenSettingsSecurity = mavenSettingsSecurity;
    }

    @Override
    public void downloadDependency(String groupId, String artifactId, String version) {
        downloadDependency(groupId, artifactId, version, true);
    }

    @Override
    public void downloadHiddenDependency(String groupId, String artifactId, String version) {
        doDownloadDependency(groupId, artifactId, version, true, true);
    }

    @Override
    public void downloadDependency(String groupId, String artifactId, String version, boolean transitively) {
        doDownloadDependency(groupId, artifactId, version, transitively, false);
    }

    protected void doDownloadDependency(
            String groupId, String artifactId, String version, boolean transitively,
            boolean hidden) {

        if (!hidden) {
            // trigger listener
            for (DownloadListener listener : downloadListeners) {
                listener.onDownloadDependency(groupId, artifactId, version);
            }
        }

        // when running jbang directly then the CP has some existing camel components
        // that essentially is not needed to be downloaded, but we need the listener to trigger
        // to capture that the GAV is required for running the application
        if (CP != null) {
            // is it already on classpath
            String target = artifactId;
            if (version != null) {
                target = target + "-" + version;
            }
            if (CP.contains(target)) {
                // already on classpath
                return;
            }
        }

        // we need version to be able to download from maven
        if (version == null) {
            return;
        }

        String gav = groupId + ":" + artifactId + ":" + version;
        threadPool.download(LOG, () -> {
            LOG.debug("Downloading: {}", gav);
            List<String> deps = List.of(gav);

            List<RemoteRepository> repositories = new ArrayList<>(remoteRepositories);
            // include Apache snapshot to make it easy to use upcoming releases
            if (!apacheSnapshotsIncluded && "org.apache.camel".equals(groupId) && version.contains("SNAPSHOT")) {
                repositories.add(apacheSnapshots);
            }

            List<MavenArtifact> artifacts = resolveDependenciesViaAether(deps, repositories, transitively);
            List<File> files = new ArrayList<>();
            LOG.debug("Resolved {} -> [{}]", gav, artifacts);

            for (MavenArtifact a : artifacts) {
                File file = a.getFile();
                // only add to classpath if not already present (do not trigger listener)
                if (!alreadyOnClasspath(a.getGav().getGroupId(), a.getGav().getArtifactId(),
                        a.getGav().getVersion(), false)) {
                    if (classLoader instanceof DependencyDownloaderClassLoader) {
                        DependencyDownloaderClassLoader ddc = (DependencyDownloaderClassLoader) classLoader;
                        ddc.addFile(file);
                    }
                    files.add(file);
                    LOG.trace("Added classpath: {}", a.getGav());
                }
            }

            // trigger listeners after downloaded and added to classloader
            for (File file : files) {
                for (ArtifactDownloadListener listener : artifactDownloadListeners) {
                    listener.onDownloadedFile(file);
                }
            }
            if (!artifacts.isEmpty()) {
                for (DownloadListener listener : downloadListeners) {
                    listener.onDownloadedDependency(groupId, artifactId, version);
                }
            }

        }, gav);
    }

    @Override
    public MavenArtifact downloadArtifact(String groupId, String artifactId, String version) {
        String gav = groupId + ":" + artifactId + ":" + version;
        LOG.debug("DownloadingArtifact: {}", gav);
        List<String> deps = List.of(gav);

        List<RemoteRepository> repositories = new ArrayList<>(remoteRepositories);
        // include Apache snapshot to make it easy to use upcoming releases
        if (!apacheSnapshotsIncluded && "org.apache.camel".equals(groupId) && version.contains("SNAPSHOT")) {
            repositories.add(apacheSnapshots);
        }

        List<MavenArtifact> artifacts = resolveDependenciesViaAether(deps, repositories, false);
        LOG.debug("Resolved {} -> [{}]", gav, artifacts);

        if (artifacts.size() == 1) {
            return artifacts.get(0);
        }

        return null;
    }

    public boolean alreadyOnClasspath(String groupId, String artifactId, String version) {
        return alreadyOnClasspath(groupId, artifactId, version, true);
    }

    private boolean alreadyOnClasspath(String groupId, String artifactId, String version, boolean listener) {
        // if no artifact then regard this as okay
        if (artifactId == null) {
            return true;
        }

        String target = artifactId;
        if (version != null) {
            target = target + "-" + version;
        }

        if (bootClasspath != null) {
            for (String s : bootClasspath) {
                if (s.contains(target)) {
                    if (listener) {
                        for (DownloadListener dl : downloadListeners) {
                            dl.onDownloadDependency(groupId, artifactId, version);
                        }
                    }
                    // already on classpath
                    return true;
                }
            }
        }

        if (classLoader instanceof URLClassLoader) {
            // create path like target to match against the file url
            String urlTarget = groupId + "/" + artifactId;
            urlTarget = urlTarget.replace('.', '/');
            urlTarget += "/" + version + "/" + target + ".jar";
            urlTarget = FileUtil.normalizePath(urlTarget); // windows vs linux
            URLClassLoader ucl = (URLClassLoader) classLoader;
            for (URL u : ucl.getURLs()) {
                String s = u.toString();
                s = FileUtil.normalizePath(s);
                if (s.contains(urlTarget)) {
                    // trigger listener
                    if (listener) {
                        for (DownloadListener dl : downloadListeners) {
                            dl.onDownloadDependency(groupId, artifactId, version);
                        }
                    }
                    // already on classpath
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onLoadingKamelet(String name) {
        // trigger listener
        for (DownloadListener listener : downloadListeners) {
            listener.onLoadingKamelet(name);
        }
    }

    @Override
    public void onLoadingModeline(String key, String value) {
        // trigger listener
        for (DownloadListener listener : downloadListeners) {
            listener.onLoadingModeline(key, value);
        }
    }

    @Override
    protected void doBuild() throws Exception {
        if (classLoader == null && camelContext != null) {
            classLoader = camelContext.getApplicationContextClassLoader();
        }
        threadPool = new DownloadThreadPool();
        threadPool.setCamelContext(camelContext);
        ServiceHelper.buildService(threadPool);

        // Aether/maven-resolver configuration used without Shrinkwrap
        registry = new DIRegistry();
        final Properties systemProperties = new Properties();
        // MNG-5670 guard against ConcurrentModificationException
        // MNG-6053 guard against key without value
        synchronized (System.getProperties()) {
            systemProperties.putAll(System.getProperties());
        }

        // locations of settings.xml and settings-security.xml
        validateMavenSettingsLocations();

        repositorySystem = configureRepositorySystem(registry, systemProperties,
                mavenSettingsSecurity);

        // read the settings
        Settings settings = mavenConfiguration(registry, repositorySystem, systemProperties, mavenSettings);

        // prepare the Maven session (local repository was configured within the settings)
        // this object is thread safe - it uses configurable download pool, but we're doing our own pooling too
        repositorySystemSession = configureRepositorySystemSession(registry, systemProperties,
                settings, new File(settings.getLocalRepository()));

        // process repositories - both from settings.xml and from --repos option. All are subject to
        // mirrorring and proxying
        List<RemoteRepository> originalRepositories = configureRemoteRepositories(settings, repos, fresh);
        remoteRepositories.addAll(repositorySystem.newResolutionRepositories(repositorySystemSession,
                originalRepositories));

        // finally process apache snapshots
        apacheSnapshots = repositorySystem.newResolutionRepositories(repositorySystemSession,
                Collections.singletonList(apacheSnapshots)).get(0);
    }

    @Override
    protected void doInit() throws Exception {
        RuntimeMXBean mb = ManagementFactory.getRuntimeMXBean();
        if (mb != null) {
            bootClasspath = mb.getClassPath().split("[:|;]");
        }
        ServiceHelper.initService(threadPool);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopAndShutdownService(threadPool);
        if (registry != null) {
            registry.close();
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

        if (!skip && mavenSettingsSecurity == null) {
            String m2settingsSecurity = System.getProperty("user.home") + File.separator + ".m2"
                                        + File.separator + "settings-security.xml";
            if (new File(m2settingsSecurity).isFile()) {
                mavenSettingsSecurity = m2settingsSecurity;
            }
        }
    }

    /**
     * Configure entire {@link RepositorySystem} service
     */
    public RepositorySystem configureRepositorySystem(
            DIRegistry registry,
            Properties systemProperties, String settingsSecurityLocation) {
        basicRepositorySystemConfiguration(registry, systemProperties);
        transportConfiguration(registry, systemProperties);
        settingsConfiguration(registry, settingsSecurityLocation);

        return registry.lookupByClass(RepositorySystem.class);
    }

    /**
     * Configure the basic, necessary requirements of {@link RepositorySystem} in {@link DIRegistry}
     */
    private static void basicRepositorySystemConfiguration(DIRegistry registry, Properties systemProperties) {
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

        // remaining requirements of org.eclipse.aether.internal.impl.DefaultLocalRepositoryProvider
        registry.bind(LocalRepositoryManagerFactory.class, EnhancedLocalRepositoryManagerFactory.class);

        // remaining requirements of org.eclipse.aether.internal.impl.synccontext.DefaultSyncContextFactory
        registry.bind(NamedLockFactorySelector.class, SimpleNamedLockFactorySelector.class);

        // remaining requirements of org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager
        registry.bind(UpdatePolicyAnalyzer.class, DefaultUpdatePolicyAnalyzer.class);
        registry.bind(ChecksumPolicyProvider.class, DefaultChecksumPolicyProvider.class);

        // remaining levels of requirements of org.eclipse.aether.internal.impl.DefaultRepositorySystem

        // requirements of org.eclipse.aether.internal.impl.DefaultUpdateCheckManager
        registry.bind(TrackingFileManager.class, DefaultTrackingFileManager.class);

        // requirements of org.eclipse.aether.internal.impl.synccontext.named.SimpleNamedLockFactorySelector
        registry.bind(NamedLockFactory.class, FileLockNamedLockFactory.class);
        registry.bind(NamedLockFactory.class, LocalReadWriteLockNamedLockFactory.class);
        registry.bind(NameMapper.class, GAVNameMapper.class);

        // requirements of org.apache.maven.repository.internal.DefaultVersionResolver (these are deprecated)
        registry.bind(org.eclipse.aether.impl.SyncContextFactory.class,
                org.eclipse.aether.internal.impl.synccontext.legacy.DefaultSyncContextFactory.class);

        // requirements of org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory
        registry.bind(LocalPathComposer.class, DefaultLocalPathComposer.class);
        registry.bind(LocalPathPrefixComposerFactory.class, DefaultLocalPathPrefixComposerFactory.class);

        // additional services
        registry.bind(org.eclipse.aether.spi.log.LoggerFactory.class, Slf4jLoggerFactory.class);
    }

    /**
     * Configure the transport related requirements of {@link RepositorySystem} in {@link DIRegistry}
     */
    private static void transportConfiguration(DIRegistry registry, Properties systemProperties) {
        // in order to resolve the artifacts we need some connector factories
        registry.bind(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        // repository connectory factory needs transporter provider(s)
        registry.bind(TransporterProvider.class, DefaultTransporterProvider.class);
        // and transport provider needs transport factories
        //        registry.bind(TransporterFactory.class, HttpTransporterFactory.class);
        //        registry.bind(TransporterFactory.class, FileTransporterFactory.class);
        // with wagon factory, we may have more flexibility, because a Wagon
        // may use pre-configured instance of http client (with all the TLS stuff configured)
        registry.bind(TransporterFactory.class, WagonTransporterFactory.class);
        // wagon transporter factory needs a wagon provider
        // wagon transporter uses a hint to select an org.apache.maven.wagon.Wagon. The hint comes from
        // org.apache.maven.wagon.repository.Repository.getProtocol()
        registry.bind("manualWagonProvider", WagonProvider.class, new WagonProvider() {
            @Override
            public Wagon lookup(String roleHint) {
                switch (roleHint) {
                    case "file":
                        return new FileWagon();
                    case "http":
                    case "https":
                        return new HttpWagon();
                    default:
                        return null;
                }
            }

            @Override
            public void release(Wagon wagon) {
            }
        });
        // wagon transporter factory also needs a wagon configurator
        registry.bind("manualWagonConfigurator", WagonConfigurator.class,
                (WagonConfigurator) (wagon, configuration) -> {
                });

        // requirements of org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
        registry.bind(RepositoryLayoutProvider.class, DefaultRepositoryLayoutProvider.class);
        // repository layout provider needs layout factory
        registry.bind(RepositoryLayoutFactory.class, Maven2RepositoryLayoutFactory.class);

        // requirements of org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory
        registry.bind(ChecksumAlgorithmFactorySelector.class, DefaultChecksumAlgorithmFactorySelector.class);
        // checksum algorithm factory selector needs at least MD5 and SHA1 algorithm factories
        registry.bind(ChecksumAlgorithmFactory.class, Md5ChecksumAlgorithmFactory.class);
        registry.bind(ChecksumAlgorithmFactory.class, Sha1ChecksumAlgorithmFactory.class);
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
        // with @org.codehaus.plexus.component.annotations.Component, but with @jakarta.inject.Named
        registry.bind(SettingsReader.class, DefaultSettingsReader.class);
        registry.bind(SettingsWriter.class, DefaultSettingsWriter.class);
        registry.bind(SettingsValidator.class, DefaultSettingsValidator.class);
        registry.bind(SettingsBuilder.class, DefaultSettingsBuilder.class);
    }

    /**
     * Using the configured {@link DIRegistry}, load {@link Settings Maven settings}
     */
    public Settings mavenConfiguration(
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
        if (localRepository == null || "".equals(localRepository.trim())) {
            localRepository = settings.getLocalRepository();
        }
        if (localRepository == null || "".equals(localRepository.trim())) {
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
    public RepositorySystemSession configureRepositorySystemSession(
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

                // handle:
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
        session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT,
                ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT);
        session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT,
                ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT);

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
        session.setOffline(false);
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
        //            session.setCache(null);
        //            session.setData(null);
        //            session.setReadOnly();
        // could be useful to search through kamelet/jbang config
        session.setWorkspaceReader(null);

        return session;
    }

    /**
     * Using the passed ({@code --repos} parameter or {@code camel.jbang.repos} option) and configured (in Maven
     * settings) repomote repository location, prepare a list of {@link RemoteRepository remote repositories} to be used
     * during Maven resolution. These repositories are <b>not yet</b> mirrored/proxied. Use
     * {@link RepositorySystem#newResolutionRepositories} first.
     *
     * @param settings maven settings
     * @param repos    optional, comma-separated list of URLs
     * @param fresh    whether to check for remote updates of the artifacts (SNAPSHOTs)
     */
    public List<RemoteRepository> configureRemoteRepositories(Settings settings, String repos, boolean fresh) {
        List<RemoteRepository> repositories = new ArrayList<>();

        Set<URL> repositoryURLs = new HashSet<>();

        // add maven central first - always
        repositories.add(new RemoteRepository.Builder("central", "default", MAVEN_CENTRAL_REPO)
                .setReleasePolicy(fresh ? POLICY_FRESH : POLICY_DEFAULT)
                .setSnapshotPolicy(POLICY_DISABLED)
                .build());

        // configure Apache snapshots - to be used if needed
        apacheSnapshots = new RemoteRepository.Builder("apache-snapshot", "default", APACHE_SNAPSHOT_REPO)
                .setReleasePolicy(POLICY_DISABLED)
                .setSnapshotPolicy(fresh ? POLICY_FRESH : POLICY_DEFAULT)
                .build();

        // and custom repos and remember URLs to not duplicate the repositories from the settings
        int customCount = 1;
        if (repos != null) {
            for (String repo : repos.split(",")) {
                try {
                    URL url = new URL(repo);
                    if (url.getHost().equals("repo1.maven.org")) {
                        continue;
                    }
                    String id = "custom" + customCount++;
                    RepositoryPolicy releasePolicy = fresh ? POLICY_FRESH : POLICY_DEFAULT;
                    if (repositoryURLs.add(url)) {
                        if (url.getHost().equals("repository.apache.org") && url.getPath().contains("/snapshots")) {
                            apacheSnapshotsIncluded = true;
                            repositories.add(apacheSnapshots);
                        } else {
                            // both snapshots and releases allowed for custom repos
                            repositories.add(new RemoteRepository.Builder(id, "default", repo)
                                    .setReleasePolicy(releasePolicy)
                                    .setSnapshotPolicy(fresh ? POLICY_FRESH : POLICY_DEFAULT)
                                    .build());
                        }
                    }
                } catch (MalformedURLException e) {
                    LOG.warn("Can't use {} URL: {}. Skipping.", repo, e.getMessage(), e);
                }
            }
        }

        // then process the repositories from active profiles of external Maven settings
        for (String profile : settings.getActiveProfiles()) {
            for (Repository r : settings.getProfilesAsMap().get(profile).getRepositories()) {
                try {
                    URL url = new URL(r.getUrl());
                    if (repositoryURLs.add(url)) {
                        if (url.getHost().equals("repository.apache.org") && url.getPath().startsWith("/snapshots")) {
                            apacheSnapshotsIncluded = true;
                        }
                        RemoteRepository.Builder rb = new RemoteRepository.Builder(r.getId(), r.getLayout(), r.getUrl());
                        if (r.getReleases() == null) {
                            rb.setPolicy(fresh ? POLICY_FRESH : POLICY_DEFAULT);
                        }
                        // if someone defines Apache snapshots repository, (s)he has to specify proper policy, sorry.
                        if (r.getSnapshots() == null) {
                            rb.setSnapshotPolicy(POLICY_DISABLED);
                        }
                        repositories.add(rb.build());
                    }
                } catch (MalformedURLException e) {
                    LOG.warn("Can't use {} URL from Maven settings: {}. Skipping.", r.getUrl(), e.getMessage(), e);
                }
            }
        }

        return repositories;
    }

    public List<MavenArtifact> resolveDependenciesViaAether(List<String> depIds, boolean transitively) {
        return resolveDependenciesViaAether(depIds, remoteRepositories, transitively);
    }

    public List<MavenArtifact> resolveDependenciesViaAether(
            List<String> depIds,
            List<RemoteRepository> repositories, boolean transitively) {

        try {
            ArtifactTypeRegistry artifactTypeRegistry = repositorySystemSession.getArtifactTypeRegistry();

            final List<ArtifactRequest> requests = new ArrayList<>(depIds.size());
            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRepositories(repositories);

            for (String depId : depIds) {
                ArtifactRequest ar = new ArtifactRequest();
                ar.setRepositories(repositories);
                ar.setArtifact(MavenGav.parseGav(depId, artifactTypeRegistry).getArtifact());
                requests.add(ar);

                Dependency dependency = new Dependency(ar.getArtifact(), "compile", false);
                collectRequest.addDependency(dependency);
                //                collectRequest.addManagedDependency(...);
            }

            DependencyFilter filter = transitively
                    ? new AcceptAllDependencyFilter()
                    : new AcceptDirectDependencyFilter(requests);

            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, filter);
            DependencyResult dependencyResult
                    = repositorySystem.resolveDependencies(repositorySystemSession, dependencyRequest);

            return dependencyResult.getArtifactResults().stream()
                    .map(dr -> {
                        String gav = dr.getArtifact().getGroupId() + ":"
                                     + dr.getArtifact().getArtifactId() + ":"
                                     + (!"jar".equals(dr.getArtifact().getExtension())
                                             ? dr.getArtifact().getExtension() + ":" : "")
                                     + (!"".equals(dr.getArtifact().getClassifier())
                                             ? dr.getArtifact().getClassifier() + ":" : "")
                                     + dr.getArtifact().getVersion();
                        return new MavenArtifact(MavenGav.parseGav(gav, artifactTypeRegistry), dr.getArtifact().getFile());
                    })
                    .collect(Collectors.toList());
        } catch (DependencyResolutionException e) {
            String msg = "Cannot resolve dependencies in " + repositories.stream().map(RemoteRepository::getUrl)
                    .collect(Collectors.joining(", "));
            throw new DownloadException(msg, e);
        } catch (RuntimeException e) {
            throw new DownloadException("Unknown error occurred while trying to resolve dependencies", e);
        }
    }

    private static class AcceptAllDependencyFilter implements DependencyFilter {
        @Override
        public boolean accept(DependencyNode node, List<DependencyNode> parents) {
            return true;
        }
    }

    private static class AcceptDirectDependencyFilter implements DependencyFilter {
        private final List<ArtifactRequest> requests;

        public AcceptDirectDependencyFilter(List<ArtifactRequest> requests) {
            this.requests = requests;
        }

        @Override
        public boolean accept(DependencyNode node, List<DependencyNode> parents) {
            Dependency dependency = node.getDependency();
            if (dependency == null) {
                return false;
            }
            Artifact current = dependency.getArtifact();
            for (ArtifactRequest ar : requests) {
                if (current.getGroupId().equals(ar.getArtifact().getGroupId())
                        && current.getArtifactId().equals(ar.getArtifact().getArtifactId())
                        && current.getExtension().equals(ar.getArtifact().getExtension())
                        && current.getClassifier().equals(ar.getArtifact().getClassifier())) {
                    return true;
                }
            }
            return false;
        }
    }

}
