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
package org.apache.camel.processor;

import org.apache.camel.DisabledAware;
import org.apache.camel.Processor;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

/**
 * Base class for EIP implementations as {@link org.apache.camel.Processor}.
 */
public abstract class BaseDelegateProcessorSupport extends DelegateAsyncProcessor implements DisabledAware {

    public BaseDelegateProcessorSupport(Processor processor) {
        super(processor);
    }

    private boolean disabled;

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
}
