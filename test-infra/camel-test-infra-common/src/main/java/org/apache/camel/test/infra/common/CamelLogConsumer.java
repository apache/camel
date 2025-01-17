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
package org.apache.camel.test.infra.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.testcontainers.containers.output.BaseConsumer;
import org.testcontainers.containers.output.OutputFrame;

public class CamelLogConsumer extends BaseConsumer<CamelLogConsumer> {

    private final Path logFile;
    private final boolean logToStdout;

    public CamelLogConsumer(Path logFile, boolean logToStdout) {
        this.logFile = logFile;
        this.logToStdout = logToStdout;
    }

    @Override
    public void accept(OutputFrame outputFrame) {
        final OutputFrame.OutputType outputType = outputFrame.getType();
        final String utf8String = outputFrame.getUtf8StringWithoutLineEnding();

        switch (outputType) {
            case END:
                break;
            case STDOUT:
                if (logToStdout) {
                    System.out.println(utf8String);
                }
                logToFile(utf8String);
                break;
            case STDERR:
                if (logToStdout) {
                    System.err.println(utf8String);
                }
                logToFile(utf8String);
                break;
            default:
                throw new IllegalArgumentException("Unexpected outputType " + outputType);
        }
    }

    private void logToFile(String utf8String) {
        try {
            Files.write(logFile, utf8String.getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            // It is just a container log, do nothing
        }
    }
}
