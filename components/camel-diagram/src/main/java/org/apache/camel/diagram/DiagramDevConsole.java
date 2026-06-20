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
     * Output format for the HTML rendering: html (default, interactive web component) or png (legacy inline image).
     * Only applies to image themes; ascii/unicode themes always render as text.
     */
    public static final String FORMAT = "format";

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

        try {
            RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(getCamelContext());
            if ("topology".equalsIgnoreCase(mode)) {
                sj.add(doCallTopologyText(dumper, theme, nodeWidth, metric, fontSize, refresh));
            } else if (isTextTheme(theme)) {
                String text = dumper.dumpRoutesAsAsciiArt(filter,
                        RouteDiagramDumper.NodeLabelMode.valueOf(nodeLabel.toUpperCase()),
                        nodeWidth, isUnicodeTheme(theme));
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
                sj.add(buildRouteWebComponentHtml(filter, refresh));
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

        JsonObject root = new JsonObject();
        try {
            RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(getCamelContext());
            if ("topology".equalsIgnoreCase(mode)) {
                return doCallTopologyJson(dumper, theme, nodeWidth, metric, fontSize);
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
            boolean metric, int fontSize, boolean refresh)
            throws Exception {
        if (isTextTheme(theme)) {
            return dumper.dumpTopologyAsAsciiArt(nodeWidth, isUnicodeTheme(theme));
        } else {
            BufferedImage image = dumper.dumpTopologyAsImage(
                    RouteDiagramDumper.Theme.valueOf(theme.toUpperCase()), metric, nodeWidth, fontSize);
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
        }
    }

    private Map<String, Object> doCallTopologyJson(
            RouteDiagramDumper dumper, String theme, int nodeWidth,
            boolean metric, int fontSize)
            throws Exception {
        JsonObject root = new JsonObject();
        if (isTextTheme(theme)) {
            String text = dumper.dumpTopologyAsAsciiArt(nodeWidth, isUnicodeTheme(theme));
            root.put("text", text);
        } else {
            BufferedImage image = dumper.dumpTopologyAsImage(
                    RouteDiagramDumper.Theme.valueOf(theme.toUpperCase()), metric, nodeWidth, fontSize);
            if (image != null) {
                String base64 = dumper.imageToBase64(image);
                root.put("image", base64);
            }
        }
        return root;
    }

    private static String buildRouteWebComponentHtml(String filter, boolean refresh) {
        String f = filter == null ? "*" : filter;
        String refreshAttr = refresh ? " refresh=\"5000\"" : "";
        // script path + route-structure src assume the dev console and static resources share an origin
        return "<html>\n"
               + "  <head>\n"
               + "    <script type=\"module\" src=\"/camel/diagram/camel-route-diagram.js\"></script>\n"
               + "  </head>\n"
               + "  <body>\n"
               + String.format("    <camel-route-diagram src=\"route-structure\" filter=\"%s\"%s></camel-route-diagram>%n",
                       escapeAttr(f), refreshAttr)
               + "  </body>\n"
               + "</html>\n";
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

}
