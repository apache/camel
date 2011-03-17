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

/**
 * A simple DataSet that allows a static payload to be used to create each message exchange
 * along with using a pluggable transformer to randomize the message.
 *
 * @version 
 */
public class SimpleDataSet extends DataSetSupport {
    private Object defaultBody = "<hello>world!</hello>";

    public SimpleDataSet() {
    }

    public SimpleDataSet(int size) {
        super(size);
    }

    // Properties
    //-------------------------------------------------------------------------

    public Object getDefaultBody() {
        return defaultBody;
    }

    public void setDefaultBody(Object defaultBody) {
        this.defaultBody = defaultBody;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    /**
     * Creates the message body for a given message
     */
    protected Object createMessageBody(long messageIndex) {
        return getDefaultBody();
    }
}
