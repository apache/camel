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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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
    /** The sources of the last failed reload: retried when the properties change, as a missing property may be why. */
    private final List<Resource> failedSources = new ArrayList<>();
    private Supplier<Collection<Resource>> beanResources;
    /** The parts of the pattern that start with ! (without it): what is left out of reloading. */
    private final List<String> excludes = new ArrayList<>();

    /** How many reloads in a row, all for files under the same data folder, make the reload loop warning. */
    static final int LOOP_RELOADS = 5;
    /** Within how long those reloads must be, in millis. */
    static final long LOOP_MILLIS = 15000;
    /** The last reloads that were for files under one data folder: when, and which folder. */
    private final Deque<DataReload> dataReloads = new ArrayDeque<>();
    /** The folders the reload loop warning has named already. */
    private final Set<String> loopWarned = new HashSet<>();

    private record DataReload(long time, String folder) {
    }

    public Supplier<Collection<Resource>> getBeanResources() {
        return beanResources;
    }

    /**
     * The resources that declare beans, such as a beans.yaml file with no routes. When the Java sources are compiled
     * again, all these resources are loaded again too, so every bean is created again from the new classes: a bean
     * whose class changed, and also a bean that calls it (bean A calling bean B needs the new instance of B).
     */
    public void setBeanResources(Supplier<Collection<Resource>> beanResources) {
        this.beanResources = beanResources;
    }

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
     * patterns can be specified separated by comma. A pattern that starts with <tt>!</tt> excludes what it matches,
     * such as <tt>*,!inbox/**</tt> to watch every file except the files under the inbox folder; a folder that an
     * exclude matches as a whole is not scanned at all.
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

        // the parts that start with ! exclude what they match, such as the data folders the routes read and write
        List<String> includes = new ArrayList<>();
        excludes.clear();
        for (String part : pattern.split(",")) {
            part = part.trim();
            if (part.startsWith("!")) {
                part = part.substring(1).trim();
                if (!part.isEmpty()) {
                    excludes.add(part);
                }
            } else if (!part.isEmpty()) {
                includes.add("*".equals(part) ? "**" : part);
            }
        }
        if (includes.isEmpty()) {
            includes.add("**");
        }

        if (getFileFilter() == null) {
            // file matcher that matches via the ant path matcher
            setFileFilter(f -> {
                // the file name, and the path relative to the starting folder (such as sub/foo.yaml)
                String path = f.getAbsolutePath();
                String relative = null;
                if (path.startsWith(base)) {
                    relative = path.substring(base.length()).replace('\\', '/');
                    while (relative.startsWith("/")) {
                        relative = relative.substring(1);
                    }
                    path = FileUtil.stripPath(path);
                }
                String name = FileUtil.compactPath(f.getPath());
                for (String part : excludes) {
                    if (matches(matcher, part, name, path, relative)) {
                        LOG.trace("Excluding file pattern:!{} path:{}", part, path);
                        return false;
                    }
                }
                for (String part : includes) {
                    boolean result = matches(matcher, part, name, path, relative);
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

    private static boolean matches(AntPathMatcher matcher, String part, String name, String path, String relative) {
        return name.equals(part) || matcher.match(part, path, false)
                || relative != null && matcher.match(part, relative, false);
    }

    /**
     * A folder that an exclude of the pattern matches as a whole, such as <tt>inbox</tt> for <tt>!inbox/**</tt>: the
     * folder is left out of the scan, so the files the routes read and write in it are not walked on every scan.
     */
    @Override
    protected boolean isExcludedDirectory(Path dir) {
        if (excludes.isEmpty() || folder == null) {
            return false;
        }
        String relative = relativePath(dir.toFile());
        if (relative == null || relative.isEmpty()) {
            return false;
        }
        for (String part : excludes) {
            // inbox and inbox/** match the folder itself; a file pattern such as *.json does not leave a folder out
            if (part.equals(relative) || AntPathMatcher.INSTANCE.match(part, relative + "/_", false)) {
                return true;
            }
        }
        return false;
    }

    /** The file a route was loaded from, as an absolute path, or null when it is not a file. */
    private static Path sourcePath(Resource resource) {
        String location = resource != null ? resource.getLocation() : null;
        if (location == null || location.contains(":") && !location.startsWith("file:")) {
            return null;
        }
        try {
            return Paths.get(location.startsWith("file:") ? location.substring(5) : location).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }

    /** The path of the file relative to the watched folder, with / separators, or null when it is not under it. */
    private String relativePath(File file) {
        String base = new File(getFolder()).getAbsolutePath();
        String path = file.getAbsolutePath();
        if (!path.startsWith(base)) {
            return null;
        }
        String relative = path.substring(base.length()).replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return relative;
    }

    /**
     * Routes that write files into a folder under the watched one reload themselves: the reload restarts the routes,
     * they write again, and that is a change again (a file copied into an inbox, moved to done or archive). When the
     * last {@link #LOOP_RELOADS} reloads within {@link #LOOP_MILLIS} were each for files under the same folder only,
     * and none of those files is a source of a route, a properties file or a Java or Groovy source, a WARN names the
     * folder and how to leave it out of reloading, once per folder (CAMEL-25670).
     *
     * @return whether the warning was logged now
     */
    boolean checkReloadLoop(List<File> changed, long now) {
        String data = dataFolder(changed);
        if (data == null) {
            dataReloads.clear();
            return false;
        }
        dataReloads.addLast(new DataReload(now, data));
        while (dataReloads.size() > LOOP_RELOADS) {
            dataReloads.removeFirst();
        }
        long took = now - dataReloads.getFirst().time();
        if (dataReloads.size() == LOOP_RELOADS && took <= LOOP_MILLIS
                && dataReloads.stream().allMatch(r -> r.folder().equals(data)) && loopWarned.add(data)) {
            LOG.warn("Routes reloaded {} times in {} seconds, each time because files under {}/ changed, which are not"
                     + " sources of the routes. If that folder holds data the routes read or write, leave it out of"
                     + " reloading: camel run --data-dir={}, or !{}/** in the reload pattern"
                     + " (camel.main.routesReloadPattern).",
                    LOOP_RELOADS, Math.max(1, Math.round(took / 1000.0)), data, data, data);
            return true;
        }
        return false;
    }

    /**
     * The folder right under the watched one that holds all the changed files, when none of them is a source (a route
     * was loaded from it, or it is a properties, Java or Groovy file), else null.
     */
    private String dataFolder(List<File> changed) {
        // by full path: a data file named as a route source (inbox/route.yaml and route.yaml) is still data
        Set<Path> sources = new HashSet<>();
        for (Route route : getCamelContext().getRoutes()) {
            Path source = sourcePath(route.getSourceResource());
            if (source != null) {
                sources.add(source);
            }
        }
        String data = null;
        for (File file : changed) {
            String name = file.getName();
            if (name.endsWith(".properties") || name.endsWith(".java") || name.endsWith(".groovy")
                    || sources.contains(file.toPath().toAbsolutePath().normalize())) {
                return null;
            }
            String relative = relativePath(file);
            int slash = relative != null ? relative.indexOf('/') : -1;
            if (slash < 1) {
                return null;
            }
            String top = relative.substring(0, slash);
            if (data == null) {
                data = top;
            } else if (!data.equals(top)) {
                return null;
            }
        }
        return data;
    }

    @Override
    protected String startupMessage(File dir) {
        return "Live route reloading enabled (directory: " + dir + ")";
    }

    protected boolean onPropertiesReload(Resource resource, boolean reloadRoutes) throws Exception {
        // the location the properties component knows the file by, which can be written another way than the file
        // that changed, such as file:./application.properties for file:application.properties (CAMEL-25767)
        String location = knownLocation(resource);

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
            changed.putAll(location, tmp);
            // filter to only keep changed properties
            pc.keepOnlyChangeProperties(changed);
        }

        boolean reloaded = false;
        if (changed != null && !changed.isEmpty()) {
            LOG.info("Reloading properties: {}. (Only Camel routes and components can be updated with changes)",
                    resource.getLocation());
            reloaded = pc.reloadProperties(location);
            if (reloaded) {
                pr.onChanged(location, changed);
                // trigger all routes to be reloaded
                if (reloadRoutes) {
                    retryFailedOrReloadAll();
                }
            } else {
                // this may be a new properties file, so we need to add as new known location
                String existing = getPropertiesByLocation(location);
                if (existing == null) {
                    // remove scheme
                    String loc = resource.getLocation();
                    if (loc.contains(":")) {
                        loc = StringHelper.after(loc, ":");
                    }
                    PropertiesSource ps = pc.getPropertiesSourceFactory().newFilePropertiesSource(loc);
                    pc.addPropertiesSource(ps);
                    reloaded = true;
                    // the configuration in a new properties file is applied as well (CAMEL-25767)
                    pr.onChanged(location, changed);
                    if (reloadRoutes) {
                        retryFailedOrReloadAll();
                    }
                }
            }
        }
        return reloaded;
    }

    /**
     * Reloads the routes after the properties changed. A file whose reload failed before is loaded from disk again
     * rather than left on the content that last ran: a missing property is a common reason for that failure, and the
     * property may be what just changed. Without this the file only loads on its next save, so an application whose
     * route uses a property added afterwards keeps running the previous route (CAMEL-25032).
     */
    private void retryFailedOrReloadAll() {
        if (failedSources.isEmpty()) {
            onRouteReload(null, false);
            return;
        }
        List<Resource> retry = new ArrayList<>(failedSources);
        LOG.info("Reloading {} file(s) that failed to load before the properties changed", retry.size());
        try {
            onRouteReload(retry, false);
        } catch (Exception e) {
            // the retry failed for its own reason, which restorePreviousRoutes logged; the properties did reload
            LOG.debug("Retrying the failed reload after the properties changed failed too: {}", e.getMessage(), e);
        }
    }

    /**
     * The location of the properties component for the file of the resource, as the same file can be written in other
     * ways (file:./application.properties and file:application.properties), else the location of the resource.
     */
    private String knownLocation(Resource resource) {
        String location = resource.getLocation();
        Path file = filePath(location);
        if (file == null) {
            return location;
        }
        for (String s : getCamelContext().getPropertiesComponent().getLocations()) {
            String known = s.endsWith(";optional=true") ? s.substring(0, s.length() - 14) : s;
            if (file.equals(filePath(known))) {
                return known;
            }
        }
        return location;
    }

    // the absolute path of a file: location, or null
    private static Path filePath(String location) {
        if (location == null || !location.startsWith("file:")) {
            return null;
        }
        try {
            return Paths.get(location.substring(5)).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
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

    /**
     * One save of several files is reloaded as one change: the properties (and any recompiled Groovy) are applied
     * first, each without reloading the routes, and then the routes of the whole change are reloaded once. A route
     * saved together with a property it uses is therefore built with that property already in place, instead of failing
     * on it and being restored (CAMEL-25032).
     * <p/>
     * A properties or Groovy file that fails is reported at once, as when it changed alone, and the other files still
     * reload. A properties file is not reloaded again: the properties component has its new values already, so the file
     * would have no changes left, and its failure would be lost (CAMEL-25834).
     * <p/>
     * If the routes fail, the route files are reloaded one at a time: the reload then fails on the one file that is
     * wrong and says which it was, so a mistake in one file does not hide behind the others (CAMEL-24860).
     */
    @Override
    protected void onReloadBatch(List<File> changed) {
        checkReloadLoop(changed, System.currentTimeMillis());
        if (changed.size() < 2) {
            super.onReloadBatch(changed);
            return;
        }
        setLastError(null);
        List<File> routeFiles = new ArrayList<>();
        List<Resource> routes = new ArrayList<>();
        boolean others = false;
        for (File file : changed) {
            String name = FileUtil.compactPath(file.getPath());
            Resource resource = PluginHelper.getResourceLoader(getCamelContext()).resolveResource("file:" + name);
            if (name.endsWith(".properties") || name.endsWith(".groovy")) {
                try {
                    others |= name.endsWith(".properties")
                            ? onPropertiesReload(resource, false) : onGroovyReload(resource, false);
                    incSucceededCounter();
                } catch (Exception e) {
                    reloadFailed(name, e);
                }
            } else {
                routeFiles.add(file);
                routes.add(resource);
            }
        }
        try {
            if (!routes.isEmpty()) {
                onRouteReload(routes, false);
            } else if (others) {
                // only properties or Groovy changed, so the routes are reloaded to pick them up
                retryFailedOrReloadAll();
            }
            // the counter is files reloaded, as it is when they are reloaded one at a time
            for (int i = 0; i < routes.size(); i++) {
                incSucceededCounter();
            }
        } catch (Exception e) {
            LOG.debug("Reloading {} changed route file(s) together failed, reloading them one at a time to find the"
                      + " file that is wrong: {}",
                    routes.size(), e.getMessage(), e);
            super.onReloadBatch(routeFiles);
        }
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
                // remember all the sources of the current routes (except the updated, and those whose file is gone)
                if (rs != null && !isDeletedFile(rs) && !equalResourceLocation(resources, rs)
                        && !equalResourceLocation(sources, rs)) {
                    sources.add(rs);
                }
            });
        }

        // the revision of each route before the reload, to tell the routes it changed (CAMEL-25771)
        Map<String, Integer> revisions = new HashMap<>();
        getCamelContext().getRoutes().forEach(r -> revisions.put(r.getId(), r.getRevision()));

        try {
            // should all existing routes be stopped and removed first?
            if (removeAllRoutes) {
                // remember all the sources of the current routes (except the updated)
                // (a file with several routes is one source: adding it once per route would load it several
                // times and fail on a duplicate route id, CAMEL-24866)
                // (the file of a route may have been removed since it loaded: on-demand reload only passes the files
                // that exist, so its routes are removed like those of a changed file, CAMEL-25427)
                // (a route from a templated route needs the file with the route template, and the file with the
                // templated route, CAMEL-25598; a route from a Kamelet is created again by its parent route)
                // (the file with the templated route is only loaded again when its route template comes from a file:
                // a route template added from Java is removed below and nothing adds it again, so loading the
                // templated route would fail the whole reload, CAMEL-25616)
                getCamelContext().getRoutes().forEach(r -> {
                    if (!r.isCreatedByKamelet()) {
                        addRouteSource(resources, sources, r.getSourceResource());
                        if (r.getSourceResource() != null) {
                            addRouteSource(resources, sources, r.getTemplatedRouteResource());
                        }
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
                    // a file that is gone would fail the reload, and every reload after it (CAMEL-25831)
                    if (!sources.contains(extra) && !isDeletedFile(extra)) {
                        sources.add(extra);
                    }
                }
            }
            // the Java sources are compiled again, so all beans are created again: a bean of a changed class, and the
            // beans that call it (not those whose file is gone, such as a Java class that was deleted, CAMEL-25831)
            if (beanResources != null && sources.stream().anyMatch(RouteWatcherReloadStrategy::isJavaSource)) {
                Collection<Resource> beans = beanResources.get();
                if (beans != null) {
                    for (Resource bean : beans) {
                        if (bean != null && !isDeletedFile(bean) && !equalResourceLocation(sources, bean)) {
                            sources.add(bean);
                        }
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
            failedSources.clear();
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
                // the routes the reload changed, known when the routes have checksums (camel-xml-io)
                int changed = 0;
                boolean checksums = true;
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
                        String change = "";
                        if (route.getRevision() == 0) {
                            checksums = false;
                        } else if (!revisions.containsKey(id)) {
                            change = " (new)";
                            changed++;
                        } else if (revisions.get(id) != route.getRevision()) {
                            change = " (changed)";
                            changed++;
                        }
                        lines.add(String.format("    %s %s (%s) (source: %s)%s", status, id, uri, loc, change));
                    }
                }
                if (checksums) {
                    LOG.info("Routes reloaded summary (total:{} started:{} changed:{})", total, started, changed);
                } else {
                    LOG.info("Routes reloaded summary (total:{} started:{})", total, started);
                }
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
            // mistake in one file leaves the rest running (CAMEL-24860); the failed file loads on its next save,
            // or when the properties change and a missing property was the reason it failed (CAMEL-25032)
            restorePreviousRoutes(resources, e);
            failedSources.clear();
            if (resources != null) {
                failedSources.addAll(resources);
            }
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
            if (rs != null && !isDeletedFile(rs) && (failed == null || !equalResourceLocation(failed, rs))) {
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
     * Adds the source of a running route to the sources to load again, unless it is one of the updated resources, it is
     * already added, or its file is gone.
     */
    private static void addRouteSource(Collection<Resource> resources, List<Resource> sources, Resource rs) {
        if (rs != null && !isDeletedFile(rs) && !equalResourceLocation(resources, rs)
                && !equalResourceLocation(sources, rs)) {
            sources.add(rs);
        }
    }

    /**
     * Whether the resource is a file that no longer exists. Only the location is looked at: a source restored from its
     * last loaded content is kept in memory and always exists, but its file may have been removed since (CAMEL-25427).
     */
    private static boolean isDeletedFile(Resource resource) {
        String loc = resource.getLocation();
        if (loc == null || !loc.startsWith("file:")) {
            return false;
        }
        return !Files.exists(Paths.get(loc.substring(5)));
    }

    /**
     * Whether the target is loading any of the given sources
     */
    private static boolean isJavaSource(Resource resource) {
        return resource.getLocation() != null && resource.getLocation().endsWith(".java");
    }

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
