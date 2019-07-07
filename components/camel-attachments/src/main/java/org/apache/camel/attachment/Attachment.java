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
package org.apache.camel.attachment;

import java.util.Collection;
import java.util.List;

import javax.activation.DataHandler;

import org.apache.camel.Message;

/**
 * Represents an attachment as part of a {@link Message}.
 */
public interface Attachment {

    /**
     * Return a DataHandler for the content within this attachment.
     *
     * @return DataHandler for the content
     */
    DataHandler getDataHandler();

    /**
     * Get all the headers for this header name. Returns null if no headers for
     * this header name are available.
     *
     * @param headerName he name of this header
     * @return a comma separated list of all header values
     */
    String getHeader(String headerName);

    /**
     * Get all the headers for this header name. Returns null if no headers for
     * this header name are available.
     *
     * @param name The name of this header
     * @return a list of all header values
     */
    List<String> getHeaderAsList(String name);

    /**
     * Get all header names for this attachment.
     *
     * @return a collection of all header names
     */
    Collection<String> getHeaderNames();

    /**
     * Set the value for this headerName. Replaces all existing header values
     * with this new value.
     * 
     * @param headerName the name of this header
     * @param headerValue the value for this header
     */
    void setHeader(String headerName, String headerValue);

    /**
     * Add this value to the existing values for this headerName.
     * 
     * @param headerName the name of this header
     * @param headerValue the value for this header
     */
    void addHeader(String headerName, String headerValue);

    /**
     * Remove all headers with this name.
     * 
     * @param headerName the name of this header
     */
    void removeHeader(String headerName);
}
