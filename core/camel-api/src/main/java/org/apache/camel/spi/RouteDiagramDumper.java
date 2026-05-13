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
package org.apache.camel.spi;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * To dump routes as visual diagrams as PNG images.
 */
public interface RouteDiagramDumper {

    enum NodeLabelMode {
        CODE,
        DESCRIPTION,
        BOTH
    }

    enum Theme {
        DARK,
        LIGHT,
        TRANSPARENT
    }

    /**
     * Service factory key.
     */
    String FACTORY = "route-diagram-dumper";

    /**
     * Dumps the routes as PNG files in the given file
     *
     * @param filter to filter routes
     * @param theme  the coloring theme
     * @param file   the name of the file to store
     */
    void dumpRoutesToFile(String filter, Theme theme, File file) throws IOException;

    /**
     * Dumps the routes as PNG files in the given folder
     *
     * @param filter to filter routes
     * @param theme  the coloring theme
     * @param folder the folder to store the files
     */
    void dumpRoutesToFolder(String filter, Theme theme, File folder) throws IOException;

    /**
     * Dumps the routes as a PNG image
     *
     * @param filter to filter routes
     * @param theme  the coloring theme
     */
    default BufferedImage dumpRoutesAsImage(String filter, Theme theme) {
        return dumpRoutesAsImage(filter, theme, false, NodeLabelMode.CODE, 180, 12);
    }

    /**
     * Dumps the routes as a PNG image
     *
     * @param filter    to filter routes
     * @param theme     the coloring theme
     * @param metrics   whether to include live metric counters
     * @param nodeLabel what information to display in the nodes
     * @param nodeWidth the width in pixels of the node boxes
     * @param fontSize  the font size
     */
    BufferedImage dumpRoutesAsImage(
            String filter, Theme theme, boolean metrics,
            NodeLabelMode nodeLabel, int nodeWidth, int fontSize);

    /**
     * Converts the image to base64
     */
    String imageToBase64(BufferedImage image) throws IOException;

    /**
     * Dumps the routes as ASCII art text
     *
     * @param filter to filter routes
     */
    default String dumpRoutesAsAsciiArt(String filter) {
        return dumpRoutesAsAsciiArt(filter, NodeLabelMode.CODE, 180, false);
    }

    /**
     * Dumps the routes as ASCII art text
     *
     * @param filter    to filter routes
     * @param nodeLabel what information to display in the nodes
     * @param nodeWidth the width in pixels of the node boxes
     */
    default String dumpRoutesAsAsciiArt(String filter, NodeLabelMode nodeLabel, int nodeWidth) {
        return dumpRoutesAsAsciiArt(filter, nodeLabel, nodeWidth, false);
    }

    /**
     * Dumps the routes as ASCII art or Unicode box-drawing text
     *
     * @param filter    to filter routes
     * @param nodeLabel what information to display in the nodes
     * @param nodeWidth the width in pixels of the node boxes
     * @param unicode   whether to use Unicode box-drawing characters
     */
    default String dumpRoutesAsAsciiArt(String filter, NodeLabelMode nodeLabel, int nodeWidth, boolean unicode) {
        throw new UnsupportedOperationException();
    }

}
