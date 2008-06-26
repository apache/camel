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
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.processor.HandleFaultProcessor;

/**
 * Represents an XML &lt;handleFault/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "handleFault")
@XmlAccessorType(XmlAccessType.FIELD)
public class HandleFaultType extends InterceptorRef {

    public HandleFaultType() {
        super(new HandleFaultProcessor());
    }

    @Override
    public String getShortName() {
        return "handleFault";
    }

    @Override
    public String toString() {
        return "HandleFault[" + getLabel() + "]";
    }

}
