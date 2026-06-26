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
package org.apache.camel.diagram;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

import org.apache.camel.spi.RouteDiagramDumper;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "route-diagram", group = "camel", displayName = "Route Diagram", description = "Visual route diagrams")
public class DiagramDevConsole extends AbstractDevConsole {

    /**
     * Filters the routes matching by route id, route uri, and source location
     */
    public static final String FILTER = "filter";

    /**
     * Theme to use: dark, light, transparent, ascii, or unicode
     */
    public static final String THEME = "theme";

    /**
     * The size of the font (default 12)
     */
    public static final String FONT_SIZE = "fontSize";

    /**
     * The node width (default 180 pixels)
     */
    public static final String NODE_WIDTH = "nodeWidth";

    /**
     * Node label mode (code, description, both). Is default code.
     */
    public static final String NODE_LABEL = "nodeLabel";

    /**
     * Whether to include live metric counters. Is default true.
     */
    public static final String METRIC = "metric";

    /**
     * Whether to auto-refresh page every 5 seconds
     */
    public static final String AUTO_REFRESH = "autoRefresh";

    /**
     * Diagram mode: route (default) or topology
     */
    public static final String MODE = "mode";

    /**
     * Output format: html (default, interactive web component), png (inline image), text or ascii (ASCII art), unicode
     * (box-drawing characters).
     */
    public static final String FORMAT = "format";

    /**
     * Whether to include external systems (kafka, http, etc.) in topology mode. Is default true.
     */
    public static final String EXTERNAL = "external";

    public DiagramDevConsole() {
        super("camel", "route-diagram", "Route Diagram", "Visual route diagrams");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        final StringJoiner sj = new StringJoiner("\n");

        String mode = (String) options.getOrDefault(MODE, "route");
        String filter = (String) options.getOrDefault(FILTER, "*");
        String theme = (String) options.getOrDefault(THEME, RouteDiagramDumper.Theme.TRANSPARENT.name());
        int fontSize
                = Integer.parseInt(options.getOrDefault(FONT_SIZE, "" + RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE).toString());
        int nodeWidth = Integer
                .parseInt(options.getOrDefault(NODE_WIDTH, "" + RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH).toString());
        String nodeLabel = (String) options.getOrDefault(NODE_LABEL, RouteDiagramDumper.NodeLabelMode.CODE.name());
        boolean metric = "true".equalsIgnoreCase((String) options.getOrDefault(METRIC, "true"));
        boolean refresh = "true".equalsIgnoreCase((String) options.getOrDefault(AUTO_REFRESH, "true"));
        String format = (String) options.getOrDefault(FORMAT, "html");

        boolean external = "true".equalsIgnoreCase((String) options.getOrDefault(EXTERNAL, "true"));

        try {
            RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(getCamelContext());
            boolean textFormat = isTextFormat(format);
            if ("topology".equalsIgnoreCase(mode)) {
                sj.add(doCallTopologyText(dumper, theme, nodeWidth, metric, fontSize, refresh, format, external));
            } else if (isTextTheme(theme) || textFormat) {
                boolean unicode = isUnicodeTheme(theme) || "unicode".equalsIgnoreCase(format);
                String text = dumper.dumpRoutesAsAsciiArt(filter,
                        RouteDiagramDumper.NodeLabelMode.valueOf(nodeLabel.toUpperCase()),
                        nodeWidth, unicode);
                sj.add(text);
            } else if ("png".equalsIgnoreCase(format)) {
                BufferedImage image = dumper.dumpRoutesAsImage(filter,
                        RouteDiagramDumper.Theme.valueOf(theme.toUpperCase()),
                        metric, RouteDiagramDumper.NodeLabelMode.valueOf(nodeLabel.toUpperCase()), nodeWidth, fontSize);
                String base64 = dumper.imageToBase64(image);
                String html = String.format(
                        "  <body>\n    <img src=\"data:image/png;base64,%s\" alt=\"Route Diagram\">\n  </body>\n",
                        base64);
                if (refresh) {
                    html = "<head><meta http-equiv=\"refresh\" content=\"5\"></head>\n" + html;
                }
                html = "<html>\n" + html + "</html>\n";
                sj.add(html);
            } else {
                sj.add(buildRouteWebComponentHtml(filter, metric, refresh));
            }
        } catch (Exception e) {
            // ignore
        }

        return sj.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String mode = (String) options.getOrDefault(MODE, "route");
        String filter = (String) options.getOrDefault(FILTER, "*");
        String theme = (String) options.getOrDefault(THEME, RouteDiagramDumper.Theme.TRANSPARENT.name());
        int fontSize
                = Integer.parseInt(options.getOrDefault(FONT_SIZE, "" + RouteDiagramLayoutEngine.DEFAULT_FONT_SIZE).toString());
        int nodeWidth = Integer
                .parseInt(options.getOrDefault(NODE_WIDTH, "" + RouteDiagramLayoutEngine.DEFAULT_BOX_WIDTH).toString());
        String nodeLabel = (String) options.getOrDefault(NODE_LABEL, RouteDiagramDumper.NodeLabelMode.CODE.name());
        boolean metric = "true".equalsIgnoreCase((String) options.getOrDefault(METRIC, "true"));
        boolean external = "true".equalsIgnoreCase((String) options.getOrDefault(EXTERNAL, "true"));

        JsonObject root = new JsonObject();
        try {
            RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(getCamelContext());
            if ("topology".equalsIgnoreCase(mode)) {
                return doCallTopologyJson(dumper, theme, nodeWidth, metric, fontSize, external);
            } else if (isTextTheme(theme)) {
                String text = dumper.dumpRoutesAsAsciiArt(filter,
                        RouteDiagramDumper.NodeLabelMode.valueOf(nodeLabel.toUpperCase()),
                        nodeWidth, isUnicodeTheme(theme));
                root.put("text", text);
            } else {
                BufferedImage image = dumper.dumpRoutesAsImage(filter,
                        RouteDiagramDumper.Theme.valueOf(theme.toUpperCase()),
                        metric, RouteDiagramDumper.NodeLabelMode.valueOf(nodeLabel.toUpperCase()), nodeWidth, fontSize);
                String base64 = dumper.imageToBase64(image);
                root.put("image", base64);
            }
        } catch (Exception e) {
            // ignore
        }

        return root;
    }

    private String doCallTopologyText(
            RouteDiagramDumper dumper, String theme, int nodeWidth,
            boolean metric, int fontSize, boolean refresh, String format, boolean external)
            throws Exception {
        boolean textFormat = isTextFormat(format);
        if (isTextTheme(theme) || textFormat) {
            boolean unicode = isUnicodeTheme(theme) || "unicode".equalsIgnoreCase(format);
            return dumper.dumpTopologyAsAsciiArt(nodeWidth, unicode, external);
        } else if ("png".equalsIgnoreCase(format)) {
            BufferedImage image = dumper.dumpTopologyAsImage(
                    RouteDiagramDumper.Theme.valueOf(theme.toUpperCase()), metric, nodeWidth, fontSize, external);
            if (image == null) {
                return "";
            }
            String base64 = dumper.imageToBase64(image);
            String html = String.format(
                    "  <body>\n    <img src=\"data:image/png;base64,%s\" alt=\"Route Topology\">\n  </body>\n",
                    base64);
            if (refresh) {
                html = "<head><meta http-equiv=\"refresh\" content=\"5\"></head>\n" + html;
            }
            return "<html>\n" + html + "</html>\n";
        } else {
            return buildTopologyWebComponentHtml(metric, refresh, external);
        }
    }

    private Map<String, Object> doCallTopologyJson(
            RouteDiagramDumper dumper, String theme, int nodeWidth,
            boolean metric, int fontSize, boolean external)
            throws Exception {
        JsonObject root = new JsonObject();
        if (isTextTheme(theme)) {
            String text = dumper.dumpTopologyAsAsciiArt(nodeWidth, isUnicodeTheme(theme), external);
            root.put("text", text);
        } else {
            BufferedImage image = dumper.dumpTopologyAsImage(
                    RouteDiagramDumper.Theme.valueOf(theme.toUpperCase()), metric, nodeWidth, fontSize, external);
            if (image != null) {
                String base64 = dumper.imageToBase64(image);
                root.put("image", base64);
            }
        }
        return root;
    }

    private static final String WEB_COMPONENT_JS = loadWebComponentJs("camel-route-diagram.js");
    private static final String TOPOLOGY_WEB_COMPONENT_JS = loadWebComponentJs("camel-topology-diagram.js");

    private static String buildTopologyWebComponentHtml(boolean metric, boolean refresh, boolean external) {
        String metricAttr = metric ? "" : " metric=\"false\"";
        String refreshAttr = refresh ? " refresh=\"5000\"" : "";
        String externalAttr = external ? "" : " external=\"false\"";
        return "<html>\n"
               + "  <head>\n"
               + "    <meta charset=\"utf-8\">\n"
               + "    <script type=\"module\">\n" + TOPOLOGY_WEB_COMPONENT_JS + "\n    </script>\n"
               + "  </head>\n"
               + "  <body>\n"
               + String.format(
                       "    <camel-topology-diagram src=\"route-topology\"%s%s%s></camel-topology-diagram>%n",
                       metricAttr, refreshAttr, externalAttr)
               + "  </body>\n"
               + "</html>\n";
    }

    private static String buildRouteWebComponentHtml(String filter, boolean metric, boolean refresh) {
        String f = filter == null ? "*" : filter;
        String metricAttr = metric ? "" : " metric=\"false\"";
        String refreshAttr = refresh ? " refresh=\"5000\"" : "";
        // inline the web component script: static resource serving is not available when only the developer
        // console is enabled (camel run --console). route-structure is a sibling console on the same origin.
        return "<html>\n"
               + "  <head>\n"
               + "    <meta charset=\"utf-8\">\n"
               + "    <script type=\"module\">\n" + WEB_COMPONENT_JS + "\n    </script>\n"
               + "  </head>\n"
               + "  <body>\n"
               + String.format(
                       "    <camel-route-diagram src=\"route-structure\" filter=\"%s\"%s%s></camel-route-diagram>%n",
                       escapeAttr(f), metricAttr, refreshAttr)
               + "  </body>\n"
               + "</html>\n";
    }

    private static String loadWebComponentJs(String filename) {
        try (InputStream is = DiagramDevConsole.class.getResourceAsStream(
                "/META-INF/resources/camel/diagram/" + filename)) {
            return is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static String escapeAttr(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static boolean isTextTheme(String theme) {
        return "ascii".equalsIgnoreCase(theme) || "unicode".equalsIgnoreCase(theme);
    }

    private static boolean isUnicodeTheme(String theme) {
        return "unicode".equalsIgnoreCase(theme);
    }

    private static boolean isTextFormat(String format) {
        return "text".equalsIgnoreCase(format) || "ascii".equalsIgnoreCase(format) || "unicode".equalsIgnoreCase(format);
    }

}
