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
package org.apache.camel.dsl.jbang.core.commands.action;

import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.TimeoutConverter;
import picocli.CommandLine;

/**
 * The {@code --timeout} of a command that waits for a running integration to answer (see
 * {@link ActionBaseCommand#timeout()}).
 */
public class ActionTimeoutMixin {

    @CommandLine.Option(names = { "--timeout" }, defaultValue = "10000", converter = TimeoutConverter.class,
                        description = "How long to wait for the integration to answer, in millis or as a duration such as 30s")
    long timeout = ActionClient.DEFAULT_TIMEOUT;
}
