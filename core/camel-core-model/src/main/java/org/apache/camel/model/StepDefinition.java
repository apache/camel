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
package org.apache.camel.model;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;

/**
 * Routes the message to a sequence of processors which is grouped together as one logical name
 */
@Metadata(label = "eip,routing",
          description = "Groups a sequence of processing steps under a single logical name"
                        + " for improved monitoring, management, and tracing visibility")
@XmlRootElement(name = "step")
@XmlAccessorType(XmlAccessType.FIELD)
@BodyTypes(eipOut = @BodyType(from = "steps",
                              description = "What its steps end with"),
           eipSteps = @BodyType(Carrier.UNCHANGED))
public class StepDefinition extends OutputDefinition<StepDefinition> {

    public StepDefinition() {
    }

    public StepDefinition(StepDefinition source) {
        super(source);
    }

    @Override
    public StepDefinition copyDefinition() {
        return new StepDefinition(this);
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return super.getOutputs();
    }

    @XmlElementRef
    @Override
    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        super.setOutputs(outputs);
    }

    @Override
    public String getShortName() {
        return "step";
    }

    @Override
    public String getLabel() {
        return "step";
    }
}
