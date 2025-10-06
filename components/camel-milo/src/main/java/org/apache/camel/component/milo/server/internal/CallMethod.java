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

package org.apache.camel.component.milo.server.internal;

import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.api.methods.AbstractMethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.structured.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallMethod extends AbstractMethodInvocationHandler {

    public static final Argument IN = new Argument(
            "in",
            Identifiers.String,
            ValueRanks.Scalar,
            null,
            new LocalizedText("A value."));

    public static final Argument OUT = new Argument(
            "out",
            Identifiers.String,
            ValueRanks.Scalar,
            null,
            new LocalizedText("A value."));

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public CallMethod(UaMethodNode node) {
        super(node);
    }

    @Override
    public Argument[] getInputArguments() {
        return new Argument[] { IN };
    }

    @Override
    public Argument[] getOutputArguments() {
        return new Argument[] { OUT };
    }

    @Override
    protected Variant[] invoke(InvocationContext invocationContext, Variant[] inputValues) {
        logger.debug("Invoking sqrt() method of objectId={}", invocationContext.getObjectId());

        String in = (String) inputValues[0].getValue();

        return new Variant[] { new Variant("out-" + in) };
    }

}
