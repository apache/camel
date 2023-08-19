package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.DumpRoutesStrategy;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link DumpRoutesStrategy} that dumps the routes to standard logger.
 */
@JdkService("default-" + DumpRoutesStrategy.FACTORY)
public class DefaultDumpRoutesStrategy implements DumpRoutesStrategy, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDumpRoutesStrategy.class);

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

    protected void onDump(String dump) {
        LOG.info("\n\n{}\n", dump);
    }

    protected void doDumpRoutesAsYaml(CamelContext camelContext) {
        final ModelToYAMLDumper dumper = PluginHelper.getModelToYAMLDumper(camelContext);
        final Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);

        int size = model.getRouteDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} routes as YAML", size);
            RoutesDefinition def = new RoutesDefinition();
            def.setRoutes(model.getRouteDefinitions());
            try {
                String dump = dumper.dumpModelAsYaml(camelContext, def, true, false);
                onDump(dump);
            } catch (Exception e) {
                LOG.warn("Error dumping routes to YAML due to {}. This exception is ignored.", e.getMessage(), e);
            }
        }

        size = model.getRestDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} rests as YAML", size);
            RestsDefinition def = new RestsDefinition();
            def.setRests(model.getRestDefinitions());
            try {
                String dump = dumper.dumpModelAsYaml(camelContext, def, true, false);
                onDump(dump);
            } catch (Exception e) {
                LOG.warn("Error dumping rests to YAML due to {}. This exception is ignored.", e.getMessage(), e);
            }
        }

        size = model.getRouteTemplateDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} route templates as YAML", size);
            RouteTemplatesDefinition def = new RouteTemplatesDefinition();
            def.setRouteTemplates(model.getRouteTemplateDefinitions());
            try {
                String dump = dumper.dumpModelAsYaml(camelContext, def, true, false);
                onDump(dump);
            } catch (Exception e) {
                LOG.warn("Error dumping route-templates to YAML due to {}. This exception is ignored.", e.getMessage(), e);
            }
        }
    }

    protected void doDumpRoutesAsXml(CamelContext camelContext) {
        final ModelToXMLDumper dumper = PluginHelper.getModelToXMLDumper(camelContext);
        final Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);

        int size = model.getRouteDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} routes as XML", size);
            // for XML to output nicely all routes in one XML then lets put them into <routes>
            RoutesDefinition def = new RoutesDefinition();
            def.setRoutes(model.getRouteDefinitions());
            try {
                String xml = dumper.dumpModelAsXml(camelContext, def, true);
                // lets separate routes with empty line
                xml = StringHelper.replaceFirst(xml, "xmlns=\"http://camel.apache.org/schema/spring\">",
                        "xmlns=\"http://camel.apache.org/schema/spring\">\n");
                xml = xml.replace("</route>", "</route>\n");
                onDump(xml);
            } catch (Exception e) {
                LOG.warn("Error dumping routes to XML due to {}. This exception is ignored.", e.getMessage(), e);
            }
        }

        size = model.getRestDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} rests as XML", size);
            // for XML to output nicely all routes in one XML then lets put them into <routes>
            RestsDefinition def = new RestsDefinition();
            def.setRests(model.getRestDefinitions());
            try {
                String xml = dumper.dumpModelAsXml(camelContext, def, true);
                // lets separate rests with empty line
                xml = StringHelper.replaceFirst(xml, "xmlns=\"http://camel.apache.org/schema/spring\">",
                        "xmlns=\"http://camel.apache.org/schema/spring\">\n");
                xml = xml.replace("</rest>", "</rest>\n");
                onDump(xml);
            } catch (Exception e) {
                LOG.warn("Error dumping rests to XML due to {}. This exception is ignored.", e.getMessage(), e);
            }
        }

        size = model.getRouteTemplateDefinitions().size();
        if (size > 0) {
            LOG.info("Dumping {} route templates as XML", size);
            // for XML to output nicely all routes in one XML then lets put them into <routes>
            RouteTemplatesDefinition def = new RouteTemplatesDefinition();
            def.setRouteTemplates(model.getRouteTemplateDefinitions());
            try {
                String xml = dumper.dumpModelAsXml(camelContext, def, true);
                // lets separate rests with empty line
                xml = StringHelper.replaceFirst(xml, "xmlns=\"http://camel.apache.org/schema/spring\">",
                        "xmlns=\"http://camel.apache.org/schema/spring\">\n");
                xml = xml.replace("</routeTemplate>", "</routeTemplate>\n");
                onDump(xml);
            } catch (Exception e) {
                LOG.warn("Error dumping route-templates to XML due to {}. This exception is ignored.", e.getMessage(), e);
            }
        }
    }

}
