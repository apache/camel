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

package org.apache.camel.dsl.jbang.core.commands.test;

import java.util.List;

import org.aesh.command.Command;
import org.aesh.command.invocation.CommandInvocation;
import org.citrusframework.jbang.cli.CitrusJBangMain;
import org.citrusframework.jbang.cli.commands.Agent;
import org.citrusframework.jbang.cli.commands.AgentRun;
import org.citrusframework.jbang.cli.commands.AgentStop;

/**
 * Overrides the Citrus {@link Agent} group command so that the {@code start} subcommand dispatches to
 * {@link TestAgentStart} instead of the plain Citrus {@code AgentStart}. The Citrus {@code Agent} implements
 * {@code GroupCommand} and its {@code getCommands()} returns plain Citrus command instances, which aesh uses for
 * dispatch — bypassing subcommands registered via the Camel {@code CommandTree}.
 */
public class TestAgent extends Agent {

    public TestAgent(CitrusJBangMain main) {
        super(main);
    }

    @Override
    public List<Command<CommandInvocation>> getCommands() {
        return List.of(
                new TestAgentStart(getMain()),
                new AgentRun(getMain()),
                new AgentStop(getMain()));
    }
}
