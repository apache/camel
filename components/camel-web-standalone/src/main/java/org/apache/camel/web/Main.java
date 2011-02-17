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
package org.apache.camel.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.mortbay.jetty.runner.Runner;

/**
 * A bootstrap class for starting Jetty Runner using an embedded war
 *
 * @version 
 */
public final class Main {
    private static final String WAR_POSTFIX = ".war";
    private static final String WAR_NAME = "camel-web";
    private static final String WAR_FILENAME = WAR_NAME + WAR_POSTFIX;
    private static final int KB = 1024;

    private Main() {
        // is started from main
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Welcome to Apache Camel!");
        
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(WAR_FILENAME);
        if (resource == null) {
            System.err.println("Could not find the " + WAR_FILENAME + " on classpath!");
            System.exit(1);
        }

        File warFile = File.createTempFile(WAR_NAME + "-", WAR_POSTFIX);
        System.out.println("Extracting " + WAR_FILENAME + " to " + warFile + " ...");

        writeStreamTo(resource.openStream(), new FileOutputStream(warFile), 8 * KB);

        System.out.println("Extracted " + WAR_FILENAME);
        System.out.println("Launching Jetty Runner...");

        List<String> argsList = new ArrayList<String>();
        if (args != null) {
            argsList.addAll(Arrays.asList(args));
        }
        argsList.add(warFile.getCanonicalPath());
        Runner.main(argsList.toArray(new String[argsList.size()]));
        System.exit(0);
    }

    public static int writeStreamTo(final InputStream input, final OutputStream output, int bufferSize) throws IOException {
        int available = Math.min(input.available(), 256 * KB);
        byte[] buffer = new byte[Math.max(bufferSize, available)];
        int answer = 0;
        int count = input.read(buffer);
        while (count >= 0) {
            output.write(buffer, 0, count);
            answer += count;
            count = input.read(buffer);
        }
        return answer;
    }
}
