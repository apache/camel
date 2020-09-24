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
package org.apache.camel.maven.packaging;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.BaseOptionModel;

public final class EndpointUriAssemblerGenerator {

    private EndpointUriAssemblerGenerator() {
    }

    public static void generateEndpointUriAssembler(
            String pn, String cn, String en,
            String pfqn, String psn,
            Collection<? extends BaseOptionModel> options, Writer w)
            throws IOException {

        w.write("/* " + AbstractGeneratorMojo.GENERATED_MSG + " */\n");
        w.write("package " + pn + ";\n");
        w.write("\n");
        w.write("import java.net.URISyntaxException;\n");
        w.write("import java.util.Map;\n");
        w.write("\n");
        w.write("import org.apache.camel.CamelContext;\n");
        w.write("import org.apache.camel.spi.EndpointUriAssembler;\n");
        w.write("import " + pfqn + ";\n");
        w.write("\n");
        w.write("/**\n");
        w.write(" * " + AbstractGeneratorMojo.GENERATED_MSG + "\n");
        w.write(" */\n");
        w.write("@SuppressWarnings(\"unchecked\")\n");
        w.write("public class " + cn + " extends " + psn
                + " implements EndpointUriAssembler {\n");

        // sort options A..Z so they always have same order
        options = options.stream().sorted(Comparator.comparing(BaseOptionModel::getName)).collect(Collectors.toList());

        // generate API that returns all the options
        w.write("\n");
        w.write("    @Override\n");
        w.write("    public String buildUri(CamelContext camelContext, String scheme, Map<String, String> parameters) throws URISyntaxException {\n");
        w.write("        return null;\n");
        w.write("    }\n");

        w.write("}\n");
        w.write("\n");
    }

}
