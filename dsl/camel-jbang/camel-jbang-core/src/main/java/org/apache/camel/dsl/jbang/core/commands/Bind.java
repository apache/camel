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

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

import org.apache.camel.github.GitHubResourceResolver;
import org.apache.camel.impl.engine.DefaultResourceResolvers;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceResolver;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.YamlUnicodeReader;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.parser.Parser;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asStringSet;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.nodeAt;

@Command(name = "bind", description = "Bind source and sink Kamelets as a new Camel integration",
         sortOptions = false)
public class Bind extends CamelCommand {

    @CommandLine.Parameters(description = "Name of binding file to be saved", arity = "1",
                            paramLabel = "<file>", parameterConsumer = FileConsumer.class)
    Path filePath; // Defined only for file path completion; the field never used
    String file;

    @CommandLine.Option(names = { "--source" }, description = "Source (from) such as a Kamelet or Camel endpoint uri",
                        required = true)
    String source;

    @CommandLine.Option(names = { "--step" }, description = "Optional steps such as a Kamelet or Camel endpoint uri")
    String[] steps;

    @CommandLine.Option(names = { "--sink" }, description = "Sink (to) such as a Kamelet or Camel endpoint uri",
                        required = true)
    String sink;

    public Bind(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {

        // the pipe source and sink can either be a kamelet or an uri
        String in = "kamelet";
        String out = "kamelet";
        if (source.contains(":")) {
            in = "uri";
        }
        if (sink.contains(":")) {
            out = "uri";
        }

        InputStream is = Bind.class.getClassLoader().getResourceAsStream("templates/pipe-" + in + "-" + out + ".yaml.tmpl");
        String context = IOHelper.loadText(is);
        IOHelper.close(is);

        String stepsContext = "";
        if (steps != null) {
            StringBuilder sb = new StringBuilder("\n  steps:\n");
            for (String step : steps) {
                boolean uri = step.contains(":");
                String text;
                if (uri) {
                    is = Bind.class.getClassLoader().getResourceAsStream("templates/step-uri.yaml.tmpl");
                    text = IOHelper.loadText(is);
                    IOHelper.close(is);
                    text = text.replaceFirst("\\{\\{ \\.Name }}", step);
                } else {
                    is = Bind.class.getClassLoader().getResourceAsStream("templates/step-kamelet.yaml.tmpl");
                    text = IOHelper.loadText(is);
                    IOHelper.close(is);
                    text = text.replaceFirst("\\{\\{ \\.Name }}", step);
                    String props = kameletProperties(step);
                    text = text.replaceFirst("\\{\\{ \\.StepProperties }}", props);
                }
                sb.append(text);
            }
            stepsContext = sb.toString();
        }

        String name = FileUtil.onlyName(file, false);
        context = context.replaceFirst("\\{\\{ \\.Name }}", name);
        context = context.replaceFirst("\\{\\{ \\.Source }}", source);
        context = context.replaceFirst("\\{\\{ \\.Sink }}", sink);
        context = context.replaceFirst("\\{\\{ \\.Steps }}", stepsContext);

        if ("kamelet".equals(in)) {
            String props = kameletProperties(source);
            context = context.replaceFirst("\\{\\{ \\.SourceProperties }}", props);
        }
        if ("kamelet".equals(out)) {
            String props = kameletProperties(sink);
            context = context.replaceFirst("\\{\\{ \\.SinkProperties }}", props);
        }

        IOHelper.writeText(context, new FileOutputStream(file, false));
        return 0;
    }

    protected String kameletProperties(String kamelet) throws Exception {
        StringBuilder sb = new StringBuilder();

        InputStream is;
        String loc;
        Resource res;

        // try local disk first before github
        ResourceResolver resolver = new DefaultResourceResolvers.FileResolver();
        try {
            res = resolver.resolve("file:" + kamelet + ".kamelet.yaml");
        } finally {
            resolver.close();
        }
        if (res.exists()) {
            is = res.getInputStream();
            loc = res.getLocation();
        } else {
            resolver = new GitHubResourceResolver();
            try {
                res = resolver.resolve(
                        "github:apache:camel-kamelets:main:kamelets/" + kamelet + ".kamelet.yaml");
            } finally {
                resolver.close();
            }
            loc = res.getLocation();
            URL u = new URL(loc);
            is = u.openStream();
        }
        if (is != null) {
            try {
                LoadSettings local = LoadSettings.builder().setLabel(loc).build();
                final StreamReader reader = new StreamReader(local, new YamlUnicodeReader(is));
                final Parser parser = new ParserImpl(local, reader);
                final Composer composer = new Composer(local, parser);
                Node root = composer.getSingleNode().orElse(null);
                if (root != null) {
                    Set<String> required = asStringSet(nodeAt(root, "/spec/definition/required"));
                    if (required != null && !required.isEmpty()) {
                        sb.append("properties:\n");
                        Iterator<String> it = required.iterator();
                        while (it.hasNext()) {
                            String req = it.next();
                            String type = asText(nodeAt(root, "/spec/definition/properties/" + req + "/type"));
                            String example = asText(nodeAt(root, "/spec/definition/properties/" + req + "/example"));
                            sb.append("      ").append(req).append(": ");
                            if (example != null) {
                                if ("string".equals(type)) {
                                    sb.append("\"");
                                }
                                sb.append(example);
                                if ("string".equals(type)) {
                                    sb.append("\"");
                                }
                            } else {
                                sb.append("\"value\"");
                            }
                            if (it.hasNext()) {
                                sb.append("\n");
                            }
                        }
                    }
                }
                IOHelper.close(is);
            } catch (Exception e) {
                System.err.println("Error parsing Kamelet: " + loc + " due to: " + e.getMessage());
            }
        } else {
            System.err.println("Kamelet not found on github: " + kamelet);
        }

        // create a dummy placeholder, so it is easier to add new properties manually
        if (sb.isEmpty()) {
            sb.append("#properties:\n      #key: \"value\"");
        }

        return sb.toString();
    }

    static class FileConsumer extends ParameterConsumer<Bind> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Bind cmd) {
            cmd.file = args.pop();
        }
    }

}
