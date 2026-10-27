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

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The icons of the steps in the card style of a route diagram: Lucide icons (https://lucide.dev, ISC License; the same
 * icons as the camel-route-diagram web component), as outlines on a 24x24 grid to stroke with round caps and joins.
 */
final class RouteDiagramIcons {

    /** The size of the grid the icons are drawn on. */
    static final int GRID = 24;

    private static final Map<String, Shape> ICONS = new HashMap<>();

    static {
        ICONS.put("workflow", shape(rect(3, 3, 8, 8, 2), path("M7 11v4a2 2 0 0 0 2 2h4"), rect(13, 13, 8, 8, 2)));
        ICONS.put("log-in", shape(path("M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"),
                polyline(10, 17, 15, 12, 10, 7), line(15, 12, 3, 12)));
        ICONS.put("log-out", shape(path("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"),
                polyline(16, 17, 21, 12, 16, 7), line(21, 12, 9, 12)));
        ICONS.put("file-text", shape(path("M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"),
                path("M14 2v6h6"), path("M16 13H8"), path("M16 17H8"), path("M10 9H8")));
        ICONS.put("git-branch", shape(line(6, 3, 6, 15), circle(18, 6, 3), circle(6, 18, 3),
                path("M18 9a9 9 0 0 1-9 9")));
        ICONS.put("corner-down-right", shape(polyline(15, 10, 20, 15, 15, 20), path("M4 4v7a4 4 0 0 0 4 4h12")));
        ICONS.put("split", shape(path("M16 3h5v5"), path("M8 3H3v5"),
                path("M12 22v-8.3a4 4 0 0 0-1.172-2.872L3 3"), path("m15 9 6-6")));
        ICONS.put("merge", shape(path("m8 6 4-4 4 4"), path("M12 2v10.3a4 4 0 0 1-1.172 2.872L4 22"),
                path("m20 22-5-5")));
        ICONS.put("shield", shape(path("M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1"
                                       + "c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z")));
        ICONS.put("alert-triangle", shape(path("m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"),
                path("M12 9v4"), path("M12 17h.01")));
        ICONS.put("flag", shape(path("M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"), line(4, 22, 4, 15)));
        ICONS.put("zap", shape(path("M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7"
                                    + "a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z")));
        ICONS.put("braces", shape(path("M8 3H7a2 2 0 0 0-2 2v5a2 2 0 0 1-2 2 2 2 0 0 1 2 2v5c0 1.1.9 2 2 2h1"),
                path("M16 21h1a2 2 0 0 0 2-2v-5c0-1.1.9-2 2-2a2 2 0 0 1-2-2V5a2 2 0 0 0-2-2h-1")));
        ICONS.put("heading", shape(path("M6 12h12"), path("M6 20V4"), path("M18 20V4")));
        ICONS.put("x", shape(path("M18 6 6 18"), path("m6 6 12 12")));
        ICONS.put("repeat", shape(path("m17 2 4 4-4 4"), path("M3 11v-1a4 4 0 0 1 4-4h14"), path("m7 22-4-4 4-4"),
                path("M21 13v1a4 4 0 0 1-4 4H3")));
        ICONS.put("code", shape(path("m16 18 6-6-6-6"), path("m8 6-6 6 6 6")));
        ICONS.put("clock", shape(circle(12, 12, 10), polyline(12, 6, 12, 12, 16, 14)));
        ICONS.put("filter", shape(path("M22 3H2l8 9.46V19l4 2v-8.54L22 3z")));
        ICONS.put("rotate-cw", shape(path("M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8"), path("M21 3v5h-5")));
        ICONS.put("box", shape(path("M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4"
                                    + "a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"),
                path("m3.3 7 8.7 5 8.7-5"), path("M12 22V12")));
    }

    private RouteDiagramIcons() {
    }

    /** The names of the icons, for testing. */
    static List<String> names() {
        return List.copyOf(ICONS.keySet());
    }

    /** The icon by its Lucide name, or null. */
    static Shape icon(String name) {
        return ICONS.get(name);
    }

    /** The icon of a step, by its type (such as setBody); the box icon for a step without one of its own. */
    static Shape iconFor(String type) {
        return ICONS.get(iconName(type));
    }

    static String iconName(String type) {
        if (type == null) {
            return "box";
        }
        return switch (type) {
            case "route" -> "workflow";
            case "from" -> "log-in";
            case "to", "toD", "wireTap", "enrich", "pollEnrich", "kamelet" -> "log-out";
            case "log" -> "file-text";
            case "choice", "switch" -> "git-branch";
            case "when", "otherwise" -> "corner-down-right";
            case "doTry" -> "shield";
            case "doCatch", "onException" -> "alert-triangle";
            case "doFinally", "onCompletion" -> "flag";
            case "multicast", "split", "recipientList", "routingSlip", "dynamicRouter", "loadBalance" -> "split";
            case "aggregate" -> "merge";
            case "circuitBreaker" -> "zap";
            case "setBody", "transform" -> "braces";
            case "setHeader", "setHeaders", "setProperty", "setVariable", "setVariables" -> "heading";
            case "removeHeader", "removeHeaders", "removeProperty", "removeProperties", "removeVariable" -> "x";
            case "marshal", "unmarshal", "convertBodyTo", "convertHeaderTo", "convertVariableTo" -> "repeat";
            case "bean", "process", "script" -> "code";
            case "delay", "throttle" -> "clock";
            case "filter", "validate" -> "filter";
            case "loop" -> "rotate-cw";
            default -> "box";
        };
    }

    private static Shape shape(Shape... parts) {
        Path2D.Double answer = new Path2D.Double();
        for (Shape part : parts) {
            answer.append(part, false);
        }
        return answer;
    }

    private static Shape rect(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, 2 * r, 2 * r);
    }

    private static Shape circle(double cx, double cy, double r) {
        return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
    }

    private static Shape line(double x1, double y1, double x2, double y2) {
        return new Line2D.Double(x1, y1, x2, y2);
    }

    private static Shape polyline(double... points) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) {
            p.lineTo(points[i], points[i + 1]);
        }
        return p;
    }

    /**
     * The outline of SVG path data: the commands M, L, H, V, C, S, A and Z, absolute and relative, as Lucide writes
     * them (numbers run together such as {@code 1.1.9}, a command repeated by more numbers).
     */
    static Path2D path(String d) {
        PathParser parser = new PathParser(d);
        return parser.parse();
    }

    private static final class PathParser {
        private final String d;
        private int pos;
        private final Path2D.Double path = new Path2D.Double();
        private double x;
        private double y;
        private double startX;
        private double startY;
        // the second control point of the last curve, for the reflection of S
        private double ctrlX;
        private double ctrlY;
        private boolean lastCurve;

        PathParser(String d) {
            this.d = d;
        }

        Path2D parse() {
            char cmd = 0;
            skipSeparators();
            while (pos < d.length()) {
                char c = d.charAt(pos);
                if (Character.isLetter(c)) {
                    cmd = c;
                    pos++;
                } else if (cmd == 0) {
                    throw new IllegalArgumentException("Path data must start with a command: " + d);
                }
                cmd = command(cmd);
                skipSeparators();
            }
            return path;
        }

        /** Runs one command, and gives the command its further numbers repeat (a moveto repeats as a lineto). */
        private char command(char cmd) {
            boolean rel = Character.isLowerCase(cmd);
            double ox = rel ? x : 0;
            double oy = rel ? y : 0;
            boolean curve = false;
            char next = cmd;
            switch (Character.toUpperCase(cmd)) {
                case 'M' -> {
                    x = ox + number();
                    y = oy + number();
                    path.moveTo(x, y);
                    startX = x;
                    startY = y;
                    next = rel ? 'l' : 'L';
                }
                case 'L' -> {
                    x = ox + number();
                    y = oy + number();
                    path.lineTo(x, y);
                }
                case 'H' -> {
                    x = ox + number();
                    path.lineTo(x, y);
                }
                case 'V' -> {
                    y = oy + number();
                    path.lineTo(x, y);
                }
                case 'C' -> {
                    double x1 = ox + number();
                    double y1 = oy + number();
                    ctrlX = ox + number();
                    ctrlY = oy + number();
                    x = ox + number();
                    y = oy + number();
                    path.curveTo(x1, y1, ctrlX, ctrlY, x, y);
                    curve = true;
                }
                case 'S' -> {
                    double x1 = lastCurve ? 2 * x - ctrlX : x;
                    double y1 = lastCurve ? 2 * y - ctrlY : y;
                    ctrlX = ox + number();
                    ctrlY = oy + number();
                    x = ox + number();
                    y = oy + number();
                    path.curveTo(x1, y1, ctrlX, ctrlY, x, y);
                    curve = true;
                }
                case 'A' -> {
                    double rx = number();
                    double ry = number();
                    double rotation = number();
                    boolean large = flag();
                    boolean sweep = flag();
                    double ex = ox + number();
                    double ey = oy + number();
                    arc(rx, ry, rotation, large, sweep, ex, ey);
                    x = ex;
                    y = ey;
                }
                case 'Z' -> {
                    path.closePath();
                    x = startX;
                    y = startY;
                    next = 0;
                }
                default -> throw new IllegalArgumentException("Unsupported path command " + cmd + " in: " + d);
            }
            lastCurve = curve;
            return next;
        }

        /** An elliptical arc from the current point, as SVG gives it (endpoint parameters, F.6.5 of SVG 1.1). */
        private void arc(double rx, double ry, double rotation, boolean large, boolean sweep, double ex, double ey) {
            if (x == ex && y == ey) {
                return;
            }
            rx = Math.abs(rx);
            ry = Math.abs(ry);
            if (rx == 0 || ry == 0) {
                path.lineTo(ex, ey);
                return;
            }
            double phi = Math.toRadians(rotation);
            double cos = Math.cos(phi);
            double sin = Math.sin(phi);
            double dx = (x - ex) / 2;
            double dy = (y - ey) / 2;
            double x1 = cos * dx + sin * dy;
            double y1 = -sin * dx + cos * dy;
            // radii too small to reach the end point are scaled up
            double lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry);
            if (lambda > 1) {
                rx *= Math.sqrt(lambda);
                ry *= Math.sqrt(lambda);
            }
            double num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1;
            double den = rx * rx * y1 * y1 + ry * ry * x1 * x1;
            double coef = Math.sqrt(Math.max(0, num / den)) * (large == sweep ? -1 : 1);
            double cx1 = coef * rx * y1 / ry;
            double cy1 = -coef * ry * x1 / rx;
            double cx = cos * cx1 - sin * cy1 + (x + ex) / 2;
            double cy = sin * cx1 + cos * cy1 + (y + ey) / 2;
            double theta = angle(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry);
            double delta = angle((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry);
            if (!sweep && delta > 0) {
                delta -= 2 * Math.PI;
            } else if (sweep && delta < 0) {
                delta += 2 * Math.PI;
            }
            // Arc2D counts angles counterclockwise on screen, SVG clockwise (the y axis points down)
            Arc2D.Double arc = new Arc2D.Double(
                    cx - rx, cy - ry, 2 * rx, 2 * ry, -Math.toDegrees(theta), -Math.toDegrees(delta), Arc2D.OPEN);
            Shape shape = phi == 0 ? arc : AffineTransform.getRotateInstance(phi, cx, cy).createTransformedShape(arc);
            path.append(shape, true);
        }

        private static double angle(double ux, double uy, double vx, double vy) {
            return Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
        }

        private boolean flag() {
            skipSeparators();
            char c = d.charAt(pos++);
            if (c != '0' && c != '1') {
                throw new IllegalArgumentException("Expected an arc flag at " + (pos - 1) + " in: " + d);
            }
            return c == '1';
        }

        private double number() {
            skipSeparators();
            int start = pos;
            if (pos < d.length() && (d.charAt(pos) == '-' || d.charAt(pos) == '+')) {
                pos++;
            }
            boolean dot = false;
            while (pos < d.length()) {
                char c = d.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    pos++;
                } else if ((c == 'e' || c == 'E') && pos > start) {
                    pos++;
                    if (pos < d.length() && (d.charAt(pos) == '-' || d.charAt(pos) == '+')) {
                        pos++;
                    }
                } else {
                    break;
                }
            }
            if (pos == start) {
                throw new IllegalArgumentException("Expected a number at " + pos + " in: " + d);
            }
            return Double.parseDouble(d.substring(start, pos));
        }

        private void skipSeparators() {
            while (pos < d.length() && (Character.isWhitespace(d.charAt(pos)) || d.charAt(pos) == ',')) {
                pos++;
            }
        }
    }
}
