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
package org.apache.camel.component.file;

import java.io.EOFException;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The resume support positions the body at a recorded offset. An offset that no longer fits the file - because it was
 * truncated or replaced - has to be reported rather than silently yielding a short body, and the file must not be left
 * open when it is.
 */
public class GenericFileConverterResumeOffsetTest {

    @TempDir
    Path folder;

    @Test
    public void shouldSkipToTheOffset() throws Exception {
        GenericFile<File> file = fileOf("0123456789", 4);

        try (InputStream is = GenericFileConverter.genericFileToInputStream(file, null)) {
            assertEquals("456789", new String(is.readAllBytes()));
        }
    }

    @Test
    public void shouldFailOnAnOffsetPastTheEndOfTheFile() throws Exception {
        GenericFile<File> file = fileOf("0123456789", 50);

        assertThrows(EOFException.class, () -> GenericFileConverter.genericFileToInputStream(file, null));

        // nothing returned the stream, so the converter has to have closed it: on Windows an open handle would
        // stop the file being deleted or moved, which is what a failing poll does next
        Files.delete(file.getFile().toPath());
        assertTrue(Files.notExists(file.getFile().toPath()));
    }

    @Test
    public void shouldFailOnAnOffsetPastTheEndOfTheFileForAReader() throws Exception {
        GenericFile<File> file = fileOf("0123456789", 50);
        file.setCharset("UTF-8");

        assertThrows(EOFException.class, () -> GenericFileConverter.genericFileToReader(file, null));

        Files.delete(file.getFile().toPath());
        assertTrue(Files.notExists(file.getFile().toPath()));
    }

    @Test
    public void shouldSkipToTheOffsetForAReader() throws Exception {
        GenericFile<File> file = fileOf("0123456789", 4);
        file.setCharset("UTF-8");

        Reader reader = GenericFileConverter.genericFileToReader(file, null);
        try {
            assertEquals("456789", IOHelper.toString(reader));
        } finally {
            IOHelper.close(reader);
        }
    }

    private GenericFile<File> fileOf(String content, long offset) throws Exception {
        File f = folder.resolve("resume-" + offset + "-" + content.length() + ".txt").toFile();
        Files.writeString(f.toPath(), content);

        GenericFile<File> file = new GenericFile<>();
        file.setFile(f);
        file.setFileName(f.getName());
        file.setAbsoluteFilePath(f.getAbsolutePath());
        file.setFileLength(f.length());
        file.updateLastOffsetValue(offset);
        return file;
    }

}
