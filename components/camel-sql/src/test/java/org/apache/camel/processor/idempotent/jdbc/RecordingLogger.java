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
package org.apache.camel.processor.idempotent.jdbc;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;

/**
 * A logger for the tests of the repositories: it records the format of the WARN, ERROR and INFO messages, and drops the
 * others.
 */
final class RecordingLogger {

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final List<String> infos = new CopyOnWriteArrayList<>();
    private final Logger logger = (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(),
            new Class<?>[] { Logger.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    case "toString":
                        return "recordingLogger";
                    case "warn":
                        record(warnings, args);
                        return null;
                    case "error":
                        record(errors, args);
                        return null;
                    case "info":
                        record(infos, args);
                        return null;
                    default:
                        break;
                }
                if (method.getReturnType() == boolean.class) {
                    return false;
                }
                if (method.getReturnType() == String.class) {
                    return "test";
                }
                return null;
            });

    Logger logger() {
        return logger;
    }

    List<String> warnings() {
        return warnings;
    }

    List<String> errors() {
        return errors;
    }

    List<String> infos() {
        return infos;
    }

    private static void record(List<String> messages, Object[] args) {
        if (args != null && args.length > 0) {
            messages.add(String.valueOf(args[0]));
        }
    }
}
