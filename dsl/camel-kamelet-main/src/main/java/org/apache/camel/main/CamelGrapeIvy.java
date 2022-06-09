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
package org.apache.camel.main;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import groovy.grape.GrapeIvy;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.cache.ResolutionCacheManager;
import org.apache.ivy.core.event.IvyEvent;
import org.apache.ivy.core.event.IvyListener;
import org.apache.ivy.core.event.download.PrepareDownloadEvent;
import org.apache.ivy.core.event.resolve.StartResolveEvent;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.util.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static groovy.grape.Grape.AUTO_DOWNLOAD_SETTING;
import static groovy.grape.Grape.DISABLE_CHECKSUMS_SETTING;

public class CamelGrapeIvy extends GrapeIvy {

    private static final Logger LOG = LoggerFactory.getLogger(CamelGrapeIvy.class);

    private static CamelGrapeIvy instance;
    private final Set<Artifact> extra = new HashSet<>();

    public CamelGrapeIvy(boolean reportDownloads) {
        super();

        // use wrapper as facade so we can hook into downloading
        Ivy wrapper = new IvyWrapper(super.getIvyInstance(), reportDownloads);
        setIvyInstance(wrapper);
        IvyContext.getContext().setIvy(wrapper);
    }

    private static synchronized CamelGrapeIvy getInstance(boolean reportDownload) {
        if (instance == null) {
            instance = new CamelGrapeIvy(reportDownload);
        }
        return instance;
    }

    public static Set<MavenGav> download(Map<String, Object> dependency, boolean reportDownload) {
        Set<MavenGav> extra = new HashSet<>();

        CamelGrapeIvy instance = getInstance(reportDownload);
        if (instance != null) {
            if (!dependency.containsKey(AUTO_DOWNLOAD_SETTING)) {
                dependency.put(AUTO_DOWNLOAD_SETTING, "true");
            }
            if (!dependency.containsKey(DISABLE_CHECKSUMS_SETTING)) {
                dependency.put(DISABLE_CHECKSUMS_SETTING, "false");
            }

            instance.extra.clear();
            instance.grab(dependency);
            if (instance.extra.size() > 0) {
                for (Artifact a : instance.extra) {
                    String gid = a.getModuleRevisionId().getOrganisation();
                    String aid = a.getModuleRevisionId().getName();
                    String v = a.getModuleRevisionId().getRevision();
                    MavenGav gav = new MavenGav();
                    gav.setGroupId(gid);
                    gav.setArtifactId(aid);
                    gav.setVersion(v);
                    extra.add(gav);
                }
            }
            instance.extra.clear();
        }

        return extra;
    }

    private class IvyWrapper extends Ivy implements Filter<Artifact> {

        private final Ivy delegate;

        public IvyWrapper(Ivy delegate, boolean reportDownloads) {
            this.delegate = delegate;
            if (reportDownloads) {
                addIvyListener();
            }
        }

        @Override
        public ResolutionCacheManager getResolutionCacheManager() {
            return delegate.getResolutionCacheManager();
        }

        @Override
        public IvySettings getSettings() {
            return delegate.getSettings();
        }

        @Override
        public ResolveReport resolve(ModuleDescriptor md, ResolveOptions options)
                throws ParseException, IOException {
            options.setArtifactFilter(this);
            return delegate.resolve(md, options);
        }

        @Override
        public boolean accept(Artifact a) {
            boolean ok = true;
            String type = a.getType();
            if (type != null) {
                // only jar,pom is supported
                ok = "jar".equals(type) || "pom".equals(type);
                if (!ok && "bundle".equals(type) && "jar".equals(a.getExt())) {
                    // remember as extra download because it cannot be downloaded as bundle type
                    extra.add(a);
                }
            }
            LOG.trace("Accept Artifact: {}={}", a, ok);
            return ok;
        }

        private void addIvyListener() {
            delegate.getEventManager().addIvyListener(new IvyListener() {
                private final Set<String> downloadedArtifacts = new HashSet<>();
                private final Set<String> resolvedDependencies = new HashSet<>();

                @Override
                public void progress(IvyEvent event) {
                    if (event instanceof StartResolveEvent) {
                        DependencyDescriptor[] deps = ((StartResolveEvent) event).getModuleDescriptor().getDependencies();
                        if (deps != null) {
                            for (DependencyDescriptor dd : deps) {
                                var name = dd.toString();
                                if (resolvedDependencies.add(name)) {
                                    LOG.info("Resolving: {}", name);
                                }
                            }
                        }
                    } else if (event instanceof PrepareDownloadEvent) {
                        Artifact[] arts = ((PrepareDownloadEvent) event).getArtifacts();
                        if (arts != null) {
                            for (Artifact a : arts) {
                                var name = a.toString();
                                if (downloadedArtifacts.add(name)) {
                                    LOG.info("Downloading: {}", name);
                                }
                            }
                        }
                    }
                }
            });
        }
    }

}
