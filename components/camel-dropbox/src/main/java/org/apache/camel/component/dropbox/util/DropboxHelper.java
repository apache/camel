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
package org.apache.camel.component.dropbox.util;

import org.apache.camel.Exchange;
import org.apache.camel.component.dropbox.DropboxConfiguration;
import org.apache.camel.util.ObjectHelper;

public final class DropboxHelper {

    private DropboxHelper() { }

    public static String getRemotePath(DropboxConfiguration configuration, Exchange exchange) {
        return ObjectHelper.isNotEmpty(
            exchange.getIn().getHeader(DropboxRequestHeader.REMOTE_PATH.name()))
            ? exchange.getIn().getHeader(DropboxRequestHeader.REMOTE_PATH.name(), String.class).replaceAll("\\s", "+")
            : configuration.getRemotePath();
    }

    public static String getNewRemotePath(DropboxConfiguration configuration, Exchange exchange) {
        return ObjectHelper.isNotEmpty(
            exchange.getIn().getHeader(DropboxRequestHeader.NEW_REMOTE_PATH.name()))
            ? exchange.getIn().getHeader(DropboxRequestHeader.NEW_REMOTE_PATH.name(), String.class)
            : configuration.getNewRemotePath();
    }

    public static String getLocalPath(DropboxConfiguration configuration, Exchange exchange) {
        return ObjectHelper.isNotEmpty(
            exchange.getIn().getHeader(DropboxRequestHeader.LOCAL_PATH.name()))
            ? exchange.getIn().getHeader(DropboxRequestHeader.LOCAL_PATH.name(), String.class)
            : configuration.getLocalPath();
    }

    public static String getQuery(DropboxConfiguration configuration, Exchange exchange) {
        return ObjectHelper.isNotEmpty(
            exchange.getIn().getHeader(DropboxRequestHeader.QUERY.name()))
            ? exchange.getIn().getHeader(DropboxRequestHeader.QUERY.name(), String.class)
            : configuration.getQuery();
    }

    public static DropboxUploadMode getUploadMode(DropboxConfiguration configuration, Exchange exchange) {
        return ObjectHelper.isNotEmpty(
            exchange.getIn().getHeader(DropboxRequestHeader.UPLOAD_MODE.name()))
            ? DropboxUploadMode.valueOf(exchange.getIn().getHeader(DropboxRequestHeader.UPLOAD_MODE.name(), String.class))
            : configuration.getUploadMode();
    }





}
