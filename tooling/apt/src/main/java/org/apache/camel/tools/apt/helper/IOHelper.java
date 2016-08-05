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
package org.apache.camel.tools.apt.helper;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class IOHelper {

    private IOHelper() {
    }

    /**
     * Loads the entire stream into memory as a String and returns it.
     * <p/>
     * <b>Notice:</b> This implementation appends a <tt>\n</tt> as line
     * terminator at the of the text.
     * <p/>
     * Warning, don't use for crazy big streams :)
     */
    public static String loadText(InputStream in, boolean skipCommentOrEmptyLines) throws IOException {
        StringBuilder builder = new StringBuilder();
        InputStreamReader isr = new InputStreamReader(in);
        try {
            BufferedReader reader = new BufferedReader(isr);
            while (true) {
                String line = reader.readLine();
                if (line != null) {

                    boolean empty = Strings.isNullOrEmpty(line);
                    boolean comment = line.trim().startsWith("#");
                    if (skipCommentOrEmptyLines && (empty || comment)) {
                        continue;
                    }

                    builder.append(line);
                    builder.append("\n");
                } else {
                    break;
                }
            }
            return builder.toString();
        } finally {
            close(isr, in);
        }
    }

    /**
     * Closes the given resources if they are available.
     *
     * @param closeables the objects to close
     */
    public static void close(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            try {
                closeable.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }



}
