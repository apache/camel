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
package org.apache.camel.component.exec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.component.exec.impl.DefaultExecBinding;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class DefaultExecBindingTest extends CamelTestSupport {
   
    @Test     
    public void testReadInput() throws Exception {
        ExecCommand command = readInput("exec:test", Collections.EMPTY_LIST);
        Assert.assertEquals("Get a wrong args.", Collections.EMPTY_LIST, command.getArgs());
        List<String> args = Arrays.asList("arg1", "arg2");
        command = readInput("exec:test", args);
        assertEquals("Get a wrong args.", args, command.getArgs());
        
        command = readInput("exec:test", "arg1 arg2");
        assertEquals("Get a wrong args.", args, command.getArgs());
        
        command = readInput("exec:test?args=arg1 arg2", null);
        assertEquals("Get a wrong args.", args, command.getArgs());
    }
    
    private ExecCommand readInput(String execEndpointUri, Object args) throws Exception {
        DefaultExecBinding binding = new DefaultExecBinding();
        ExecEndpoint execEndpoint = createExecEndpoint(execEndpointUri);
        Exchange exchange = execEndpoint.createExchange();
        exchange.getIn().setHeader(ExecBinding.EXEC_COMMAND_ARGS, args);
        return binding.readInput(exchange, execEndpoint);
    }
    
    private ExecEndpoint createExecEndpoint(String uri) throws Exception {
        Component component = context.getComponent("exec");
        return (ExecEndpoint)component.createEndpoint(uri);
    }

}
