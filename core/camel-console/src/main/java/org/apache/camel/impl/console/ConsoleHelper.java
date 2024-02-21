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
package org.apache.camel.impl.console;

import java.io.LineNumberReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

import static org.apache.camel.support.LoggerHelper.extractSourceLocationLineNumber;

public final class ConsoleHelper {

    private ConsoleHelper() {
    }

    public static List<JsonObject> loadSourceAsJson(CamelContext camelContext, String location) {
        if (location == null) {
            return null;
        }
        Integer lineNumber = extractSourceLocationLineNumber(location);
        try {
            location = LoggerHelper.stripSourceLocationLineNumber(location);
            Resource resource = PluginHelper.getResourceLoader(camelContext).resolveResource(location);
            if (resource != null) {
                return loadSourceAsJson(resource.getReader(), lineNumber);
            }
        } catch (Exception e) {
            // ignore
        }

        return Collections.EMPTY_LIST;
    }

    public static List<JsonObject> loadSourceAsJson(Reader reader, Integer lineNumber) {
        List<JsonObject> code = new ArrayList<>();
        try {
            LineNumberReader lnr = new LineNumberReader(reader);
            int i = 0;
            String t;
            do {
                t = lnr.readLine();
                if (t != null) {
                    i++;
                    JsonObject c = new JsonObject();
                    c.put("line", i);
                    c.put("code", Jsoner.escape(t));
                    if (lineNumber != null && lineNumber == i) {
                        c.put("match", true);
                    }
                    code.add(c);
                }
            } while (t != null);
            IOHelper.close(lnr);
        } catch (Exception e) {
            // ignore
        }

        return code.isEmpty() ? null : code;
    }

    public static String loadSourceLine(CamelContext camelContext, String location, Integer lineNumber) {
        if (location == null || lineNumber == null) {
            return null;
        }

        try {
            location = LoggerHelper.stripSourceLocationLineNumber(location);
            Resource resource = PluginHelper.getResourceLoader(camelContext).resolveResource(location);
            if (resource != null) {
                LineNumberReader reader = new LineNumberReader(resource.getReader());
                int i = 0;
                String t;
                do {
                    t = reader.readLine();
                    if (t != null) {
                        i++;
                        if (i == lineNumber) {
                            return t;
                        }
                    }
                } while (t != null);
                IOHelper.close(reader);
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

}
