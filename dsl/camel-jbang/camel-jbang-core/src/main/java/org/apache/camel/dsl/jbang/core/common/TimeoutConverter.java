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
package org.apache.camel.dsl.jbang.core.common;

import org.apache.camel.util.TimeUtils;
import picocli.CommandLine;

/**
 * Converts the <tt>--timeout</tt> option of the commands that talk to a running integration, to millis: a number is
 * millis (<tt>20000</tt>), and a duration has units (<tt>30s</tt>, <tt>1m30s</tt>).
 */
public class TimeoutConverter implements CommandLine.ITypeConverter<Long> {

    @Override
    public Long convert(String value) {
        try {
            return TimeUtils.toMilliSeconds(value.trim());
        } catch (RuntimeException e) {
            throw new CommandLine.TypeConversionException(
                    "'" + value + "' is not a timeout: use millis, or a duration such as 30s");
        }
    }
}
