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
package org.apache.camel.model.dataformat;

/**
 * Data format that is capable of using content type header.
 */
public interface ContentTypeHeaderAware {

    /**
     * Whether the data format should set the <tt>Content-Type</tt> header with the type from the data format.
     * <p/>
     * For example <tt>application/xml</tt> for data formats marshalling to XML, or <tt>application/json</tt> for data
     * formats marshalling to JSON etc.
     */
    String getContentTypeHeader();

    /**
     * Whether the data format should set the <tt>Content-Type</tt> header with the type from the data format.
     * <p/>
     * For example <tt>application/xml</tt> for data formats marshalling to XML, or <tt>application/json</tt> for data
     * formats marshalling to JSON etc.
     */
    void setContentTypeHeader(String contentTypeHeader);

}
