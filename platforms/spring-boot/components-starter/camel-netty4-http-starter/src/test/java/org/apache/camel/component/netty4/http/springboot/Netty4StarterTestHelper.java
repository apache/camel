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
package org.apache.camel.component.netty4.http.springboot;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.converter.IOConverter;
import org.apache.camel.test.AvailablePortFinder;

public final class Netty4StarterTestHelper {

    private static volatile int port = -1;

    private Netty4StarterTestHelper() {
    }

    private static void initPort() throws Exception {
        if (port <= 0) {
            File file = new File("target/nettyport.txt");

            if (!file.exists()) {
                // start from somewhere in the 26xxx range
                port = AvailablePortFinder.getNextAvailable(26000);
            } else {
                // read port number from file
                String s = IOConverter.toString(file, null);
                port = Integer.parseInt(s);
                // use next free port
                port = AvailablePortFinder.getNextAvailable(port + 1);
            }

            // save to file, do not append
            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                fos.write(String.valueOf(port).getBytes());
            }
        }
    }

    public static int getPort() {
        try {
            initPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return port;
    }
}
