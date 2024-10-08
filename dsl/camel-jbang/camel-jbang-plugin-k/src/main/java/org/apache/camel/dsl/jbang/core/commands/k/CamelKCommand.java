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
package org.apache.camel.dsl.jbang.core.commands.k;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.kubernetes.KubernetesBaseCommand;
import picocli.CommandLine;

@Deprecated
@CommandLine.Command(name = "k",
                     description = "DEPRECATED: Manage Camel integrations on Kubernetes (use k --help to see sub commands)")
public class CamelKCommand extends KubernetesBaseCommand {

    static final String OPERATOR_ID_LABEL = "camel.apache.org/operator.id";
    static final String INTEGRATION_LABEL = "camel.apache.org/integration";
    static final String INTEGRATION_CONTAINER_NAME = "integration";

    public static final String INTEGRATION_PROFILE_ANNOTATION = "camel.apache.org/integration-profile.id";
    public static final String INTEGRATION_PROFILE_NAMESPACE_ANNOTATION = "camel.apache.org/integration-profile.namespace";

    public CamelKCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // defaults to list integrations deployed on Kubernetes
        new CommandLine(new IntegrationGet(getMain())).execute();
        return 0;
    }
}
