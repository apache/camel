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
package org.apache.camel.component.thrift.impl;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.generated.InvalidOperation;
import org.apache.camel.component.thrift.generated.Work;
import org.apache.thrift.TException;

/**
 * Test Thrift Calculator blocking server implementation
 */
public class CalculatorSyncServerImpl implements Calculator.Iface {

    @Override
    public void ping() throws TException {
    }

    @Override
    public int add(int num1, int num2) throws TException {
        return num1 + num2;
    }

    @Override
    public int calculate(int logId, Work work) throws InvalidOperation, TException {
        int val = 0;
        switch (work.op) {
            case ADD:
                val = work.num1 + work.num2;
                break;
            case SUBTRACT:
                val = work.num1 - work.num2;
                break;
            case MULTIPLY:
                val = work.num1 * work.num2;
                break;
            case DIVIDE:
                if (work.num2 == 0) {
                    InvalidOperation io = new InvalidOperation();
                    io.whatOp = work.op.getValue();
                    io.why = "Cannot divide by 0";
                    throw io;
                }
                val = work.num1 / work.num2;
                break;
            default:
                InvalidOperation io = new InvalidOperation();
                io.whatOp = work.op.getValue();
                io.why = "Unknown operation";
                throw io;
        }

        return val;
    }

    @Override
    public void zip() throws TException {
    }

    @Override
    public Work echo(Work w) throws TException {
        return w.deepCopy();
    }

    @Override
    public int alltypes(boolean v1, byte v2, short v3, int v4, long v5, double v6, String v7, ByteBuffer v8, Work v9, List<Integer> v10, Set<String> v11, Map<String, Long> v12)
            throws TException {
        return 1;
    }
}
