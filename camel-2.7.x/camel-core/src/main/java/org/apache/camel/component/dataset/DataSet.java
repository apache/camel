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

import org.apache.camel.Exchange;

/**
 * Represents a strategy for testing endpoints with canned data.
 *
 * @version 
 */
public interface DataSet {

    /**
     * Populates a message exchange when using the DataSet as a source of messages
     */
    void populateMessage(Exchange exchange, long messageIndex) throws Exception;

    /**
     * Returns the size of the dataset
     */
    long getSize();

    /**
     * Asserts that the expected message has been received for the given index
     */
    void assertMessageExpected(DataSetEndpoint endpoint, Exchange expected, Exchange actual, long messageIndex) throws Exception;

    /**
     * Returns the number of messages which should be received before reporting on the progress of the test
     */
    long getReportCount();
}
