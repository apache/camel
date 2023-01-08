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
package org.apache.camel.component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.git.GitEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.SystemReader;

public abstract class RepositoryFactory {

    private static final SystemReader DEFAULT_INSTANCE;
    private static final List<String> VALID_SCHEMES = Arrays.asList("classpath:", "file:", "http:", "https:");

    static {
        DEFAULT_INSTANCE = SystemReader.getInstance();
    }

    private RepositoryFactory() {
    }

    public static Repository of(GitEndpoint endpoint) {
        if (ObjectHelper.isNotEmpty(endpoint.getGitConfigFile())) {
            return resolveConfigFile(endpoint, endpoint.getGitConfigFile());
        }
        return getRepository(endpoint, DEFAULT_INSTANCE);
    }

    private static Repository resolveConfigFile(GitEndpoint endpoint, String uri) {
        if (ObjectHelper.isEmpty(uri)) {
            throw new IllegalArgumentException("URI to git config file must be supplied");
        }

        if (!ResourceHelper.hasScheme(uri) || !VALID_SCHEMES.contains(ResourceHelper.getScheme(uri))) {
            throw new IllegalArgumentException(
                    "URI to git config file must have scheme:path pattern where scheme could be classpath, file, http or https");
        }

        String schema = ResourceHelper.getScheme(uri);
        String path = uri.substring(schema.length());

        File gitConfigFile;
        if (ResourceHelper.isClasspathUri(uri)) {
            gitConfigFile = new File(endpoint.getClass().getClassLoader().getResource(path).getFile());
        } else if (ResourceHelper.isHttpUri(uri)) {
            try {
                gitConfigFile = getTempFileFromHttp(uri);
            } catch (IOException e) {
                throw new RuntimeCamelException(String.format("Something went wrong when loading: %s", uri), e);
            }
        } else { //load from system
            gitConfigFile = new File(path);
            if (Files.isDirectory(gitConfigFile.toPath()) || !Files.isReadable(gitConfigFile.toPath())) {
                throw new IllegalArgumentException(
                        String.format(
                                "The configuration file at %s is unreadable (either missing, lacking proper access permission or is not a regular file)",
                                path));
            }
        }

        return getRepository(endpoint, new CustomConfigSystemReader(gitConfigFile));
    }

    private static Repository getRepository(GitEndpoint endpoint, SystemReader instance) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try {
            SystemReader.setInstance(instance);
            // scan environment GIT_* variables
            return builder.setGitDir(new File(endpoint.getLocalPath(), ".git")).readEnvironment()
                    .findGitDir() // scan up the file system tree
                    .build();
        } catch (IOException e) {
            throw new RuntimeCamelException(
                    String.format("There was an error opening the repository at %s", endpoint.getLocalPath()), e);
        }
    }

    private static File getTempFileFromHttp(String url) throws IOException {
        Path tempFile = Files.createTempFile(null, null);
        FileOutputStream outputStream = new FileOutputStream(tempFile.toString());
        try {
            ReadableByteChannel byteChannel = Channels.newChannel(new URL(url).openStream());
            outputStream.getChannel().transferFrom(byteChannel, 0, Long.MAX_VALUE);
        } finally {
            IOHelper.close(outputStream);
        }
        return tempFile.toFile();
    }

}
