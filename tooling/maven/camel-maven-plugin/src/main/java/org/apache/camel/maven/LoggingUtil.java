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
package org.apache.camel.maven;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.LoggerFactory;

public final class LoggingUtil {

    static {
        Configurator.initialize(null, "log4j2.properties");
    }

    private LoggingUtil() {
    }

    public static void configureLog(String level) {
        level = level.toLowerCase();

        switch (level) {
            case "trace":
                Configurator.setRootLevel(Level.TRACE);
                break;
            case "debug":
                Configurator.setRootLevel(Level.DEBUG);
                break;
            case "info":
                Configurator.setRootLevel(Level.INFO);
                break;
            case "warn":
                Configurator.setRootLevel(Level.WARN);
                break;
            case "error":
                Configurator.setRootLevel(Level.ERROR);
                break;
            case "fatal":
                Configurator.setRootLevel(Level.FATAL);
                break;
            default: {
                Configurator.setRootLevel(Level.INFO);
                LoggerFactory.getLogger(LoggingUtil.class).warn("Invalid logging level: {}", level);
            }
        }
    }

}
