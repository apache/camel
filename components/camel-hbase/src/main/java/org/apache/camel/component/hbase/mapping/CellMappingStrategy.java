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
package org.apache.camel.component.hbase.mapping;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.hbase.model.HBaseData;

/**
 * A Cell resolver is responsible on identifying the cells, to which the Echange refers to.
 * Is used for all types of operations (Put, Get etc).
 * It is allowed that an exchange refers to more than once cells. This happens if headers
 * for multiple cells are present in the {@link Exchange}.
 */
public interface CellMappingStrategy {

    /**
     * Resolves the cell that the {@link Exchange} refers to.
     */
    HBaseData resolveModel(Message message);

    /**
     * Applies the KeyValues of a get operation to the {@link Exchange}.
     *
     * @param message The message that will be applied the Get result.
     * @param data The rows that will be applied to the message.
     */
    void applyGetResults(Message message, HBaseData data);

    /**
     * Applies the KeyValues of a scan operation to the {@link Exchange}.
     */
    void applyScanResults(Message message, HBaseData data);

}
