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

import org.apache.camel.Message;
import org.apache.camel.component.hbase.model.HBaseData;

/**
 * A {@link org.apache.camel.component.hbase.mapping.CellMappingStrategy} implementation.
 * It distinguishes between multiple cell, by reading headers with index suffix.
 * <p/>
 * In case of multiple headers:
 * <ul>
 * <li>First header is expected to have no suffix</li>
 * <li>Suffixes start from number 2</li>
 * <li>Suffixes need to be sequential</li>
 * </ul>
 */
public class BodyMappingStrategy implements CellMappingStrategy {

    /**
     * Resolves the cells that the {@link org.apache.camel.Exchange} refers to.
     */
    @Override
    public HBaseData resolveModel(Message message) {
        return message.getBody(HBaseData.class);
    }

    /**
     * Applies the cells to the {@link org.apache.camel.Exchange}.
     */
    @Override
    public void applyGetResults(Message message, HBaseData data) {
        if (data == null) {
            return;
        }
        message.setBody(data);
    }

    /**
     * Applies the cells to the {@link org.apache.camel.Exchange}.
     */
    @Override
    public void applyScanResults(Message message, HBaseData data) {
        if (data == null) {
            return;
        }
        message.setBody(data);
    }
}
