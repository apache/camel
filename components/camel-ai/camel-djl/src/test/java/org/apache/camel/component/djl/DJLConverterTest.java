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
package org.apache.camel.component.djl;

import java.io.File;
import java.nio.file.Path;

import ai.djl.modality.cv.Image;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DJLConverterTest {

    private static final String IMAGE_RESOURCE = "/data/detect/kitten.jpg";

    // Converting from a File opens a stream internally; it must be closed (try-with-resources) so the
    // file descriptor is not leaked per conversion. The conversion must still return a valid Image.
    @Test
    void toImageFromFileReturnsImageWithoutLeakingStream() throws Exception {
        File file = new File(DJLConverterTest.class.getResource(IMAGE_RESOURCE).toURI());
        Image image = DJLConverter.toImage(file);
        assertNotNull(image);
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
    }

    @Test
    void toImageFromPathReturnsImageWithoutLeakingStream() throws Exception {
        Path path = Path.of(DJLConverterTest.class.getResource(IMAGE_RESOURCE).toURI());
        Image image = DJLConverter.toImage(path);
        assertNotNull(image);
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
    }
}
