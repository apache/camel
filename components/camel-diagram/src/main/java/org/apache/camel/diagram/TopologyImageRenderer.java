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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.apache.camel.diagram.RouteDiagramRenderer.DiagramColors;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutEdge;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutNode;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyLayoutResult;

/**
 * Renders topology diagrams as PNG images.
 */
public class TopologyImageRenderer {

    private TopologyImageRenderer() {
    }

    public static BufferedImage renderImage(
            TopologyLayoutResult result, DiagramColors colors,
            int fontSize, int nodeWidth, boolean metrics, boolean showDescription) {

        int scale = RouteDiagramLayoutEngine.SCALE;
        int width = result.totalWidth + RouteDiagramLayoutEngine.PADDING;
        int height = result.totalHeight + RouteDiagramLayoutEngine.PADDING;

        width = Math.max(width, nodeWidth * scale + RouteDiagramLayoutEngine.PADDING * 2);
        height = Math.max(height, 100 * scale);

        Color bgColor = colors.getBg();

        int imageType = bgColor != null ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage image = new BufferedImage(
                Math.min(width, 16384), Math.min(height, 16384), imageType);

        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (bgColor != null) {
            g.setColor(bgColor);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
        }

        int fontSizeScaled = fontSize * scale;
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, fontSizeScaled);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        int arc = 14 * scale;
        float strokeWidth = 1.5f * scale;
        int arrowSize = 6 * scale;
        int nw = nodeWidth * scale;

        drawEdges(g, result, colors, nw, strokeWidth, arrowSize);
        drawNodes(g, result, colors, font, fm, nw, fontSizeScaled, arc, strokeWidth, metrics, showDescription);

        g.dispose();
        return image;
    }

    private static void drawEdges(
            Graphics2D g, TopologyLayoutResult result, DiagramColors colors,
            int nw, float strokeWidth, int arrowSize) {

        for (TopologyLayoutEdge edge : result.edges) {
            if (edge.selfLoop) {
                continue;
            }

            boolean dashed = "external".equals(edge.connectionType);
            java.awt.Stroke stroke;
            if (dashed) {
                stroke = new BasicStroke(
                        strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10.0f, new float[] { 10f, 5f }, 0.0f);
            } else {
                stroke = new BasicStroke(strokeWidth);
            }
            g.setStroke(stroke);
            g.setColor(colors.getArrow());

            int fromCx = edge.from.x + nw / 2;
            int fromBy = edge.from.y + edge.from.height;
            int toCx = edge.to.x + nw / 2;
            int toTy = edge.to.y;

            if (fromCx == toCx) {
                g.drawLine(fromCx, fromBy, toCx, toTy - arrowSize / 2);
            } else {
                int midY = fromBy + (toTy - fromBy) / 2;
                g.drawLine(fromCx, fromBy, fromCx, midY);
                g.drawLine(fromCx, midY, toCx, midY);
                g.drawLine(toCx, midY, toCx, toTy - arrowSize / 2);
            }

            // Arrow head
            int[] xPoints = { toCx - arrowSize, toCx + arrowSize, toCx };
            int[] yPoints = { toTy - arrowSize, toTy - arrowSize, toTy };
            g.fillPolygon(xPoints, yPoints, 3);
        }
    }

    private static void drawNodes(
            Graphics2D g, TopologyLayoutResult result, DiagramColors colors,
            Font font, FontMetrics fm, int nw, int fontSizeScaled,
            int arc, float strokeWidth, boolean metrics, boolean showDescription) {

        for (TopologyLayoutNode node : result.nodes) {
            Color nodeColor;
            if ("trigger".equals(node.nodeType)) {
                nodeColor = colors.getNodeFrom();
            } else {
                nodeColor = colors.getNodeDefault();
            }

            // Node box
            g.setColor(nodeColor);
            g.setStroke(new BasicStroke(strokeWidth));
            g.fillRoundRect(node.x, node.y, nw, node.height, arc, arc);

            g.setColor(nodeColor.brighter());
            g.drawRoundRect(node.x, node.y, nw, node.height, arc, arc);

            // Text
            g.setColor(colors.getText());
            String line1;
            if (showDescription && node.description != null && !node.description.isBlank()) {
                line1 = node.description;
            } else {
                line1 = node.routeId;
            }
            int lineHeight = fm.getHeight();
            int textY;

            if (showDescription) {
                textY = node.y + (node.height - lineHeight) / 2 + fm.getAscent();
                int line1Width = fm.stringWidth(line1);
                g.drawString(line1, node.x + (nw - line1Width) / 2, textY);
            } else {
                String line2 = "(" + node.from + ")";
                textY = node.y + (node.height - 2 * lineHeight) / 2 + fm.getAscent();

                int line1Width = fm.stringWidth(line1);
                g.drawString(line1, node.x + (nw - line1Width) / 2, textY);

                Font smallFont = font.deriveFont((float) (fontSizeScaled * 0.85));
                g.setFont(smallFont);
                int line2Width = g.getFontMetrics().stringWidth(line2);
                g.drawString(line2, node.x + (nw - line2Width) / 2, textY + lineHeight);
                g.setFont(font);
            }

            // Metrics
            if (metrics && node.exchangesTotal > 0) {
                long ok = node.exchangesTotal - node.exchangesFailed;
                Font metricsFont = font.deriveFont((float) (fontSizeScaled * 0.75));
                g.setFont(metricsFont);
                if (ok > 0) {
                    g.setColor(new Color(0x43a047));
                    String okStr = String.valueOf(ok);
                    g.drawString(okStr, node.x + nw / 2 - g.getFontMetrics().stringWidth(okStr) - 4,
                            textY + 2 * lineHeight);
                }
                if (node.exchangesFailed > 0) {
                    g.setColor(new Color(0xe53935));
                    String failStr = String.valueOf(node.exchangesFailed);
                    g.drawString(failStr, node.x + nw / 2 + 4, textY + 2 * lineHeight);
                }
                g.setFont(font);
            }
        }
    }

}
