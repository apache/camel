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

package org.apache.camel.component.krati;

public final class KratiConstants {
    public static final String KEY = "CamelKratiKey";
    public static final String VALUE = "CamelKratiValue";

    //Store Types
    public static final String STORE_TYPE = "STORE_TYPE";
    public static final String DYNAMIC_STORE = "DYNAMIC_STORE";
    public static final String INDEXED_STORE = "INDEXED_STORE";

    //Operation Types
    public static final String KRATI_OPERATION = "CamelKratiOperation";
    public static final String KRATI_OPERATION_PUT = "CamelKratiPut";
    public static final String KRATI_OPERATION_UPDATE = "CamelKratiUpdate";
    public static final String KRATI_OPERATION_DELETE = "CamelKratiDelete";
    public static final String KRATI_OPERATION_DELETEALL = "CamelKratiDeleteAll";
    public static final String KRATI_OPERATION_GET = "CamelKratiGet";

    public static final String KRATI_OPERATION_STATUS = "CamelKratiOperationStatus";
    public static final String KRATI_OPERATION_SUCESSFUL = "CamelKratiOperationSuccess";
    public static final String KRATI_OPERATION_FAILURE = "CamelKratiOperationFailure";

    private KratiConstants() {
        //Utility Class
    }




}
