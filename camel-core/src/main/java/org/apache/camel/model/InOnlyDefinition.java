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

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.spi.Metadata;

/**
 * Marks the exchange pattern for the route to one way
 *
 * @version 
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "inOnly")
@XmlAccessorType(XmlAccessType.FIELD)
public class InOnlyDefinition extends SendDefinition<InOnlyDefinition> {

    public InOnlyDefinition() {
    }

    public InOnlyDefinition(String uri) {
        setUri(uri);
    }

    public InOnlyDefinition(Endpoint endpoint) {
        setEndpoint(endpoint);
    }

    @Override
    public String toString() {
        return "InOnly[" + getLabel() + "]";
    }
    
    @Override
    public String getLabel() {
        return "inOnly";
    }

    @Override
    public ExchangePattern getPattern() {
        return ExchangePattern.InOnly;
    }
}