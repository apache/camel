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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.model.Model;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.ModelDumpLine;
import org.apache.camel.spi.ModelToStructureDumper;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.util.StringHelper;

@JdkService(ModelToStructureDumper.FACTORY)
public class DefaultModelToStructureDumper implements ModelToStructureDumper {

    @Override
    public List<ModelDumpLine> dumpStructure(CamelContext context, String routeId, boolean brief) throws Exception {
        // dump in text format padded by level
        List<ModelDumpLine> answer = new ArrayList<>();

        // lookup model and runtime route
        final Model model = context.getCamelContextExtension().getContextPlugin(Model.class);
        final RouteDefinition def = model.getRouteDefinition(routeId);
        String scheme = def.getResource() != null ? def.getResource().getScheme() : "file";

        String loc = scheme + ":" + LoggerHelper.getLineNumberLoggerName(def);
        answer.add(
                new ModelDumpLine(loc, "route", def.getRouteId(), 0, "route[" + def.getRouteId() + "]", def.getDescription()));
        String uri = def.getInput().getLabel();
        if (brief) {
            uri = StringHelper.before(uri, "?", uri);
        }
        answer.add(new ModelDumpLine(loc, "from", routeId, 1, "from[" + uri + "]", def.getDescription()));

        var outputs = ProcessorDefinitionHelper.filterTypeInOutputs(def.getOutputs(), OptionalIdentifiedDefinition.class);
        for (var output : outputs) {
            loc = scheme + ":" + output.getLocation();
            if (output.getLineNumber() > 0) {
                loc += ":" + output.getLineNumber();
            }
            String kind = output.getShortName();
            String id = output.getId();
            int level = getLevel(output) + 1;
            boolean choice = "choice".equals(output.getShortName());
            String code = choice || brief ? output.getShortName() : output.getLabel();
            String desc = output.getDescription();
            answer.add(new ModelDumpLine(loc, kind, id, level, code, desc));
        }

        return answer;
    }

    private static int getLevel(NamedNode node) {
        int level = 0;
        while (node != null && node.getParent() != null) {
            // special for choice
            boolean choice = "choice".equals(node.getParent().getShortName());
            if (choice) {
                level++;
            }
            boolean shallow = "when".equals(node.getShortName()) || "otherwise".equals(node.getShortName());
            if (!shallow) {
                level++;
            }
            node = node.getParent();
        }
        return level;
    }

}
