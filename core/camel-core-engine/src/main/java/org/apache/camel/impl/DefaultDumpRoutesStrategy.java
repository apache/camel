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
package org.apache.camel.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NamedNode;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.DumpRoutesStrategy;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.ServiceFactory;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.LoggerHelper.stripSourceLocationLineNumber;

/**
 * Default {@link DumpRoutesStrategy} that dumps the routes to standard logger.
 */
@ServiceFactory("default-" + DumpRoutesStrategy.FACTORY)
public class DefaultDumpRoutesStrategy extends ServiceSupport implements DumpRoutesStrategy, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDumpRoutesStrategy.class);
    private static final String DIVIDER = "--------------------------------------------------------------------------------";

    private final AtomicInteger counter = new AtomicInteger();
    private CamelContext camelContext;

    private String include = "routes";
    private boolean resolvePlaceholders = true;
    private boolean uriAsParameters;
    private boolean log = true;
    private String directory;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getInclude() {
        return include;
    }

    public void setInclude(String include) {
        this.include = include;
    }

    public boolean isResolvePlaceholders() {
        return resolvePlaceholders;
    }

    public void setResolvePlaceholders(boolean resolvePlaceholders) {
        this.resolvePlaceholders = resolvePlaceholders;
    }

    public boolean isLog() {
        return log;
    }

    public void setLog(boolean log) {
        this.log = log;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public boolean isUriAsParameters() {
        return uriAsParameters;
    }

    public void setUriAsParameters(boolean uriAsParameters) {
        this.uriAsParameters = uriAsParameters;
    }

    @Override
    public void dumpRoutes(String format) {
        if ("yaml".equalsIgnoreCase(format)) {
            doDumpRoutesAsYaml(camelContext);
        } else if ("xml".equalsIgnoreCase(format)) {
            doDumpRoutesAsXml(camelContext);
        }
    }

    protected void doDumpRoutesAsYaml(CamelContext camelContext) {
        final ModelToYAMLDumper dumper = PluginHelper.getModelToYAMLDumper(camelContext);
        final Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        final DummyResource dummy = new DummyResource(null, null);

        if (include.contains("*") || include.contains("all") || include.contains("routes")) {
            int size = model.getRouteDefinitions().size();
            if (size > 0) {
                Map<Resource, RoutesDefinition> groups = new LinkedHashMap<>();
                for (RouteDefinition route : model.getRouteDefinitions()) {
                    Resource res = route.getResource();
                    if (res == null) {
                        res = dummy;
                    }
                    RoutesDefinition routes = groups.computeIfAbsent(res, resource -> new RoutesDefinition());
                    routes.getRoutes().add(route);
                }
                StringBuilder sbLog = new StringBuilder();
                for (Map.Entry<Resource, RoutesDefinition> entry : groups.entrySet()) {
                    RoutesDefinition def = entry.getValue();
                    Resource resource = entry.getKey();

                    StringBuilder sbLocal = new StringBuilder();
                    doDumpYaml(camelContext, def, resource == dummy ? null : resource, dumper, "routes", sbLocal, sbLog);
                    // dump each resource into its own file
                    doDumpToDirectory(resource, sbLocal, "routes", "yaml");
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} routes as YAML", size);
                    LOG.info("{}", sbLog);
                }
            }
        }

        if (include.contains("*") || include.contains("all") || include.contains("rests")) {
            int size = model.getRestDefinitions().size();
            if (size > 0) {
                Map<Resource, RestsDefinition> groups = new LinkedHashMap<>();
                for (RestDefinition rest : model.getRestDefinitions()) {
                    Resource res = rest.getResource();
                    if (res == null) {
                        res = dummy;
                    }
                    RestsDefinition rests = groups.computeIfAbsent(res, resource -> new RestsDefinition());
                    rests.getRests().add(rest);
                }
                StringBuilder sbLog = new StringBuilder();
                for (Map.Entry<Resource, RestsDefinition> entry : groups.entrySet()) {
                    RestsDefinition def = entry.getValue();
                    Resource resource = entry.getKey();

                    StringBuilder sbLocal = new StringBuilder();
                    doDumpYaml(camelContext, def, resource == dummy ? null : resource, dumper, "rests", sbLocal, sbLog);
                    // dump each resource into its own file
                    doDumpToDirectory(resource, sbLocal, "rests", "yaml");
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} rests as YAML", size);
                    LOG.info("{}", sbLog);
                }
            }
        }

        if (include.contains("*") || include.contains("all") || include.contains("routeTemplates")
                || include.contains("route-templates")) {
            int size = model.getRouteTemplateDefinitions().size();
            if (size > 0) {
                Map<Resource, RouteTemplatesDefinition> groups = new LinkedHashMap<>();
                for (RouteTemplateDefinition rt : model.getRouteTemplateDefinitions()) {
                    Resource res = rt.getResource();
                    if (res == null) {
                        res = dummy;
                    }
                    RouteTemplatesDefinition rests = groups.computeIfAbsent(res, resource -> new RouteTemplatesDefinition());
                    rests.getRouteTemplates().add(rt);
                }
                StringBuilder sbLog = new StringBuilder();
                for (Map.Entry<Resource, RouteTemplatesDefinition> entry : groups.entrySet()) {
                    RouteTemplatesDefinition def = entry.getValue();
                    Resource resource = entry.getKey();

                    StringBuilder sbLocal = new StringBuilder();
                    doDumpYaml(camelContext, def, resource == dummy ? null : resource, dumper, "route-templates", sbLocal,
                            sbLog);
                    // dump each resource into its own file
                    doDumpToDirectory(resource, sbLocal, "route-templates", "yaml");
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} route-templates as YAML", size);
                    LOG.info("{}", sbLog);
                }
            }
        }
    }

    protected void doDumpYaml(
            CamelContext camelContext, NamedNode def, Resource resource,
            ModelToYAMLDumper dumper, String kind, StringBuilder sbLocal, StringBuilder sbLog) {
        try {
            String dump = dumper.dumpModelAsYaml(camelContext, def, resolvePlaceholders, uriAsParameters);
            sbLocal.append(dump);
            appendLogDump(resource, dump, sbLog);
        } catch (Exception e) {
            LOG.warn("Error dumping {}} to YAML due to {}. This exception is ignored.", kind, e.getMessage(), e);
        }
    }

    protected void doDumpRoutesAsXml(CamelContext camelContext) {
        final ModelToXMLDumper dumper = PluginHelper.getModelToXMLDumper(camelContext);
        final Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        final DummyResource dummy = new DummyResource(null, null);

        if (include.contains("*") || include.contains("all") || include.contains("routes")) {
            int size = model.getRouteDefinitions().size();
            if (size > 0) {
                Map<Resource, RoutesDefinition> groups = new LinkedHashMap<>();
                for (RouteDefinition route : model.getRouteDefinitions()) {
                    Resource res = route.getResource();
                    if (res == null) {
                        res = dummy;
                    }
                    RoutesDefinition routes = groups.computeIfAbsent(res, resource -> new RoutesDefinition());
                    routes.getRoutes().add(route);
                }
                StringBuilder sbLog = new StringBuilder();
                for (Map.Entry<Resource, RoutesDefinition> entry : groups.entrySet()) {
                    RoutesDefinition def = entry.getValue();
                    Resource resource = entry.getKey();

                    StringBuilder sbLocal = new StringBuilder();
                    doDumpXml(camelContext, def, resource == dummy ? null : resource, dumper, "route", "routes", sbLocal,
                            sbLog);
                    // dump each resource into its own file
                    doDumpToDirectory(resource, sbLocal, "routes", "xml");
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} routes as XML", size);
                    LOG.info("{}", sbLog);
                }
            }
        }

        if (include.contains("*") || include.contains("all") || include.contains("rests")) {
            int size = model.getRestDefinitions().size();
            if (size > 0) {
                Map<Resource, RestsDefinition> groups = new LinkedHashMap<>();
                for (RestDefinition rest : model.getRestDefinitions()) {
                    Resource res = rest.getResource();
                    if (res == null) {
                        res = dummy;
                    }
                    RestsDefinition routes = groups.computeIfAbsent(res, resource -> new RestsDefinition());
                    routes.getRests().add(rest);
                }
                StringBuilder sbLog = new StringBuilder();
                for (Map.Entry<Resource, RestsDefinition> entry : groups.entrySet()) {
                    RestsDefinition def = entry.getValue();
                    Resource resource = entry.getKey();

                    StringBuilder sbLocal = new StringBuilder();
                    doDumpXml(camelContext, def, resource == dummy ? null : resource, dumper, "rest", "rests", sbLocal, sbLog);
                    // dump each resource into its own file
                    doDumpToDirectory(resource, sbLocal, "rests", "xml");
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} rests as XML", size);
                    LOG.info("{}", sbLog);
                }
            }
        }

        if (include.contains("*") || include.contains("all") || include.contains("routeTemplates")
                || include.contains("route-templates")) {
            int size = model.getRouteTemplateDefinitions().size();
            if (size > 0) {
                Map<Resource, RouteTemplatesDefinition> groups = new LinkedHashMap<>();
                for (RouteTemplateDefinition rt : model.getRouteTemplateDefinitions()) {
                    Resource res = rt.getResource();
                    if (res == null) {
                        res = dummy;
                    }
                    RouteTemplatesDefinition routes = groups.computeIfAbsent(res, resource -> new RouteTemplatesDefinition());
                    routes.getRouteTemplates().add(rt);
                }
                StringBuilder sbLog = new StringBuilder();
                for (Map.Entry<Resource, RouteTemplatesDefinition> entry : groups.entrySet()) {
                    RouteTemplatesDefinition def = entry.getValue();
                    Resource resource = entry.getKey();

                    StringBuilder sbLocal = new StringBuilder();
                    doDumpXml(camelContext, def, resource == dummy ? null : resource, dumper, "routeTemplate",
                            "route-templates", sbLocal, sbLog);
                    // dump each resource into its own file
                    doDumpToDirectory(resource, sbLocal, "route-templates", "xml");
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} route-templates as XML", size);
                    LOG.info("{}", sbLog);
                }
            }
        }
    }

    protected void doDumpXml(
            CamelContext camelContext, NamedNode def, Resource resource,
            ModelToXMLDumper dumper, String replace, String kind, StringBuilder sbLocal, StringBuilder sbLog) {
        try {
            String xml = dumper.dumpModelAsXml(camelContext, def, resolvePlaceholders);
            // lets separate with empty line
            xml = StringHelper.replaceFirst(xml, "xmlns=\"http://camel.apache.org/schema/spring\">",
                    "xmlns=\"http://camel.apache.org/schema/spring\">\n");
            xml = xml.replace("</" + replace + ">", "</" + replace + ">\n");
            sbLocal.append(xml);
            appendLogDump(resource, xml, sbLog);
        } catch (Exception e) {
            LOG.warn("Error dumping {}} to XML due to {}. This exception is ignored.", kind, e.getMessage(), e);
        }
    }

    protected void doDumpToDirectory(Resource resource, StringBuilder sbLocal, String kind, String ext) {
        if (directory != null && !sbLocal.isEmpty()) {
            // make sure directory exists
            File dir = new File(directory);
            dir.mkdirs();

            String name = resource != null ? resource.getLocation() : null;
            if (name == null) {
                name = "dump" + counter.incrementAndGet();
            }
            // strip scheme
            if (name.contains(":")) {
                name = StringHelper.after(name, ":");
            }
            name = FileUtil.onlyName(name) + "." + ext;
            File target = new File(directory, name);
            try {
                IOHelper.writeText(sbLocal.toString(), target);
                LOG.info("Dumped {} to file: {}", kind, target);
            } catch (IOException e) {
                throw new RuntimeException("Error dumping " + kind + " to file: " + target, e);
            }
        }
    }

    protected void appendLogDump(Resource resource, String dump, StringBuilder sbLog) {
        String loc = null;
        if (resource != null) {
            loc = extractLocationName(resource.getLocation());
        }
        if (loc != null) {
            sbLog.append(String.format("\nSource: %s%n%s%n%s%n", loc, DIVIDER, dump));
        } else {
            sbLog.append(String.format("%n%n%s%n", dump));
        }
    }

    private static final class DummyResource extends ResourceSupport {

        private DummyResource(String scheme, String location) {
            super(scheme, location);
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return null; // not in use
        }
    }

    private static String extractLocationName(String loc) {
        if (loc == null) {
            return null;
        }
        loc = stripSourceLocationLineNumber(loc);
        if (loc != null) {
            if (loc.contains(":")) {
                // strip prefix
                loc = loc.substring(loc.indexOf(':') + 1);
                // file based such as xml and yaml
                loc = FileUtil.stripPath(loc);
            }
        }
        return loc;
    }

}
