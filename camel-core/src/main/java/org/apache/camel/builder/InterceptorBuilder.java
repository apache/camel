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
package org.apache.camel.builder;

import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.processor.DelegateProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * @version $Revision: 519943 $
 */
public class InterceptorBuilder implements ProcessorFactory {
    private final List<DelegateProcessor> intercepts = new ArrayList<DelegateProcessor>();
    private final FromBuilder parent;
    private FromBuilder target;

    public InterceptorBuilder(FromBuilder parent) {
        this.parent = parent;
    }

    public InterceptorBuilder add(DelegateProcessor interceptor) {
        intercepts.add(interceptor);
        return this;
    }

    public FromBuilder target() {
        this.target = new FromBuilder(parent);
        return target;
    }

    public Processor createProcessor() throws Exception {

        // The target is required.
        if (target == null)
            throw new RuntimeCamelException("target provided.");

        // Interceptors are optional
        DelegateProcessor first = null;
        DelegateProcessor last = null;
        for (DelegateProcessor p : intercepts) {
            if (first == null) {
                first = p;
            }
            if (last != null) {
                last.setProcessor(p);
            }
            last = p;
        }

        Processor p = target.createProcessor();
        if (last != null) {
            last.setProcessor(p);
        }
        return first == null ? p : first;
    }
}
