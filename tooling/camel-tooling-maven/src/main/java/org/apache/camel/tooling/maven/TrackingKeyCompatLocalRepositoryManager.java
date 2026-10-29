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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;
import java.util.regex.Pattern;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link LocalRepositoryManager} that also accepts the artifacts a newer Maven Resolver tracked as downloaded from
 * the requested repository.
 * <p>
 * Maven 3.10 (Maven Resolver 2.0.23+) records the origin of a downloaded artifact in {@code _remote.repositories} with
 * a URL-qualified key ({@code file>central-<sha1(url)>=}), while Maven Resolver 1.9 only knows the ID-only key
 * ({@code file>central=}). Without this, every artifact downloaded by Maven 3.10 is considered as not downloaded from
 * the requested repository: it cannot be resolved offline and it is verified again against the remote repositories.
 * <p>
 * An URL-qualified entry is accepted for a repository with the same id, whatever the URL, which is the trust level of
 * the ID-only entries (and what Maven Resolver 2.0.23 itself does when it reads ID-only entries). What is written to
 * the local repository is left untouched, so it stays readable by both old and new Maven versions.
 */
class TrackingKeyCompatLocalRepositoryManager implements LocalRepositoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(TrackingKeyCompatLocalRepositoryManager.class);

    private static final String CONFIG_PROP_TRACKING_FILENAME = "aether.enhancedLocalRepository.trackingFilename";
    private static final String DEFAULT_TRACKING_FILENAME = "_remote.repositories";
    // the SHA-1 of the URL (nid_hurl), optionally followed by the one of the context for repository managers
    private static final String URL_QUALIFIER = "-[0-9a-f]{40}(-[0-9a-f]{40})?";

    private final LocalRepositoryManager delegate;
    private final String trackingFilename;

    TrackingKeyCompatLocalRepositoryManager(RepositorySystemSession session, LocalRepositoryManager delegate) {
        this.delegate = delegate;
        this.trackingFilename
                = ConfigUtils.getString(session, DEFAULT_TRACKING_FILENAME, CONFIG_PROP_TRACKING_FILENAME);
    }

    /**
     * Wraps the local repository manager of the given session, unless it is already wrapped.
     */
    static LocalRepositoryManager wrap(RepositorySystemSession session, LocalRepositoryManager lrm) {
        if (lrm == null || lrm instanceof TrackingKeyCompatLocalRepositoryManager) {
            return lrm;
        }
        return new TrackingKeyCompatLocalRepositoryManager(session, lrm);
    }

    @Override
    public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
        LocalArtifactResult result = delegate.find(session, request);
        if (!result.isAvailable() && result.getFile() != null && result.getFile().isFile()) {
            File file = result.getFile();
            Properties props = readTrackingFile(file);
            for (RemoteRepository repository : request.getRepositories()) {
                Pattern key = Pattern.compile(Pattern.quote(file.getName() + '>' + repository.getId()) + URL_QUALIFIER);
                if (props.keySet().stream().anyMatch(k -> key.matcher(k.toString()).matches())) {
                    LOG.debug("Accepting {} tracked with an URL-qualified key of repository {}", file,
                            repository.getId());
                    result.setAvailable(true);
                    result.setRepository(repository);
                    break;
                }
            }
        }
        return result;
    }

    private Properties readTrackingFile(File artifactFile) {
        Properties props = new Properties();
        File trackingFile = new File(artifactFile.getParentFile(), trackingFilename);
        if (trackingFile.isFile()) {
            try (InputStream is = Files.newInputStream(trackingFile.toPath())) {
                props.load(is);
            } catch (IOException e) {
                LOG.debug("Cannot read {}: {}", trackingFile, e.getMessage(), e);
            }
        }
        return props;
    }

    @Override
    public LocalRepository getRepository() {
        return delegate.getRepository();
    }

    @Override
    public String getPathForLocalArtifact(Artifact artifact) {
        return delegate.getPathForLocalArtifact(artifact);
    }

    @Override
    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
        return delegate.getPathForRemoteArtifact(artifact, repository, context);
    }

    @Override
    public String getPathForLocalMetadata(Metadata metadata) {
        return delegate.getPathForLocalMetadata(metadata);
    }

    @Override
    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
        return delegate.getPathForRemoteMetadata(metadata, repository, context);
    }

    @Override
    public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
        delegate.add(session, request);
    }

    @Override
    public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
        return delegate.find(session, request);
    }

    @Override
    public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
        delegate.add(session, request);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
