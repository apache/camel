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
package org.apache.camel.component.snakeyaml.custom;

import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.constructor.ConstructorException;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;

/**
 * A CustomSafeConstructor which picks up the options to disallow recursive keys
 *
 * NOTE - If this PR gets applied then we can remove it:
 * https://bitbucket.org/asomov/snakeyaml/pull-requests/55/allow-configuration-for-preventing-billion/diff
 */
public class CustomSafeConstructor extends SafeConstructor {

    private boolean allowRecursiveKeys;

    @Override
    protected void constructMapping2ndStep(MappingNode node, Map<Object, Object> mapping) {
        List<NodeTuple> nodeValue = node.getValue();
        for (NodeTuple tuple : nodeValue) {
            Node keyNode = tuple.getKeyNode();
            Node valueNode = tuple.getValueNode();
            Object key = constructObject(keyNode);
            if (key != null) {
                try {
                    key.hashCode(); // check circular dependencies
                } catch (Exception e) {
                    throw new CustomConstructorException("while constructing a mapping",
                            node.getStartMark(), "found unacceptable key " + key,
                            tuple.getKeyNode().getStartMark(), e);
                }
            }
            Object value = constructObject(valueNode);
            if (keyNode.isTwoStepsConstruction()) {
                if (allowRecursiveKeys) {
                    postponeMapFilling(mapping, key, value);
                } else {
                    throw new YAMLException("Recursive key for mapping is detected but it is not configured to be allowed.");
                }
            } else {
                mapping.put(key, value);
            }
        }
    }

    public boolean isAllowRecursiveKeys() {
        return allowRecursiveKeys;
    }

    public void setAllowRecursiveKeys(boolean allowRecursiveKeys) {
        this.allowRecursiveKeys = allowRecursiveKeys;
    }

    private static class CustomConstructorException extends ConstructorException {
        public CustomConstructorException(String context, Mark contextMark, String problem,
                                       Mark problemMark, Throwable cause) {
            super(context, contextMark, problem, problemMark, cause);
        }
    }
}