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

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

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
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.LoggerHelper.stripSourceLocationLineNumber;

/**
 * Default {@link DumpRoutesStrategy} that dumps the routes to standard logger.
 */
@ServiceFactory("default-" + DumpRoutesStrategy.FACTORY)
public class DefaultDumpRoutesStrategy implements DumpRoutesStrategy, CamelContextAware {

    // TODO: save to disk
    // TODO: dumpRoutes=yaml?log=false&directory=mydir

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDumpRoutesStrategy.class);
    private static final String DIVIDER = "--------------------------------------------------------------------------------";

    private CamelContext camelContext;

    private String include = "routes";
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

    /**
     * Controls what to include in output.
     *
     * Possible values: routes, rests, routeTemplates. Multiple values can be separated by comma. Default is routes.
     */
    public void setInclude(String include) {
        this.include = include;
    }

    public boolean isLog() {
        return log;
    }

    /**
     * Whether to log route dumps to Logger
     */
    public void setLog(boolean log) {
        this.log = log;
    }

    public String getDirectory() {
        return directory;
    }

    /**
     * Whether to save route dumps to files in the given directory. The name of the files are based on ids (route ids,
     * etc.). Existing files will be overwritten.
     */
    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public boolean isUriAsParameters() {
        return uriAsParameters;
    }

    /**
     * When dumping to YAML format, then this option controls whether endpoint URIs should be expanded into a key/value
     * parameters.
     */
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

        if (include.contains("routes")) {
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
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Resource, RoutesDefinition> entry : groups.entrySet()) {
                    RoutesDefinition def = entry.getValue();
                    Resource resource = entry.getKey();
                    doDumpYaml(camelContext, def, resource == dummy ? null : resource, dumper, "routes", sb);
                }
                if (!sb.isEmpty() && log) {
                    LOG.info("Dumping {} routes as YAML", size);
                    LOG.info("{}", sb);
                }
            }
        }

        if (include.contains("rests")) {
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
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Resource, RestsDefinition> entry : groups.entrySet()) {
                    RestsDefinition def = entry.getValue();
                    Resource resource = entry.getKey();
                    doDumpYaml(camelContext, def, resource == dummy ? null : resource, dumper, "rests", sb);
                }
                if (!sb.isEmpty() && log) {
                    LOG.info("Dumping {} rests as YAML", size);
                    LOG.info("{}", sb);
                }
            }
        }

        if (include.contains("routeTemplates") || include.contains("route-templates")) {
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
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Resource, RouteTemplatesDefinition> entry : groups.entrySet()) {
                    RouteTemplatesDefinition def = entry.getValue();
                    Resource resource = entry.getKey();
                    doDumpYaml(camelContext, def, resource == dummy ? null : resource, dumper, "route-templates", sb);
                }
                if (!sb.isEmpty() && log) {
                    LOG.info("Dumping {} route-templates as YAML", size);
                    LOG.info("{}", sb);
                }
            }
        }
    }

    protected void doDumpYaml(
            CamelContext camelContext, NamedNode def, Resource resource,
            ModelToYAMLDumper dumper, String kind, StringBuilder sb) {
        try {
            String dump = dumper.dumpModelAsYaml(camelContext, def, true, uriAsParameters);
            appendDump(resource, dump, sb);
        } catch (Exception e) {
            LOG.warn("Error dumping {}} to YAML due to {}. This exception is ignored.", kind, e.getMessage(), e);
        }
    }

    protected void doDumpRoutesAsXml(CamelContext camelContext) {
        final ModelToXMLDumper dumper = PluginHelper.getModelToXMLDumper(camelContext);
        final Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        final DummyResource dummy = new DummyResource(null, null);

        if (include.contains("routes")) {
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
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Resource, RoutesDefinition> entry : groups.entrySet()) {
                    RoutesDefinition def = entry.getValue();
                    Resource resource = entry.getKey();
                    doDumpXml(camelContext, def, resource == dummy ? null : resource, dumper, "route", "routes", sb);
                }
                if (!sb.isEmpty() && log) {
                    LOG.info("Dumping {} routes as XML", size);
                    LOG.info("{}", sb);
                }
            }
        }

        if (include.contains("rests")) {
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
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Resource, RestsDefinition> entry : groups.entrySet()) {
                    RestsDefinition def = entry.getValue();
                    Resource resource = entry.getKey();
                    doDumpXml(camelContext, def, resource == dummy ? null : resource, dumper, "rest", "rests", sb);
                }
                if (!sb.isEmpty() && log) {
                    LOG.info("Dumping {} rests as XML", size);
                    LOG.info("{}", sb);
                }
            }
        }

        if (include.contains("routeTemplates") || include.contains("route-templates")) {
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
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Resource, RouteTemplatesDefinition> entry : groups.entrySet()) {
                    RouteTemplatesDefinition def = entry.getValue();
                    Resource resource = entry.getKey();
                    doDumpXml(camelContext, def, resource == dummy ? null : resource, dumper, "routeTemplate",
                            "route-templates", sb);
                }
                if (!sb.isEmpty() && log) {
                    LOG.info("Dumping {} route-templates as XML", size);
                    LOG.info("{}", sb);
                }
            }
        }
    }

    protected void doDumpXml(
            CamelContext camelContext, NamedNode def, Resource resource,
            ModelToXMLDumper dumper, String replace, String kind, StringBuilder sb) {
        try {
            String xml = dumper.dumpModelAsXml(camelContext, def, true);
            // lets separate with empty line
            xml = StringHelper.replaceFirst(xml, "xmlns=\"http://camel.apache.org/schema/spring\">",
                    "xmlns=\"http://camel.apache.org/schema/spring\">\n");
            xml = xml.replace("</" + replace + ">", "</" + replace + ">\n");
            appendDump(resource, xml, sb);
        } catch (Exception e) {
            LOG.warn("Error dumping {}} to XML due to {}. This exception is ignored.", kind, e.getMessage(), e);
        }
    }

    protected void appendDump(Resource resource, String dump, StringBuilder sb) {
        String loc = null;
        if (resource != null) {
            loc = extractLocationName(resource.getLocation());
        }
        if (loc != null) {
            sb.append(String.format("\nSource: %s%n%s%n%s%n", loc, DIVIDER, dump));
        } else {
            sb.append(String.format("%n%n%s%n", dump));
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
