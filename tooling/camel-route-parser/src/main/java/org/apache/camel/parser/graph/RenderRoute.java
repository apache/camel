/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.parser.graph;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.apache.camel.parser.model.CamelNodeDetails;

/**
 * @deprecated  experiment to render a route via Java image
 */
@Deprecated
public class RenderRoute {

    public static void main(String[] args) {
        RenderRoute render = new RenderRoute();
        render(null);
    }

    public static void render(CamelNodeDetails root) {
        // TODO:
        try {
            int width = 200, height = 200;

            // TYPE_INT_ARGB specifies the image format: 8-bit RGBA packed
            // into integer pixels
//            BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            Graphics2D ig2 = bi.createGraphics();

            ig2.drawRect(10, 10, 80, 40);
            ig2.drawLine(45, 50, 45, 80);
            ig2.drawRect(10, 80, 80, 40);

            Font font = new Font("Arial", Font.BOLD, 20);
            ig2.setFont(font);
            String message = "Apache Camel";
            FontMetrics fontMetrics = ig2.getFontMetrics();
            int stringWidth = fontMetrics.stringWidth(message);
            int stringHeight = fontMetrics.getAscent();
            ig2.setPaint(Color.black);
            ig2.drawString(message, (width - stringWidth) / 2, height / 2 + stringHeight / 4);

            ImageIO.write(bi, "PNG", new File("target/route.png"));

        } catch (IOException ie) {
            ie.printStackTrace();
        }

    }
}
