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
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.camel.support.LoggerHelper.stripSourceLocationLineNumber;

/**
 * Default {@link DumpRoutesStrategy} that dumps the routes to standard logger.
 */
@JdkService("default-" + DumpRoutesStrategy.FACTORY)
public class DefaultDumpRoutesStrategy implements DumpRoutesStrategy, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDumpRoutesStrategy.class);
    private static final String DIVIDER = "--------------------------------------------------------------------------------";

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void dumpRoutes(String format) {
        if ("yaml".equalsIgnoreCase(format)) {
            doDumpRoutesAsYaml(camelContext);
        } else if ("xml".equalsIgnoreCase(format)) {
            doDumpRoutesAsXml(camelContext);
        }
    }

    protected void onDump(Resource resource, String dump) {
        String loc = null;
        if (resource != null) {
            loc = extractLocationName(resource.getLocation());
        }
        if (loc != null) {
            LOG.info("\nSource: {}\n{}\n{}\n", loc, DIVIDER, dump);
        } else {
            LOG.info("\n\n{}\n", dump);
        }
    }

    protected void doDumpRoutesAsYaml(CamelContext camelContext) {
        final ModelToYAMLDumper dumper = PluginHelper.getModelToYAMLDumper(camelContext);
        final Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        final DummyResource dummy = new DummyResource(null, null);

        int size = model.getRouteDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} routes as YAML", size);
            Map<Resource, RoutesDefinition> groups = new LinkedHashMap<>();
            for (RouteDefinition route : model.getRouteDefinitions()) {
                Resource res = route.getResource();
                if (res == null) {
                    res = dummy;
                }
                RoutesDefinition routes = groups.computeIfAbsent(res, resource -> new RoutesDefinition());
                routes.getRoutes().add(route);
            }
            for (Map.Entry<Resource, RoutesDefinition> entry : groups.entrySet()) {
                RoutesDefinition def = entry.getValue();
                Resource resource = entry.getKey();
                doDumpYaml(camelContext, def, resource == dummy ? null : resource, dumper, "routes");
            }
        }

        size = model.getRestDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} rests as YAML", size);
            Map<Resource, RestsDefinition> groups = new LinkedHashMap<>();
            for (RestDefinition rest : model.getRestDefinitions()) {
                Resource res = rest.getResource();
                if (res == null) {
                    res = dummy;
                }
                RestsDefinition rests = groups.computeIfAbsent(res, resource -> new RestsDefinition());
                rests.getRests().add(rest);
            }
            for (Map.Entry<Resource, RestsDefinition> entry : groups.entrySet()) {
                RestsDefinition def = entry.getValue();
                Resource resource = entry.getKey();
                doDumpYaml(camelContext, def, resource == dummy ? null : resource, dumper, "rests");
            }
        }

        size = model.getRouteTemplateDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} route templates as YAML", size);
            Map<Resource, RouteTemplatesDefinition> groups = new LinkedHashMap<>();
            for (RouteTemplateDefinition rt : model.getRouteTemplateDefinitions()) {
                Resource res = rt.getResource();
                if (res == null) {
                    res = dummy;
                }
                RouteTemplatesDefinition rests = groups.computeIfAbsent(res, resource -> new RouteTemplatesDefinition());
                rests.getRouteTemplates().add(rt);
            }
            for (Map.Entry<Resource, RouteTemplatesDefinition> entry : groups.entrySet()) {
                RouteTemplatesDefinition def = entry.getValue();
                Resource resource = entry.getKey();
                doDumpYaml(camelContext, def, resource == dummy ? null : resource, dumper, "route-templates");
            }
        }
    }

    protected void doDumpYaml(CamelContext camelContext, NamedNode def, Resource resource,
                              ModelToYAMLDumper dumper, String kind) {
        try {
            String dump = dumper.dumpModelAsYaml(camelContext, def, true, false);
            onDump(resource, dump);
        } catch (Exception e) {
            LOG.warn("Error dumping {}} to YAML due to {}. This exception is ignored.", kind, e.getMessage(), e);
        }
    }

    protected void doDumpRoutesAsXml(CamelContext camelContext) {
        final ModelToXMLDumper dumper = PluginHelper.getModelToXMLDumper(camelContext);
        final Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        final DummyResource dummy = new DummyResource(null, null);

        int size = model.getRouteDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} routes as XML", size);
            Map<Resource, RoutesDefinition> groups = new LinkedHashMap<>();
            for (RouteDefinition route : model.getRouteDefinitions()) {
                Resource res = route.getResource();
                if (res == null) {
                    res = dummy;
                }
                RoutesDefinition routes = groups.computeIfAbsent(res, resource -> new RoutesDefinition());
                routes.getRoutes().add(route);
            }
            for (Map.Entry<Resource, RoutesDefinition> entry : groups.entrySet()) {
                RoutesDefinition def = entry.getValue();
                Resource resource = entry.getKey();
                doDumpXml(camelContext, def, resource == dummy ? null : resource, dumper, "route", "routes");
            }
        }

        size = model.getRestDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} rests as XML", size);
            Map<Resource, RestsDefinition> groups = new LinkedHashMap<>();
            for (RestDefinition rest : model.getRestDefinitions()) {
                Resource res = rest.getResource();
                if (res == null) {
                    res = dummy;
                }
                RestsDefinition routes = groups.computeIfAbsent(res, resource -> new RestsDefinition());
                routes.getRests().add(rest);
            }
            for (Map.Entry<Resource, RestsDefinition> entry : groups.entrySet()) {
                RestsDefinition def = entry.getValue();
                Resource resource = entry.getKey();
                doDumpXml(camelContext, def, resource == dummy ? null : resource, dumper, "rest", "rests");
            }
        }

        size = model.getRouteTemplateDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} route templates as XML", size);
            Map<Resource, RouteTemplatesDefinition> groups = new LinkedHashMap<>();
            for (RouteTemplateDefinition rt : model.getRouteTemplateDefinitions()) {
                Resource res = rt.getResource();
                if (res == null) {
                    res = dummy;
                }
                RouteTemplatesDefinition routes = groups.computeIfAbsent(res, resource -> new RouteTemplatesDefinition());
                routes.getRouteTemplates().add(rt);
            }
            for (Map.Entry<Resource, RouteTemplatesDefinition> entry : groups.entrySet()) {
                RouteTemplatesDefinition def = entry.getValue();
                Resource resource = entry.getKey();
                doDumpXml(camelContext, def, resource == dummy ? null : resource, dumper, "routeTemplate", "route-templates");
            }
        }
    }

    protected void doDumpXml(CamelContext camelContext, NamedNode def, Resource resource,
                              ModelToXMLDumper dumper, String replace, String kind) {
        try {
            String xml = dumper.dumpModelAsXml(camelContext, def, true);
            // lets separate with empty line
            xml = StringHelper.replaceFirst(xml, "xmlns=\"http://camel.apache.org/schema/spring\">",
                    "xmlns=\"http://camel.apache.org/schema/spring\">\n");
            xml = xml.replace("</" + replace + ">", "</" + replace + ">\n");
            onDump(resource, xml);
        } catch (Exception e) {
            LOG.warn("Error dumping {}} to XML due to {}. This exception is ignored.", kind, e.getMessage(), e);
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
