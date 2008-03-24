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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A useful base class for output types
 *
 * @version $Revision$
 */
@XmlType(name = "outputType")
@XmlAccessorType(XmlAccessType.FIELD)
public class OutputType<Type extends ProcessorType> extends ProcessorType<Type> {
    private static final transient Log LOG = LogFactory.getLog(OutputType.class);

    @XmlElementRef
    protected List<ProcessorType<?>> outputs = new ArrayList<ProcessorType<?>>();

    public List<ProcessorType<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType<?>> outputs) {
        this.outputs = outputs;
        if (outputs != null) {
            for (ProcessorType output : outputs) {
                configureChild(output);
            }
        }
    }


    @Override
    protected void configureChild(ProcessorType output) {
        super.configureChild(output);
        if (isInheritErrorHandler()) {
            output.setErrorHandlerBuilder(getErrorHandlerBuilder());
        }
        // don't inherit interceptors by default
/*
        List<InterceptorType> list = output.getInterceptors();
        if (list == null) {
            log.warn("No interceptor collection: " + output);
        }
        else {
            list.addAll(getInterceptors());
        }
*/
    }
}
