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
package org.apache.camel.spring.model;

import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import java.util.List;
import java.util.ArrayList;

/**
 * A useful base class for output types
 * 
 * @version $Revision: 1.1 $
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class OutputType {
    @XmlElements({
    @XmlElement(name = "process", type = ProcessorRef.class),
    @XmlElement(name = "to", type = ToType.class)})
    protected List<ProcessorType> processor = new ArrayList<ProcessorType>();

    public List<ProcessorType> getProcessor() {
        return processor;
    }

    public void setProcessor(List<ProcessorType> processor) {
        this.processor = processor;
    }
}
