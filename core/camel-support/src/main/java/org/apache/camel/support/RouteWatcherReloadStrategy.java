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
package org.apache.camel.support;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StartupSummaryLevel;
import org.apache.camel.spi.ContextServiceLoaderPluginResolver;
import org.apache.camel.spi.GroovyScriptCompiler;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesReload;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.AntPathMatcher;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watcher strategy for triggering reloading of Camel routes in a running Camel application. The strategy watches a
 * directory (and subdirectories) for file changes. By default, the strategy is matching Camel routes in XML or YAML
 * files.
 */
public class RouteWatcherReloadStrategy extends FileWatcherResourceReloadStrategy {

    /**
     * Special when reloading routes(s) requires to also ensure other resources are reloaded together such as
     * camel-java-joor-dsl to ensure all resources are compiled in the same compilation unit.
     */
    public static final String RELOAD_RESOURCES = "RouteWatcherReloadResources";

    private static final Logger LOG = LoggerFactory.getLogger(RouteWatcherReloadStrategy.class);

    private static final String DEFAULT_PATTERN = "*.yaml,*.xml";

    private String pattern;
    private boolean removeAllRoutes = true;
    private final List<Resource> previousSources = new ArrayList<>();
    /** The content of the sources of the last successful reload, by location: what a failed reload goes back to. */
    private final Map<String, byte[]> lastGoodContent = new ConcurrentHashMap<>();

    public RouteWatcherReloadStrategy() {
    }

    public RouteWatcherReloadStrategy(String directory) {
        this(directory, false);
    }

    public RouteWatcherReloadStrategy(String directory, boolean recursive) {
        super(directory, recursive);
    }

    public String getPattern() {
        return pattern;
    }

    /**
     * Used for inclusive filtering of routes from directories.
     *
     * Typical used for specifying to accept routes in XML or YAML files, such as <tt>*.yaml,*.xml</tt>. Multiple
     * patterns can be specified separated by comma.
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public boolean isRemoveAllRoutes() {
        return removeAllRoutes;
    }

    /**
     * When reloading routes should all existing routes be stopped and removed.
     *
     * By default, Camel will stop and remove all existing routes before reloading routes. This ensures that only the
     * reloaded routes will be active. If disabled then only routes with the same route id is updated, and any existing
     * routes are continued to run.
     */
    public void setRemoveAllRoutes(boolean removeAllRoutes) {
        this.removeAllRoutes = removeAllRoutes;
    }

    @Override
    protected void doStart() throws Exception {
        if (getResourceReload() == null) {
            // attach listener that triggers the route update
            setResourceReload((name, resource) -> {
                if (name.endsWith(".properties")) {
                    onPropertiesReload(resource, true);
                } else if (name.endsWith(".groovy")) {
                    onGroovyReload(resource, true);
                } else {
                    onRouteReload(List.of(resource), false);
                }
            });
        }

        // the following is all related to watching files on disk
        if (folder == null) {
            return;
        }
        if (pattern == null || pattern.isBlank()) {
            pattern = DEFAULT_PATTERN;
        } else if ("*".equals(pattern)) {
            pattern = "**"; // use ant style matching to match everything
        }

        final String base = new File(getFolder()).getAbsolutePath();
        final AntPathMatcher matcher = new AntPathMatcher();

        if (getFileFilter() == null) {
            // file matcher that matches via the ant path matcher
            final String[] parts = pattern.split(",");
            setFileFilter(f -> {
                for (String part : parts) {
                    // strip starting directory, so we have a relative name to the starting folder
                    String path = f.getAbsolutePath();
                    if (path.startsWith(base)) {
                        path = FileUtil.stripPath(path);
                    }

                    String name = FileUtil.compactPath(f.getPath());
                    boolean exact = name.equals(part);
                    boolean result = exact || matcher.match(part, path, false);
                    LOG.trace("Accepting file pattern:{} path:{} -> {}", part, path, result);

                    if (result) {
                        return true;
                    }
                }
                return false;
            });
        }

        super.doStart();
    }

    @Override
    protected String startupMessage(File dir) {
        return "Live route reloading enabled (directory: " + dir + ")";
    }

    protected boolean onPropertiesReload(Resource resource, boolean reloadRoutes) throws Exception {
        // optimize to only update if something changed
        OrderedLocationProperties changed = null;

        PropertiesComponent pc = getCamelContext().getPropertiesComponent();
        PropertiesReload pr = getCamelContext().hasService(PropertiesReload.class);
        if (pr != null) {
            // load the properties, so we can update (remember location)
            InputStream is = resource.getInputStream();
            OrderedProperties tmp = new OrderedProperties();
            try {
                tmp.load(is);
            } finally {
                IOHelper.close(is);
            }
            changed = new OrderedLocationProperties();
            changed.putAll(resource.getLocation(), tmp);
            // filter to only keep changed properties
            pc.keepOnlyChangeProperties(changed);
        }

        boolean reloaded = false;
        if (changed != null && !changed.isEmpty()) {
            LOG.info("Reloading properties: {}. (Only Camel routes and components can be updated with changes)",
                    resource.getLocation());
            reloaded = pc.reloadProperties(resource.getLocation());
            if (reloaded) {
                pr.onReload(resource.getLocation(), changed);
                // trigger all routes to be reloaded
                if (reloadRoutes) {
                    onRouteReload(null, false);
                }
            } else {
                // this may be a new properties file, so we need to add as new known location
                String existing = getPropertiesByLocation(resource.getLocation());
                if (existing == null) {
                    // remove scheme
                    String loc = resource.getLocation();
                    if (loc.contains(":")) {
                        loc = StringHelper.after(loc, ":");
                    }
                    PropertiesSource ps = pc.getPropertiesSourceFactory().newFilePropertiesSource(loc);
                    pc.addPropertiesSource(ps);
                    reloaded = true;
                }
            }
        }
        return reloaded;
    }

    private String getPropertiesByLocation(String loc) {
        PropertiesComponent pc = getCamelContext().getPropertiesComponent();
        for (String s : pc.getLocations()) {
            if (s.endsWith(";optional=true")) {
                s = s.substring(0, s.length() - 14);
            }
            if (Objects.equals(s, loc)) {
                return loc;
            }
        }
        return null;
    }

    protected boolean onGroovyReload(Resource resource, boolean reloadRoutes) throws Exception {
        GroovyScriptCompiler compiler
                = getCamelContext().getCamelContextExtension().getContextPlugin(GroovyScriptCompiler.class);
        if (compiler != null) {
            compiler.recompile(resource);
            // trigger all routes to be reloaded (which will also trigger reloading this resource)
            if (reloadRoutes) {
                onRouteReload(null, false);
            }
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    protected void onRouteReload(Collection<Resource> resources, boolean removeEverything) {
        // notify context service plugins before reloading routes
        ContextServiceLoaderPluginResolver pluginResolver
                = getCamelContext().getCamelContextExtension().getContextPlugin(ContextServiceLoaderPluginResolver.class);
        if (pluginResolver != null) {
            pluginResolver.onReload();
        }

        // remember all existing resources
        List<Resource> sources = new ArrayList<>();

        if (!previousSources.isEmpty()) {
            // last update failed, so we need to update all previous sources to ensure we go back
            // to the last working set
            previousSources.forEach(rs -> {
                // remember all the sources of the current routes (except the updated)
                if (rs != null && !equalResourceLocation(resources, rs) && !equalResourceLocation(sources, rs)) {
                    sources.add(rs);
                }
            });
        }

        try {
            // should all existing routes be stopped and removed first?
            if (removeAllRoutes) {
                // remember all the sources of the current routes (except the updated)
                // (a file with several routes is one source: adding it once per route would load it several
                // times and fail on a duplicate route id, CAMEL-24866)
                getCamelContext().getRoutes().forEach(r -> {
                    Resource rs = r.getSourceResource();
                    if (rs != null && !equalResourceLocation(resources, rs) && !equalResourceLocation(sources, rs)) {
                        sources.add(rs);
                    }
                });
                // first stop and remove all routes
                getCamelContext().getRouteController().removeAllRoutes();
                // remove left-over route templates and endpoints, so we can start on a fresh
                getCamelContext().removeRouteTemplates("*");
                getCamelContext().getEndpointRegistry().clear();
            }

            if (resources != null) {
                for (Resource resource : resources) {
                    if (Files.exists(Paths.get(resource.getURI())) && !equalResourceLocation(sources, resource)) {
                        sources.add(resource);
                    }
                }
            }

            Collection<Resource> extras
                    = getCamelContext().getRegistry().lookupByNameAndType(RELOAD_RESOURCES, Collection.class);
            if (extras != null) {
                for (Resource extra : extras) {
                    if (!sources.contains(extra)) {
                        sources.add(extra);
                    }
                }
            }

            // just in case remember this set of sources as what was attempted previously to update
            // in case the update fails with an exception
            previousSources.clear();
            previousSources.addAll(sources);

            // special situation where we remove all routes
            if (removeEverything) {
                sources.clear();
            }

            // reload those other routes that was stopped and removed as we want to keep running those
            Set<String> ids
                    = PluginHelper.getRoutesLoader(getCamelContext()).updateRoutes(sources);

            // update okay, so clear as we do not need to remember those anymore
            previousSources.clear();
            if (removeEverything) {
                // the route files are gone and nothing runs: the remembered content goes with them, so a later
                // failed reload cannot put a deleted file back (CAMEL-24899)
                lastGoodContent.clear();
            } else {
                rememberContent(sources);
            }

            if (!ids.isEmpty()) {
                List<String> lines = new ArrayList<>();
                int total = 0;
                int started = 0;
                for (String id : ids) {
                    total++;
                    Route route = getCamelContext().getRoute(id);
                    if (route != null) {
                        ServiceStatus status = getCamelContext().getRouteController().getRouteStatus(id);
                        if (status == null) {
                            // undefined status of route (should not really happen)
                            LOG.warn("Cannot get route status for route: {}. This route is skipped.", id);
                            continue;
                        }
                        if (ServiceStatus.Started.equals(status)) {
                            started++;
                        }
                        // use basic endpoint uri to not log verbose details or potential sensitive data
                        String uri = route.getEndpoint().getEndpointBaseUri();
                        uri = URISupport.sanitizeUri(uri);
                        String loc = route.getSourceLocationShort();
                        if (loc == null) {
                            loc = "";
                        }
                        lines.add(String.format("    %s %s (%s) (source: %s)", status, id, uri, loc));
                    }
                }
                LOG.info("Routes reloaded summary (total:{} started:{})", total, started);
                // if we are default/verbose then log each route line
                if (getCamelContext().getStartupSummaryLevel() == StartupSummaryLevel.Default
                        || getCamelContext().getStartupSummaryLevel() == StartupSummaryLevel.Verbose) {
                    for (String line : lines) {
                        LOG.info(line);
                    }
                }
            }

            // fire events for routes reloaded
            int index = 1;
            int total = ids.size();
            for (String id : ids) {
                Route route = getCamelContext().getRoute(id);
                if (route != null) {
                    EventHelper.notifyRouteReloaded(getCamelContext(), route, index++, total);
                }
            }

            if (!removeAllRoutes) {
                // if not all previous routes are removed then to have safe route reloading
                // it is recommended to configure ids on the routes
                StringJoiner sj = new StringJoiner("\n    ");
                for (String id : ids) {
                    Route route = getCamelContext().getRoute(id);
                    if (route != null && !route.isCustomId()) {
                        sj.add(route.getEndpoint().getEndpointUri());
                    }
                }
                if (sj.length() > 0) {
                    LOG.warn(
                            "Routes with no id's detected. Its recommended to assign route id's to your routes so Camel can reload the routes correctly.\n    Unassigned routes:\n    {}",
                            sj);
                }
            }
        } catch (Exception e) {
            // the routes that ran before were removed above and the new ones failed to load: the app has no routes
            // until the next successful reload. Restore the previous routes now, without the failed resources, so a
            // mistake in one file leaves the rest running (CAMEL-24860); the failed file loads on its next save
            restorePreviousRoutes(resources, e);
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }

    /**
     * Remembers the content of the sources that loaded, so a later failed reload of one of them can go back to the
     * version that ran (the file on disk is then the broken one). Only the sources of the current set are kept; the
     * caller drops the lot when everything is removed.
     */
    private void rememberContent(Collection<Resource> sources) {
        if (!removeAllRoutes || sources == null) {
            return;
        }
        Set<String> current = new HashSet<>();
        for (Resource rs : sources) {
            if (rs == null || !rs.exists()) {
                continue;
            }
            current.add(rs.getLocation());
            try (InputStream is = rs.getInputStream()) {
                lastGoodContent.put(rs.getLocation(), is.readAllBytes());
            } catch (Exception e) {
                LOG.debug("Cannot remember the content of {} for a later failed reload: {}", rs.getLocation(),
                        e.getMessage());
            }
        }
        lastGoodContent.keySet().retainAll(current);
    }

    /**
     * Reloads the sources of the routes that ran before a failed reload, so a mistake in one file does not leave the
     * application without routes: the other files from disk, and the failed file from the content that last loaded,
     * which is kept in memory for a project whose routes are all in one file (CAMEL-24899). After a successful restore
     * the remembered set is cleared: the running routes are the last working set again, and the next reload collects
     * their sources itself. The file on disk is untouched and its next save is loaded as usual.
     */
    protected void restorePreviousRoutes(Collection<Resource> failed, Exception cause) {
        if (!removeAllRoutes || previousSources.isEmpty()) {
            return;
        }
        List<Resource> restore = new ArrayList<>();
        for (Resource rs : previousSources) {
            if (rs != null && (failed == null || !equalResourceLocation(failed, rs))) {
                restore.add(rs);
            }
        }
        // the failed file itself: its last loaded content, kept in memory, because the file on disk is the broken
        // version (a project with a single route file has nothing else to go back to, CAMEL-24899)
        int fromMemory = 0;
        if (failed != null) {
            for (Resource rs : failed) {
                byte[] content = rs != null ? lastGoodContent.get(rs.getLocation()) : null;
                if (content != null && !equalResourceLocation(restore, rs)) {
                    restore.add(ResourceHelper.fromBytes(rs.getLocation(), content));
                    fromMemory++;
                }
            }
        }
        if (restore.isEmpty()) {
            LOG.warn("Reload failed and there are no previous routes to restore: the application runs without routes"
                     + " until the file is fixed");
            return;
        }
        try {
            // a partial load may have left routes or endpoints behind
            getCamelContext().getRouteController().removeAllRoutes();
            getCamelContext().removeRouteTemplates("*");
            getCamelContext().getEndpointRegistry().clear();
            Set<String> ids = PluginHelper.getRoutesLoader(getCamelContext()).updateRoutes(restore);
            // the running routes are the last working set again: the next reload collects their sources itself
            previousSources.clear();
            LOG.warn("Reload failed due to: {}. The previous routes were restored ({} route(s) running{}); the changed"
                     + " file loads on its next save",
                    cause.getMessage(), ids.size(),
                    fromMemory > 0 ? ", " + fromMemory + " from the last loaded content of the changed file" : "");
        } catch (Exception e) {
            LOG.warn("Reload failed and the previous routes could not be restored due to: {}. The application runs"
                     + " without routes until the file is fixed",
                    e.getMessage(), e);
        }
    }

    /**
     * Whether the target is loading any of the given sources
     */
    private static boolean equalResourceLocation(Collection<Resource> sources, Resource target) {
        if (sources == null || target == null || sources.isEmpty()) {
            return false;
        }
        for (Resource source : sources) {
            if (equalResourceLocation(source, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the two resources are loading the same resource
     */
    private static boolean equalResourceLocation(Resource source, Resource target) {
        if (source == null || target == null) {
            return false;
        }

        // use URI to match as file/classpath resources may refer to the same uri
        URI u1 = source.getURI();
        URI u2 = target.getURI();
        boolean answer = u1.equals(u2);
        if (!answer) {
            // file and classpath may refer to the same when they have src/main/resources && target/classes
            String s1 = u1.toString().replace("src/main/resources/", "").replace("src/test/resources/", "")
                    .replace("target/classes/", "");
            String s2 = u2.toString().replace("src/main/resources/", "").replace("src/test/resources/", "")
                    .replace("target/classes/", "");
            answer = s1.equals(s2);
        }
        return answer;
    }

}
