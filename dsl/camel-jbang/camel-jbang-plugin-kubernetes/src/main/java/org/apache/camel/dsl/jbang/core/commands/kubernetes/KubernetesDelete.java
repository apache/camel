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

package org.apache.camel.dsl.jbang.core.commands.kubernetes;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import io.fabric8.kubernetes.api.model.StatusDetails;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.SourceScheme;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "delete", description = "Delete Camel application from Kubernetes", sortOptions = false)
public class KubernetesDelete extends KubernetesBaseCommand {

    @CommandLine.Parameters(description = "The Camel file to delete. Integration name is derived from the file name.",
                            arity = "0..1", paramLabel = "<file>")
    String filePath;

    @CommandLine.Option(names = { "--name" },
                        description = "The integration name. Use this when the name should not get derived from the source file name.")
    String name;

    @CommandLine.Option(names = { "--working-dir" },
                        description = "The working directory where to find exported project sources.")
    String workingDir;

    @CommandLine.Option(names = { "--cluster-type" },
                        description = "The target cluster type. Special configurations may be applied to different cluster types such as Kind or Minikube or Openshift.")
    protected String clusterType;

    public KubernetesDelete(CamelJBangMain main) {
        super(main);
    }

    public Integer doCall() throws Exception {
        File resolvedWorkingDir;
        if (workingDir != null) {
            resolvedWorkingDir = new File(workingDir);
        } else {
            String projectName;
            if (name != null) {
                projectName = KubernetesHelper.sanitize(name);
            } else if (filePath != null) {
                projectName = KubernetesHelper.sanitize(FileUtil.onlyName(SourceScheme.onlyName(filePath)));
            } else {
                printer().println("Name or source file must be set");
                return 1;
            }

            resolvedWorkingDir = new File(RUN_PLATFORM_DIR + "/" + projectName);
        }

        if (!resolvedWorkingDir.exists()) {
            printer().printf("Failed to resolve exported project from path '%s'%n", resolvedWorkingDir);
            return 1;
        }

        File resolvedManifestDir = new File(resolvedWorkingDir, "target/kubernetes");
        File manifest = KubernetesHelper.resolveKubernetesManifest(clusterType, resolvedManifestDir);
        try (FileInputStream fis = new FileInputStream(manifest)) {
            List<StatusDetails> status;
            if (!ObjectHelper.isEmpty(namespace)) {
                status = client().load(fis).inNamespace(namespace).delete();
            } else {
                status = client().load(fis).delete();
            }

            status.forEach(s -> printer().printf("Deleted: %s '%s'%n", StringHelper.capitalize(s.getKind()), s.getName()));
        }

        return 0;
    }
}
