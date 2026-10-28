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
package org.apache.camel.dsl.jbang.core.commands;

import org.apache.camel.dsl.jbang.core.common.TimeoutConverter;
import picocli.CommandLine;

/**
 * The {@code --duration} of a command that watches or follows: how long to keep going. Without it, the command keeps
 * going until Enter is pressed, and without an interactive terminal it shows its output once.
 */
public class DurationMixin {

    @CommandLine.Option(names = { "--duration" }, converter = TimeoutConverter.class,
                        description = "How long to keep watching or following, in millis or as a duration such as 30s"
                                      + " (default: until Enter is pressed)")
    long duration;

    /**
     * How long to keep going, in millis, or 0 to keep going until Enter is pressed.
     */
    public long getDuration() {
        return duration;
    }
}
