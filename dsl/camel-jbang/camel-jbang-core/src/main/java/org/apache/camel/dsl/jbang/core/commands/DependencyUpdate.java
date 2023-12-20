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

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.tooling.maven.MavenGav;
import org.apache.camel.util.IOHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "update",
                     description = "Updates JBang style dependencies in source file")
public class DependencyUpdate extends DependencyList {

    @CommandLine.Option(names = { "--source" },
                        description = "Camel source such as .java file to have dependencies updated (//DEPS)",
                        required = true)
    protected String source;

    @CommandLine.Option(names = { "--clean" },
                        description = "Regenerate list of dependencies (do not keep existing dependencies)")
    protected boolean clean;

    private final List<String> deps = new ArrayList<>();
    private File target;

    public DependencyUpdate(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // source file must exists
        target = new File(source);
        if (!target.exists()) {
            System.err.println("Source file does not exist: " + target);
            return -1;
        }

        if (clean) {
            // remove DEPS in source file first
            updateSource();
        }

        return super.doCall();
    }

    @Override
    protected void outputGav(MavenGav gav, int index, int total) {
        if (index == 0) {
            deps.add("//DEPS org.apache.camel:camel-bom:" + gav.getVersion() + "@pom");
        }
        if (gav.getGroupId().equals("org.apache.camel")) {
            // jbang has version in @pom so we should remove this
            gav.setVersion(null);
        }
        String line = "//DEPS " + gav;
        if (!deps.contains(line)) {
            deps.add(line);
        }
        boolean last = total - index <= 1;
        if (last) {
            updateSource();
        }
    }

    private void updateSource() {
        try {
            List<String> lines = Files.readAllLines(target.toPath());
            List<String> answer = new ArrayList<>();

            // find position of where the old DEPS was
            int pos = -1;
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                if (l.trim().startsWith("//DEPS ")) {
                    if (pos == -1) {
                        pos = i;
                    }
                } else {
                    answer.add(l);
                }
            }
            // add after shebang in top
            if (pos == -1) {
                if (answer.get(0).trim().startsWith("///usr/bin/env jbang")) {
                    pos = 1;
                }
            }
            if (pos == -1) {
                pos = 0;
            }

            // reverse collection as we insert pos based
            Collections.reverse(deps);
            for (String dep : deps) {
                answer.add(pos, dep);
            }

            String text = String.join(System.lineSeparator(), answer);
            IOHelper.writeText(text, target);
        } catch (Exception e) {
            System.err.println("Error updating source file: " + target + " due to: " + e.getMessage());
        }
    }

}
