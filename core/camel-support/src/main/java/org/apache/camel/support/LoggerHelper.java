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
package org.apache.camel.support;

import org.apache.camel.LineNumberAware;
import org.apache.camel.NamedRoute;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;

/**
 * Helper for logging purposes.
 */
public final class LoggerHelper {

    private LoggerHelper() {
    }

    /**
     * If the node is {@link LineNumberAware} then get a logger name that will point to the source:line, otherwise
     * return <tt>null</tt>.
     *
     * @param  node the node
     * @return      the logger name, or <tt>null</tt>
     */
    public static String getLineNumberLoggerName(Object node) {
        String name = null;
        if (node instanceof LineNumberAware) {
            if (node instanceof NamedRoute) {
                // we want the input from a route as it has the source location / line number
                node = ((NamedRoute) node).getInput();
            }
            String loc = ((LineNumberAware) node).getLocation();
            int line = ((LineNumberAware) node).getLineNumber();
            if (loc != null) {
                // is it a class or file?
                name = loc;
                if (loc.contains(":")) {
                    // strip prefix
                    loc = StringHelper.after(loc, ":", loc);

                    // file based such as xml and yaml
                    name = FileUtil.stripPath(loc);
                } else {
                    // classname so let us only grab the name
                    int pos = name.lastIndexOf('.');
                    if (pos > 0) {
                        name = name.substring(0, pos);
                    }
                }
                if (line != -1) {
                    name += ":" + line;
                }
            }
        }
        return name;
    }

    public static String getSourceLocation(Object node) {
        String name = null;
        if (node instanceof LineNumberAware) {
            if (node instanceof NamedRoute) {
                // we want the input from a route as it has the source location / line number
                node = ((NamedRoute) node).getInput();
            }
            String loc = ((LineNumberAware) node).getLocation();
            int line = ((LineNumberAware) node).getLineNumber();
            if (loc != null) {
                // is it a class or file?
                name = loc;
                if (line != -1) {
                    name += ":" + line;
                }
            }
        }
        return name;
    }

    public static String stripSourceLocationLineNumber(String location) {
        int cnt = StringHelper.countChar(location, ':');
        if (cnt > 1) {
            int pos = location.lastIndexOf(':');
            return location.substring(0, pos);
        } else {
            return location;
        }
    }

    public static Integer extractSourceLocationLineNumber(String location) {
        int cnt = StringHelper.countChar(location, ':');
        if (cnt > 1) {
            int pos = location.lastIndexOf(':');
            // in case pos is end of line
            if (pos < location.length() - 1) {
                String num = location.substring(pos + 1);
                try {
                    return Integer.valueOf(num);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

}
