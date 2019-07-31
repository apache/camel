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
package org.apache.camel.component.exec.impl;

import org.apache.camel.component.exec.ExecCommand;
import org.apache.camel.component.exec.ExecDefaultExecutor;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;

/**
 * Mock of {@link org.apache.camel.component.exec.ExecCommandExecutor} which provokes to throw an
 * {@link org.apache.camel.component.exec.ExecException}
 */
public class ProvokeExceptionExecCommandExecutor extends DefaultExecCommandExecutor {

    @Override
    protected DefaultExecutor prepareDefaultExecutor(ExecCommand execCommand) {
        DefaultExecutor executor = new DefaultExecutorMock();
        executor.setExitValues(null);
        executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());

        return executor;
    }

    public class DefaultExecutorMock extends ExecDefaultExecutor {

        @Override
        public boolean isFailure(int exitValue) {
            //provoke ExecuteException
            return true;
        }
    }
}