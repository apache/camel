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
package org.apache.camel.component.thrift;

/**
 * Thrift component constants
 */
public interface ThriftConstants {

    String THRIFT_SYNC_CLIENT_CLASS_NAME = "Client";
    String THRIFT_ASYNC_CLIENT_CLASS_NAME = "AsyncClient";
    String THRIFT_ASYNC_CLIENT_FACTORY_NAME = "Factory";
    String THRIFT_ASYNC_CLIENT_GETTER_NAME = "getAsyncClient";
    String THRIFT_SERVER_SYNC_INTERFACE_NAME = "Iface";
    String THRIFT_SERVER_SYNC_PROCESSOR_CLASS = "Processor";
    String THRIFT_SERVER_ASYNC_INTERFACE_NAME = "AsyncIface";
    String THRIFT_SERVER_ASYNC_PROCESSOR_CLASS = "AsyncProcessor";
    
    String THRIFT_DEFAULT_SECURITY_PROTOCOL = "TLS";
    String THRIFT_DEFAULT_SECURITY_STORE_TYPE = "JKS";
    
    int THRIFT_CONSUMER_POOL_SIZE = 1;
    int THRIFT_CONSUMER_MAX_POOL_SIZE = 10;
    /*
     * This headers will be set after Thrift consumer method is invoked
     */
    String THRIFT_METHOD_NAME_HEADER = "CamelThriftMethodName";
}
