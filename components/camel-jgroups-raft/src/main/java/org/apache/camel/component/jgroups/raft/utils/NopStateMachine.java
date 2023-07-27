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
package org.apache.camel.component.jgroups.raft.utils;

import java.io.DataInput;
import java.io.DataOutput;

import org.jgroups.raft.StateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of JGroups-raft state machine ({@code org.jgroups.protocols.raft.StateMachine}) that dose nothing.
 */
public class NopStateMachine implements StateMachine {
    private static final transient Logger LOG = LoggerFactory.getLogger(NopStateMachine.class);

    @Override
    public byte[] apply(byte[] data, int offset, int length, boolean serialize_response) throws Exception {
        LOG.trace("Called StateMachine.apply(byte[] {}, int {}, int {}) on {}", data, offset, length, this);
        return new byte[0];
    }

    @Override
    public void readContentFrom(DataInput dataInput) throws Exception {
        LOG.trace("Called StateMachine.readContentFrom(DataInput {}) on {}", dataInput, this);
    }

    @Override
    public void writeContentTo(DataOutput dataOutput) throws Exception {
        LOG.trace("Called StateMachine.readContentFrom(DataOutput {}) on {}", dataOutput, this);
    }
}
