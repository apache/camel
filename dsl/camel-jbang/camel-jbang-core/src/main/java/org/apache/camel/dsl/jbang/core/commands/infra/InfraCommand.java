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
package org.apache.camel.dsl.jbang.core.commands.infra;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import picocli.CommandLine;

@CommandLine.Command(name = "infra",
                     description = "List and Run external services for testing and prototyping")
public class InfraCommand extends CamelCommand {

    static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    {
        JSON_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        JSON_MAPPER.registerModule(new JavaTimeModule());
        JSON_MAPPER.registerModule(new Jdk8Module());
        JSON_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public InfraCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // defaults to list
        new CommandLine(new InfraList(getMain())).execute();
        return 0;
    }

    record TestInfraService(String service, String implementation, List<String> alias, List<String> aliasImplementation) {
    }
}
