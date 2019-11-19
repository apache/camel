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
package org.apache.camel.component.iota;

import org.iota.jota.utils.Constants;

public final class IOTAConstants {

    public static final String SEED_HEADER = "CamelIOTASeed";
    public static final String VALUE_HEADER = "CamelIOTAValue";
    public static final String TO_ADDRESS_HEADER = "CamelIOTAToAddress";
    public static final String ADDRESS_INDEX_HEADER = "CamelIOTAAddressIndex";
    public static final String ADDRESS_START_INDEX_HEADER = "CamelIOTAAddressStartIndex";
    public static final String ADDRESS_END_INDEX_HEADER = "CamelIOTAAddressEndIndex";

    protected static final int MIN_WEIGHT_MAGNITUDE = 14;
    protected static final int DEPTH = 9;

    protected static final int TAG_LENGTH = Constants.TAG_LENGTH;
    protected static final int MESSAGE_LENGTH = Constants.MESSAGE_LENGTH;

    protected static final String SEND_TRANSFER_OPERATION = "sendTransfer";
    protected static final String GET_NEW_ADDRESS_OPERATION = "getNewAddress";
    protected static final String GET_TRANSFERS_OPERATION = "getTransfers";
    

    private IOTAConstants() {
    }

}
