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
package org.apache.camel.itest.springboot.arquillian.container.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.jboss.arquillian.container.se.api.ClassPathDirectory;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.asset.ClassAsset;

public final class FileDeploymentUtils {

    private static final char DELIMITER_RESOURCE_PATH = '/';
    private static final char DELIMITER_CLASS_NAME_PATH = '.';
    private static final String EXTENSION_CLASS = ".class";

    private FileDeploymentUtils() {
    }

    public static void materializeClass(File entryDirectory, ClassAsset classAsset) throws DeploymentException, IOException {
        File classDirectory;
        if (classAsset.getSource().getPackage() != null) {
            classDirectory = new File(entryDirectory, classAsset.getSource().getPackage().getName().replace(DELIMITER_CLASS_NAME_PATH, File.separatorChar));
            if (!classDirectory.mkdirs()) {
                throw new DeploymentException("Could not create class package directory: " + classDirectory);
            }
        } else {
            classDirectory = entryDirectory;
        }
        File classFile = new File(classDirectory, classAsset.getSource().getSimpleName().concat(EXTENSION_CLASS));
        classFile.createNewFile();
        try (InputStream in = classAsset.openStream(); OutputStream out = new FileOutputStream(classFile)) {
            copy(in, out);
        }
    }

    public static void materializeSubdirectories(File entryDirectory, Node node) throws DeploymentException, IOException {
        for (Node child : node.getChildren()) {
            if (child.getAsset() == null) {
                materializeSubdirectories(entryDirectory, child);
            } else {
                if (ClassPathDirectory.isMarkerFileArchivePath(child.getPath())) {
                    // Do not materialize the marker file
                    continue;
                }
                // E.g. META-INF/my-super-descriptor.xml
                File resourceFile = new File(entryDirectory, child.getPath().get().replace(DELIMITER_RESOURCE_PATH, File.separatorChar));
                File resoureDirectory = resourceFile.getParentFile();
                if (!resoureDirectory.exists() && !resoureDirectory.mkdirs()) {
                    throw new DeploymentException("Could not create class path directory: " + entryDirectory);
                }
                resourceFile.createNewFile();
                try (InputStream in = child.getAsset().openStream(); OutputStream out = new FileOutputStream(resourceFile)) {
                    copy(in, out);
                }
                child.getPath().get();
            }
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        final byte[] buffer = new byte[8192];
        int n = 0;
        while (-1 != (n = in.read(buffer))) {
            out.write(buffer, 0, n);
        }
        out.flush();
    }

    public static void deleteRecursively(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteContent(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
