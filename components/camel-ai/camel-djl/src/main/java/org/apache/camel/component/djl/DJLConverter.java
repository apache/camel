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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import ai.djl.modality.audio.Audio;
import ai.djl.modality.audio.AudioFactory;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;

/**
 * Converter methods to convert from / to DJL types.
 */
@Converter(generateLoader = true)
public class DJLConverter {

    @Converter
    public static Image toImage(byte[] bytes) throws IOException {
        return toImage(new ByteArrayInputStream(bytes));
    }

    @Converter
    public static Image toImage(File file) throws IOException {
        return toImage(new FileInputStream(file));
    }

    @Converter
    public static Image toImage(Path path) throws IOException {
        return toImage(Files.newInputStream(path));
    }

    @Converter
    public static Image toImage(InputStream inputStream) throws IOException {
        return ImageFactory.getInstance().fromInputStream(inputStream);
    }

    @Converter
    public static Image toImage(DetectedObjects.DetectedObject detectedObject, Exchange exchange) {
        if (exchange == null || exchange.getMessage() == null) {
            return null;
        }

        Rectangle rect = detectedObject.getBoundingBox().getBounds();
        Image image = exchange.getMessage().getHeader(DJLConstants.INPUT, Image.class);
        return image.getSubImage(
                (int) (rect.getX() * image.getWidth()),
                (int) (rect.getY() * image.getHeight()),
                (int) (rect.getWidth() * image.getWidth()),
                (int) (rect.getHeight() * image.getHeight()));
    }

    @Converter
    public static Image[] toImages(DetectedObjects detectedObjects, Exchange exchange) {
        return detectedObjects.<DetectedObjects.DetectedObject>items().stream()
                .map(obj -> toImage(obj, exchange))
                .toArray(Image[]::new);
    }

    @Converter
    public static byte[] toBytes(Image image, Exchange exchange) throws IOException {
        if (exchange == null
                || exchange.getMessage() == null
                || exchange.getMessage().getHeader(DJLConstants.FILE_TYPE) == null) {
            throw new TypeConversionException(
                    image,
                    Image.class,
                    new IllegalStateException("File type must be provided via " + DJLConstants.FILE_TYPE + " header"));
        }

        String fileType = exchange.getMessage().getHeader(DJLConstants.FILE_TYPE, String.class);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        image.save(os, fileType);
        return os.toByteArray();
    }

    @Converter
    public static Audio toAudio(byte[] bytes) throws IOException {
        return toAudio(new ByteArrayInputStream(bytes));
    }

    @Converter
    public static Audio toAudio(File file) throws IOException {
        return toAudio(new FileInputStream(file));
    }

    @Converter
    public static Audio toAudio(Path path) throws IOException {
        return toAudio(Files.newInputStream(path));
    }

    @Converter
    public static Audio toAudio(InputStream inputStream) throws IOException {
        return AudioFactory.newInstance().fromInputStream(inputStream);
    }
}
