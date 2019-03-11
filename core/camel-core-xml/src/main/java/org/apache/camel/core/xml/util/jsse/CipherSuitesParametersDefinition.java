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
package org.apache.camel.core.xml.util.jsse;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cipherSuitesParameters", propOrder = {"cipherSuite"})
public class CipherSuitesParametersDefinition {

    private List<String> cipherSuite;

    /**
     * Returns a live reference to the list of cipher suite names.
     *
     * @return a reference to the list, never {@code null}
     */
    public List<String> getCipherSuite() {
        if (this.cipherSuite == null) {
            this.cipherSuite = new ArrayList<>();
        }
        return this.cipherSuite;
    }
}
