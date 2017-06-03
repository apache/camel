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
package org.apache.camel.itest.springboot.util;

import java.io.File;
import java.io.IOException;

public final class LocationUtils {

    private LocationUtils() {
    }

    public static File camelRoot(String path) {
        return new File(camelRoot(), path);
    }

    public static File camelRoot() {
        try {
            File root = new File(".").getCanonicalFile();
            while (root != null) {
                File[] names = root.listFiles(pathname -> pathname.getName().equals("camel-core"));
                if (names != null && names.length == 1) {
                    break;
                }
                root = root.getParentFile();
            }

            if (root == null) {
                throw new IllegalStateException("Cannot find Apache Camel project root directory");
            }
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("Error while getting directory", e);
        }
    }

}
