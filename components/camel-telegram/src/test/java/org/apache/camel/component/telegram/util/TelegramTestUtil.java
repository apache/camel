/**
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
package org.apache.camel.component.telegram.util;

import java.io.IOException;

import org.apache.cxf.helpers.IOUtils;

/**
 * Utility functions for telegram tests.
 */
public final class TelegramTestUtil {

    private TelegramTestUtil() {
    }

    /**
     * Creates a sample image.
     *
     * @param imageIOType the image-io code of the image type (eg. PNG, JPG)
     * @return a sample image
     * @throws IOException if anything goes wrong
     */
    public static byte[] createSampleImage(String imageIOType) throws IOException {
        byte[] img;
        if (imageIOType.equalsIgnoreCase("png")) {
            img = IOUtils.readBytesFromStream(TelegramTestUtil.class.getResourceAsStream("/attachments/sample.png"));
        } else if (imageIOType.equalsIgnoreCase("jpg")) {
            img = IOUtils.readBytesFromStream(TelegramTestUtil.class.getResourceAsStream("/attachments/sample.jpg"));
        } else {
            throw new IllegalArgumentException("Unknown format " + imageIOType);
        }
        return img;
    }


    public static byte[] createSampleAudio() throws IOException {
        byte[] audio = IOUtils.readBytesFromStream(TelegramTestUtil.class.getResourceAsStream("/attachments/sample.mp3"));
        return audio;
    }

    public static byte[] createSampleVideo() throws IOException {
        byte[] video = IOUtils.readBytesFromStream(TelegramTestUtil.class.getResourceAsStream("/attachments/sample.mp4"));
        return video;
    }

    public static byte[] createSampleDocument() throws IOException {
        byte[] document = IOUtils.readBytesFromStream(TelegramTestUtil.class.getResourceAsStream("/attachments/sample.txt"));
        return document;
    }
}
