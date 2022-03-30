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
package org.apache.camel.dsl.yaml.common.exception;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;

public class DuplicateKeyException extends MarkedYamlEngineException {

    public DuplicateKeyException(Node node, List<NodeTuple> nodes) {
        super(null, Optional.empty(), "Node should have only have 1 key, was: " + nodes.size() + " keys: [" + keyNames(nodes)
                                      + "] (Maybe this is an indent problem in the YAML source).",
              node.getStartMark());
    }

    private static String keyNames(List<NodeTuple> nodes) {
        StringJoiner sj = new StringJoiner(",");
        for (NodeTuple node : nodes) {
            Node key = node.getKeyNode();
            sj.add(asText(key));
        }
        return sj.toString();
    }

}
