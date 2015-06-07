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
package org.apache.camel.component.kafka.embedded;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

final class TestUtils {
    private static final Random RANDOM = new Random();

    private TestUtils() {
    }

    public static File constructTempDir(String dirPrefix) {
        File file = new File(System.getProperty("java.io.tmpdir"), dirPrefix + RANDOM.nextInt(10000000));
        if (!file.mkdirs()) {
            throw new RuntimeException("could not create temp directory: " + file.getAbsolutePath());
        }
        file.deleteOnExit();
        return file;
    }

    public static int getAvailablePort() {
        try {
            ServerSocket socket = new ServerSocket(0);
            try {
                return socket.getLocalPort();
            } finally {
                socket.close();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot find available port: " + e.getMessage(), e);
        }
    }

    public static boolean deleteFile(File path) throws FileNotFoundException {
        if (!path.exists()) {
            throw new FileNotFoundException(path.getAbsolutePath());
        }
        boolean ret = true;
        if (path.isDirectory()) {
            for (File f : path.listFiles()) {
                ret = ret && deleteFile(f);
            }
        }
        return ret && path.delete();
    }
}
