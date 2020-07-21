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
package org.apache.camel.component.telegram.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.util.IOHelper;

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
            img = readBytesFromStream(TelegramTestUtil.class.getResourceAsStream("/attachments/sample.png"));
        } else if (imageIOType.equalsIgnoreCase("jpg")) {
            img = readBytesFromStream(TelegramTestUtil.class.getResourceAsStream("/attachments/sample.jpg"));
        } else if ("webp".equalsIgnoreCase(imageIOType)) {
            img = readBytesFromStream(TelegramTestUtil.class.getResourceAsStream("/attachments/sample.webp"));
        } else {
            throw new IllegalArgumentException("Unknown format " + imageIOType);
        }
        return img;
    }


    private static byte[] readBytesFromStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(IOHelper.DEFAULT_BUFFER_SIZE);
        IOHelper.copy(in, out, IOHelper.DEFAULT_BUFFER_SIZE);
        return out.toByteArray();
    }

    public static byte[] createSampleAudio() throws IOException {
        byte[] audio = readBytesFromStream(TelegramTestUtil.class.getResourceAsStream("/attachments/sample.mp3"));
        return audio;
    }

    public static byte[] createSampleVideo() throws IOException {
        byte[] video = readBytesFromStream(TelegramTestUtil.class.getResourceAsStream("/attachments/sample.mp4"));
        return video;
    }

    public static byte[] createSampleDocument() throws IOException {
        byte[] document = readBytesFromStream(TelegramTestUtil.class.getResourceAsStream("/attachments/sample.txt"));
        return document;
    }

    public static String stringResource(String path) {
        try (Reader r = new InputStreamReader(TelegramTestUtil.class.getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8)) {
            return IOHelper.toString(r);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String serialize(Object result) {
        try {
            return new ObjectMapper().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
