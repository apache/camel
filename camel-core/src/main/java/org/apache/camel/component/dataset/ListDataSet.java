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
package org.apache.camel.component.dataset;

import java.util.LinkedList;
import java.util.List;

/**
 * A DataSet that allows a list of static payloads to be used to create each message exchange
 * along with using a pluggable transformer to customize the messages.
 *
 * @version
 */
public class ListDataSet extends DataSetSupport {
    private List<Object> defaultBodies;

    public ListDataSet() {
        super(0);
    }

    public ListDataSet(List<Object> defaultBodies) {
        this.defaultBodies = defaultBodies;
        setSize(defaultBodies.size());
    }

    // Properties
    //-------------------------------------------------------------------------

    public List<Object> getDefaultBodies() {
        if (defaultBodies == null) {
            defaultBodies = new LinkedList<>();
        }

        return defaultBodies;
    }

    public void setDefaultBodies(List<Object> defaultBodies) {
        this.defaultBodies = defaultBodies;
        setSize(defaultBodies.size());
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    /**
     * Creates the message body for a given message.  If the messageIndex is greater than the size
     * of the list, use the modulus.
     */
    protected Object createMessageBody(long messageIndex) {
        int listIndex = (int) (messageIndex % getDefaultBodies().size());

        return getDefaultBodies().get(listIndex);
    }
}
