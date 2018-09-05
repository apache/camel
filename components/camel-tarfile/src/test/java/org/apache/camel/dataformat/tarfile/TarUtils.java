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
package org.apache.camel.dataformat.tarfile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.camel.util.IOHelper;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

final class TarUtils {

    static final String TEXT = "The Masque of Queen Bersabe (excerpt) \n"
            + "by: Algernon Charles Swinburne \n\n"
            + "My lips kissed dumb the word of Ah \n"
            + "Sighed on strange lips grown sick thereby. \n"
            + "God wrought to me my royal bed; \n"
            + "The inner work thereof was red, \n"
            + "The outer work was ivory. \n"
            + "My mouth's heat was the heat of flame \n"
            + "For lust towards the kings that came \n"
            + "With horsemen riding royally.";

    private TarUtils() {
        // Prevent instantiation
    }

    static byte[] getTaredText(String entryName) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(TEXT.getBytes("UTF-8"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(baos);
        try {
            TarArchiveEntry entry = new TarArchiveEntry(entryName);
            entry.setSize(bais.available());
            tos.putArchiveEntry(entry);
            IOHelper.copy(bais, tos);
        } finally {
            tos.closeArchiveEntry();
            IOHelper.close(bais, tos);
        }
        return baos.toByteArray();
    }

    static byte[] getTaredTextInFolder(String folder, String file) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(TEXT.getBytes("UTF-8"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TarArchiveOutputStream tos = new TarArchiveOutputStream(baos);
        try {
            TarArchiveEntry folderEntry = new TarArchiveEntry(folder);
            folderEntry.setSize(0L);
            tos.putArchiveEntry(folderEntry);

            TarArchiveEntry fileEntry = new TarArchiveEntry(file);
            fileEntry.setSize(bais.available());
            tos.putArchiveEntry(fileEntry);

            IOHelper.copy(bais, tos);
        } finally {
            tos.closeArchiveEntry();
            IOHelper.close(bais, tos);
        }
        return baos.toByteArray();
    }

    static byte[] getBytes(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            IOHelper.copy(fis, baos);
        } finally {
            IOHelper.close(fis, baos);
        }
        return baos.toByteArray();
    }

}
