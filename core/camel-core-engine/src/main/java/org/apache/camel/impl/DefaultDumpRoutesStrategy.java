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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NamedNode;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.DumpRoutesStrategy;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.JdkService;
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
@JdkService("default-" + DumpRoutesStrategy.FACTORY)
public class DefaultDumpRoutesStrategy extends ServiceSupport implements DumpRoutesStrategy, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDumpRoutesStrategy.class);
    private static final String DIVIDER = "--------------------------------------------------------------------------------";

    private final AtomicInteger counter = new AtomicInteger();
    private CamelContext camelContext;

    private String include = "routes";
    private boolean resolvePlaceholders = true;
    private boolean uriAsParameters;
    private boolean generatedIds = true;
    private boolean log = true;
    private String output;
    private String outputFileName;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        // output can be a filename, dir, or both
        String name = FileUtil.stripPath(output);
        if (name != null && name.contains(".")) {
            outputFileName = name;
            output = FileUtil.onlyPath(output);
            if (output == null || output.isEmpty()) {
                output = ".";
            }
        }
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

    public boolean isGeneratedIds() {
        return generatedIds;
    }

    public void setGeneratedIds(boolean generatedIds) {
        this.generatedIds = generatedIds;
    }

    public boolean isLog() {
        return log;
    }

    public void setLog(boolean log) {
        this.log = log;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
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
        final Set<String> files = new HashSet<>();

        if (include.contains("*") || include.contains("all") || include.contains("beans")) {
            int size = model.getRegistryBeans().size();
            if (size > 0) {
                Map<Resource, List<RegistryBeanDefinition>> groups = new LinkedHashMap<>();
                for (RegistryBeanDefinition bean : model.getRegistryBeans()) {
                    Resource res = bean.getResource();
                    if (res == null) {
                        res = dummy;
                    }
                    List<RegistryBeanDefinition> beans = groups.computeIfAbsent(res, resource -> new ArrayList<>());
                    beans.add(bean);
                }
                StringBuilder sbLog = new StringBuilder();
                for (Map.Entry<Resource, List<RegistryBeanDefinition>> entry : groups.entrySet()) {
                    List<RegistryBeanDefinition> beans = entry.getValue();
                    Resource resource = entry.getKey();

                    StringBuilder sbLocal = new StringBuilder();
                    doDumpYamlBeans(camelContext, beans, resource == dummy ? null : resource, dumper, "beans", sbLocal, sbLog);
                    // dump each resource into its own file
                    doDumpToDirectory(resource, sbLocal, "beans", "yaml", files);
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} beans as YAML", size);
                    LOG.info("{}", sbLog);
                }
            }
        }

        if (include.contains("*") || include.contains("all") || include.contains("routes")) {
            int size = model.getRouteDefinitions().size();
            if (size > 0) {
                Map<Resource, RoutesDefinition> groups = new LinkedHashMap<>();
                for (RouteDefinition route : model.getRouteDefinitions()) {
                    if ((route.isRest() != null && route.isRest()) || (route.isTemplate() != null && route.isTemplate())) {
                        // skip routes that are rest/templates
                        continue;
                    }
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
                    doDumpToDirectory(resource, sbLocal, "routes", "yaml", files);
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} routes as YAML", size);
                    LOG.info("{}", sbLog);
                }
            }
        }

        if (include.contains("*") || include.contains("all") || include.contains("routeConfigurations")
                || include.contains("route-configurations")) {
            int size = model.getRouteConfigurationDefinitions().size();
            if (size > 0) {
                Map<Resource, RouteConfigurationsDefinition> groups = new LinkedHashMap<>();
                for (RouteConfigurationDefinition config : model.getRouteConfigurationDefinitions()) {
                    Resource res = config.getResource();
                    if (res == null) {
                        res = dummy;
                    }
                    RouteConfigurationsDefinition routes
                            = groups.computeIfAbsent(res, resource -> new RouteConfigurationsDefinition());
                    routes.getRouteConfigurations().add(config);
                }
                StringBuilder sbLog = new StringBuilder();
                for (Map.Entry<Resource, RouteConfigurationsDefinition> entry : groups.entrySet()) {
                    RouteConfigurationsDefinition def = entry.getValue();
                    Resource resource = entry.getKey();

                    StringBuilder sbLocal = new StringBuilder();
                    doDumpYaml(camelContext, def, resource == dummy ? null : resource, dumper, "route-configurations", sbLocal,
                            sbLog);
                    // dump each resource into its own file
                    doDumpToDirectory(resource, sbLocal, "route-configurations", "yaml", files);
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} route-configurations as YAML", size);
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
                    doDumpToDirectory(resource, sbLocal, "rests", "yaml", files);
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
                    doDumpToDirectory(resource, sbLocal, "route-templates", "yaml", files);
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
            String dump = dumper.dumpModelAsYaml(camelContext, def, resolvePlaceholders, uriAsParameters, generatedIds);
            sbLocal.append(dump);
            appendLogDump(resource, dump, sbLog);
        } catch (Exception e) {
            LOG.warn("Error dumping {}} to YAML due to {}. This exception is ignored.", kind, e.getMessage(), e);
        }
    }

    protected void doDumpYamlBeans(
            CamelContext camelContext, List beans, Resource resource,
            ModelToYAMLDumper dumper, String kind, StringBuilder sbLocal, StringBuilder sbLog) {
        try {
            String dump = dumper.dumpBeansAsYaml(camelContext, beans);
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
        final Set<String> files = new HashSet<>();

        if (include.contains("*") || include.contains("all") || include.contains("beans")) {
            int size = model.getRegistryBeans().size();
            if (size > 0) {
                Map<Resource, List<RegistryBeanDefinition>> groups = new LinkedHashMap<>();
                for (RegistryBeanDefinition bean : model.getRegistryBeans()) {
                    Resource res = bean.getResource();
                    if (res == null) {
                        res = dummy;
                    }
                    List<RegistryBeanDefinition> beans = groups.computeIfAbsent(res, resource -> new ArrayList<>());
                    beans.add(bean);
                }
                StringBuilder sbLog = new StringBuilder();
                for (Map.Entry<Resource, List<RegistryBeanDefinition>> entry : groups.entrySet()) {
                    List<RegistryBeanDefinition> beans = entry.getValue();
                    Resource resource = entry.getKey();

                    StringBuilder sbLocal = new StringBuilder();
                    doDumpXmlBeans(camelContext, beans, resource == dummy ? null : resource, dumper, "beans", sbLocal, sbLog);
                    // dump each resource into its own file
                    doDumpToDirectory(resource, sbLocal, "beans", "xml", files);
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} beans as XML", size);
                    LOG.info("{}", sbLog);
                }
            }
        }

        if (include.contains("*") || include.contains("all") || include.contains("routes")) {
            int size = model.getRouteDefinitions().size();
            if (size > 0) {
                Map<Resource, RoutesDefinition> groups = new LinkedHashMap<>();
                for (RouteDefinition route : model.getRouteDefinitions()) {
                    if ((route.isRest() != null && route.isRest()) || (route.isTemplate() != null && route.isTemplate())) {
                        // skip routes that are rest/templates
                        continue;
                    }
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
                    doDumpToDirectory(resource, sbLocal, "routes", "xml", files);
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} routes as XML", size);
                    LOG.info("{}", sbLog);
                }
            }
        }

        if (include.contains("*") || include.contains("all") || include.contains("routeConfigurations")
                || include.contains("route-configurations")) {
            int size = model.getRouteConfigurationDefinitions().size();
            if (size > 0) {
                Map<Resource, RouteConfigurationsDefinition> groups = new LinkedHashMap<>();
                for (RouteConfigurationDefinition config : model.getRouteConfigurationDefinitions()) {
                    Resource res = config.getResource();
                    if (res == null) {
                        res = dummy;
                    }
                    RouteConfigurationsDefinition routes
                            = groups.computeIfAbsent(res, resource -> new RouteConfigurationsDefinition());
                    routes.getRouteConfigurations().add(config);
                }
                StringBuilder sbLog = new StringBuilder();
                for (Map.Entry<Resource, RouteConfigurationsDefinition> entry : groups.entrySet()) {
                    RouteConfigurationsDefinition def = entry.getValue();
                    Resource resource = entry.getKey();

                    StringBuilder sbLocal = new StringBuilder();
                    doDumpXml(camelContext, def, resource == dummy ? null : resource, dumper, "routeConfiguration",
                            "route-configurations",
                            sbLocal, sbLog);
                    // dump each resource into its own file
                    doDumpToDirectory(resource, sbLocal, "route-configurations", "xml", files);
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} route-configurations as XML", size);
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
                    doDumpToDirectory(resource, sbLocal, "rests", "xml", files);
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
                    doDumpToDirectory(resource, sbLocal, "route-templates", "xml", files);
                }
                if (!sbLog.isEmpty() && log) {
                    LOG.info("Dumping {} route-templates as XML", size);
                    LOG.info("{}", sbLog);
                }
            }
        }

        if (output != null && !files.isEmpty()) {
            // all XML files need to have <camel> as root tag
            doAdjustXmlFiles(files);
        }
    }

    protected void doDumpXmlBeans(
            CamelContext camelContext, List beans, Resource resource,
            ModelToXMLDumper dumper, String kind, StringBuilder sbLocal, StringBuilder sbLog) {
        try {
            String dump = dumper.dumpBeansAsXml(camelContext, beans);
            sbLocal.append(dump);
            appendLogDump(resource, dump, sbLog);
        } catch (Exception e) {
            LOG.warn("Error dumping {}} to XML due to {}. This exception is ignored.", kind, e.getMessage(), e);
        }
    }

    protected void doDumpXml(
            CamelContext camelContext, NamedNode def, Resource resource,
            ModelToXMLDumper dumper, String replace, String kind, StringBuilder sbLocal, StringBuilder sbLog) {
        try {
            String xml = dumper.dumpModelAsXml(camelContext, def, resolvePlaceholders, generatedIds);
            // remove spring schema xmlns that camel-jaxb dumper includes
            xml = StringHelper.replaceFirst(xml, " xmlns=\"http://camel.apache.org/schema/spring\">", ">");
            xml = xml.replace("</" + replace + ">", "</" + replace + ">\n");
            // remove outer tag (routes, rests, etc)
            replace = replace + "s";
            xml = StringHelper.replaceFirst(xml, "<" + replace + ">", "");
            xml = StringHelper.replaceFirst(xml, "</" + replace + ">", "");

            sbLocal.append(xml);
            appendLogDump(resource, xml, sbLog);
        } catch (Exception e) {
            LOG.warn("Error dumping {}} to XML due to {}. This exception is ignored.", kind, e.getMessage(), e);
        }
    }

    protected void doDumpToDirectory(Resource resource, StringBuilder sbLocal, String kind, String ext, Set<String> files) {
        if (output != null && !sbLocal.isEmpty()) {
            // make sure directory exists
            File dir = new File(output);
            dir.mkdirs();

            String name = resolveFileName(ext, resource);
            boolean newFile = files.isEmpty() || !files.contains(name);
            File target = new File(output, name);
            try {
                if (newFile) {
                    // write as new file (override old file if exists)
                    IOHelper.writeText(sbLocal.toString(), target);
                } else {
                    // append to existing file
                    IOHelper.appendText(sbLocal.toString(), target);
                }
                files.add(name);
                LOG.info("Dumped {} to file: {}", kind, target);
            } catch (IOException e) {
                throw new RuntimeException("Error dumping " + kind + " to file: " + target, e);
            }
        }
    }

    protected void doAdjustXmlFiles(Set<String> files) {
        for (String name : files) {
            if (name.endsWith(".xml")) {
                try {
                    File file = new File(output, name);
                    // wrap xml files with <camel> root tag
                    StringBuilder sb = new StringBuilder();
                    sb.append("<camel>\n\n");
                    String xml = IOHelper.loadText(new FileInputStream(file));
                    sb.append(xml);
                    sb.append("\n</camel>\n");
                    IOHelper.writeText(sb.toString(), file);
                } catch (Exception e) {
                    LOG.warn("Error adjusting dumped XML file: {} due to {}. This exception is ignored.", name, e.getMessage(),
                            e);
                }
            }
        }
    }

    protected void appendLogDump(Resource resource, String dump, StringBuilder sbLog) {
        String loc = null;
        if (resource != null) {
            loc = extractLocationName(resource.getLocation());
        }
        if (loc != null) {
            sbLog.append(String.format("%nSource: %s%n%s%n%s%n", loc, DIVIDER, dump));
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
                loc = StringHelper.after(loc, ":", loc);

                // file based such as xml and yaml
                loc = FileUtil.stripPath(loc);
            }
        }
        return loc;
    }

    protected String resolveFileName(String ext, Resource resource) {
        if (outputFileName != null) {
            return outputFileName;
        }

        // compute name from resource or auto-generated
        String name = resource != null ? resource.getLocation() : null;
        if (name == null) {
            name = "dump" + counter.incrementAndGet();
        }
        // strip scheme
        if (name.contains(":")) {
            name = StringHelper.after(name, ":");
        }
        return FileUtil.onlyName(name) + "." + ext;
    }

}
