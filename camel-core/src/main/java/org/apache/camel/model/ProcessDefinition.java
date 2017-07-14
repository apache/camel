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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.apache.camel.processor.DelegateSyncProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;

/**
 * Calls a Camel processor
 *
 * @version 
 */
@Metadata(label = "eip,endpoint")
@XmlRootElement(name = "process")
@XmlAccessorType(XmlAccessType.FIELD)
public class ProcessDefinition extends NoOutputDefinition<ProcessDefinition> {
    @XmlAttribute(required = true)
    private String ref;
    @XmlTransient
    private Processor processor;

    public ProcessDefinition() {
    }

    public ProcessDefinition(Processor processor) {
        this.processor = processor;
    }

    @Override
    public String toString() {
        if (ref != null) {
            return "process[ref:" + ref + "]";
        } else {
            // do not invoke toString on the processor as we do not know what it would do
            String id = ObjectHelper.getIdentityHashCode(processor);
            return "process[Processor@" + id + "]";
        }
    }

    @Override
    public String getLabel() {
        if (ref != null) {
            return "ref:" + ref;
        } else if (processor != null) {
            // do not invoke toString on the processor as we do not know what it would do
            String id = ObjectHelper.getIdentityHashCode(processor);
            return "Processor@" + id;
        } else {
            return "";
        }
    }

    public String getRef() {
        return ref;
    }

    /**
     * Reference to the {@link Processor} to lookup in the registry to use.
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) {
        Processor answer = processor;
        if (processor == null) {
            ObjectHelper.notNull(ref, "ref", this);
            answer = routeContext.mandatoryLookup(getRef(), Processor.class);
        }

        // ensure its wrapped in a Service so we can manage it from eg. JMX
        // (a Processor must be a Service to be enlisted in JMX)
        if (!(answer instanceof Service)) {
            if (answer instanceof AsyncProcessor) {
                // the processor is async by nature so use the async delegate
                answer = new DelegateAsyncProcessor(answer);
            } else {
                // the processor is sync by nature so use the sync delegate
                answer = new DelegateSyncProcessor(answer);
            }
        }
        return answer;
    }
}
