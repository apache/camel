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
package org.apache.camel.component.cmis;

public enum CamelCMISActions {
    CREATE { public String getMethodName() {
            return "createNode"; }},
    DELETE_DOCUMENT { public String getMethodName() {
            return "deleteDocument"; }},
    DELETE_FOLDER { public String getMethodName() {
            return "deleteFolder"; }},
    MOVE_DOCUMENT { public String getMethodName() {
            return "moveDocument"; }},
    MOVE_FOLDER { public String getMethodName() {
            return "moveFolder"; }},
    COPY_DOCUMENT { public String getMethodName() {
            return "copyDocument"; }},
    COPY_FOLDER { public String getMethodName() {
            return "copyFolder"; }},
    RENAME { public String getMethodName() {
            return "rename"; }},
    CHECK_IN { public String getMethodName() {
            return "checkIn"; }},
    CHECK_OUT { public String getMethodName() {
            return "checkOut"; }},
    CANCEL_CHECK_OUT { public String getMethodName() {
            return "cancelCheckOut"; }},
    GET_FOLDER { public String getMethodName() {
            return "getFolder"; }},
    LIST_FOLDER { public String getMethodName() {
            return "listFolder"; }},
    FIND_OBJECT_BY_ID { public String getMethodName() {
            return "findObjectById"; }},
    FIND_OBJECT_BY_PATH { public String getMethodName() {
            return "findObjectByPath"; }},
    CREATE_FOLDER_BY_PATH { public String getMethodName() {
            return "createFolderByPath"; }},
    DOWNLOAD_DOCUMENT { public String getMethodName() {
            return "downloadDocument"; }};

    public abstract String getMethodName();
}
