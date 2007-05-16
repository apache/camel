/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import org.apache.camel.Processor;
import org.apache.camel.Service;

/**
 * A simple helper class which allows you to attach an artbirary service to a processor which is
 * started before the processor and closed after it
 *
 * @version $Revision: 1.1 $
 */
public class LifecycleProcessor extends DelegateProcessor {
    private Service service;

    public LifecycleProcessor(Processor next, Service service) {
        super(next);
        this.service = service;
    }

    @Override
    protected void doStart() throws Exception {
        service.start();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        service.stop();
    }
}
