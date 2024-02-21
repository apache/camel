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
package org.apache.camel.dsl.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.camel.spi.Resource;
import org.apache.camel.util.IOHelper;

/**
 * Default {@link SourceLoader}.
 */
public class DefaultSourceLoader implements SourceLoader {

    @Override
    public String loadResource(Resource resource) throws IOException {
        InputStream in = resource.getInputStream();

        StringBuilder builder = new StringBuilder();
        InputStreamReader isr = new InputStreamReader(in);
        boolean first = true;
        try {
            BufferedReader reader = IOHelper.buffered(isr);
            while (true) {
                String line = reader.readLine();
                if (line != null) {
                    // we need to skip first line if it starts with a special script marker for camel-jbang in pipe mode
                    if (first && line.startsWith("///usr/bin/env jbang") && line.contains("camel@apache/camel pipe")) {
                        line = ""; // use an empty line so line numbers still matches
                    }
                    builder.append(line);
                    builder.append("\n");
                    first = false;
                } else {
                    break;
                }
            }
            return builder.toString();
        } finally {
            IOHelper.close(isr, in);
        }
    }
}
