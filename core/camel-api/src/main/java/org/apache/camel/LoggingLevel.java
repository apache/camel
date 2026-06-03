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
package org.apache.camel;

import jakarta.xml.bind.annotation.XmlEnum;

/**
 * Logging severity levels used throughout Camel to control the verbosity of diagnostic output.
 * <p/>
 * This enum is used wherever Camel accepts a configurable log level: the
 * <a href="https://camel.apache.org/manual/log-eip.html">Log EIP</a>, the {@link org.apache.camel.spi.CamelLogger},
 * error-handler redelivery logging, tracing, the dead-letter channel, and many component-level options. It maps
 * directly to the SLF4J levels of the same name. {@link #OFF} disables the specific log statement without changing the
 * logger's overall threshold.
 */
@XmlEnum
public enum LoggingLevel {

    /** Most detailed logging level. */
    TRACE,
    /** Detailed information useful during development. */
    DEBUG,
    /** Informational messages. */
    INFO,
    /** Warning messages. */
    WARN,
    /** Error messages. */
    ERROR,
    /** Disables logging entirely. */
    OFF;

    /**
     * Is the given logging level equal or higher than the current level.
     */
    public boolean isEnabled(LoggingLevel level) {
        // off is always false
        if (this == OFF || level == OFF) {
            return false;
        }

        return this.compareTo(level) <= 0;
    }
}
