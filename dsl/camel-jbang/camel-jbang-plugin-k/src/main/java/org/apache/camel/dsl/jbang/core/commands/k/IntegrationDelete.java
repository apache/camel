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

import java.util.Arrays;
import java.util.List;

import io.fabric8.kubernetes.api.model.StatusDetails;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.v1.Integration;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "delete", description = "Delete integrations deployed on Kubernetes", sortOptions = false)
public class IntegrationDelete extends KubeBaseCommand {

    @CommandLine.Parameters(description = "Integration names to delete.",
                            arity = "0..*", paramLabel = "<names>")
    String[] names;

    @CommandLine.Option(names = { "--all" },
                        description = "Delete all integrations in current namespace.")
    boolean all;

    public IntegrationDelete(CamelJBangMain main) {
        super(main);
    }

    public Integer doCall() throws Exception {
        if (all) {
            client(Integration.class).delete();
            printer().println("Integrations deleted");
        } else {
            if (names == null) {
                throw new RuntimeCamelException("Missing integration name as argument or --all option.");
            }

            for (String name : Arrays.stream(names).map(KubernetesHelper::sanitize).toList()) {
                List<StatusDetails> status = client(Integration.class).withName(name).delete();
                if (status.isEmpty()) {
                    printer().printf("Integration %s deletion skipped - not found%n", name);
                } else {
                    printer().printf("Integration %s deleted%n", name);
                }
            }
        }

        return 0;
    }

}
