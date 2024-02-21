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
package org.apache.camel.cli.connector;

import java.lang.management.ManagementFactory;
import java.util.Set;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.util.StringHelper;

/**
 * Helper for logger action.
 *
 * Currently, only log4j is supported.
 */
public final class LoggerHelper {

    // log4j support
    private static final String LOG4J_MBEAN = "org.apache.logging.log4j2";

    private LoggerHelper() {
    }

    /**
     * Change logging level in the logging system.
     *
     * Currently, only log4j is supported.
     *
     * @param logger the logger name, null is assumed to be root
     * @param level  the new logging level
     */
    public static void changeLoggingLevel(String logger, String level) {
        if (logger == null || logger.isEmpty()) {
            logger = "root";
        }

        try {
            MBeanServer ms = ManagementFactory.getPlatformMBeanServer();
            if (ms != null) {
                Set<ObjectName> set = ms.queryNames(new ObjectName(LOG4J_MBEAN + ":type=*,component=Loggers,name=*"), null);
                for (ObjectName on : set) {
                    if (ms.isRegistered(on)) {
                        String name = (String) ms.getAttribute(on, "Name");
                        if (name == null || name.isEmpty()) {
                            name = "root";
                        }
                        if (logger.equals(name)) {
                            ms.setAttribute(on, new Attribute("Level", level));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public static String stripSourceLocationLineNumber(String location) {
        int cnt = StringHelper.countChar(location, ':');
        if (cnt >= 1) {
            int pos = location.lastIndexOf(':');
            return location.substring(0, pos);
        } else {
            return location;
        }
    }

    public static Integer extractSourceLocationLineNumber(String location) {
        int cnt = StringHelper.countChar(location, ':');
        if (cnt >= 1) {
            int pos = location.lastIndexOf(':');
            String num = location.substring(pos + 1);
            try {
                return Integer.valueOf(num);
            } catch (Exception var5) {
                return null;
            }
        } else {
            return null;
        }
    }

    public static String extractSourceLocationId(String location) {
        int cnt = StringHelper.countChar(location, ':');
        if (cnt >= 1) {
            int pos = location.lastIndexOf(':');
            return location.substring(pos + 1);
        } else {
            return null;
        }
    }

}
