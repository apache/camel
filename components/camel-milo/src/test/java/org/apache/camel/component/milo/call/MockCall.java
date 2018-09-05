/**
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

package org.apache.camel.component.milo.call;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.milo.opcua.sdk.server.annotations.UaInputArgument;
import org.eclipse.milo.opcua.sdk.server.annotations.UaMethod;
import org.eclipse.milo.opcua.sdk.server.annotations.UaOutputArgument;
import org.eclipse.milo.opcua.sdk.server.api.ServerNodeMap;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.util.AnnotationBasedInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.util.AnnotationBasedInvocationHandler.InvocationContext;
import org.eclipse.milo.opcua.sdk.server.util.AnnotationBasedInvocationHandler.Out;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

import static org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText.english;

public final class MockCall {

    private MockCall() {
    }

    // in: str1[String], out: out1[String]
    public static class Call1 {

        public List<String> calls = new LinkedList<>();

        @UaMethod
        public void call(final InvocationContext context, @UaInputArgument(name = "in1")
        final String in1, @UaOutputArgument(name = "out1")
        final Out<String> out1) {
            this.calls.add(in1);
            out1.set("out-" + in1);
        }
    }

    public static UaMethodNode fromNode(final UShort index, final ServerNodeMap nodeMap, final String nodeId, final String name, final Object methodObject) {

        try {
            final UaMethodNode method = new UaMethodNode(nodeMap, new NodeId(index, nodeId), new QualifiedName(index, name), english(name), english(nodeId), UInteger.MIN,
                                                         UInteger.MIN, true, true);

            final AnnotationBasedInvocationHandler handler = AnnotationBasedInvocationHandler.fromAnnotatedObject(nodeMap, methodObject);
            method.setInputArguments(handler.getInputArguments());
            method.setOutputArguments(handler.getOutputArguments());
            method.setInvocationHandler(handler);
            return method;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
