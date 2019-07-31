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
package org.apache.camel.component.sap.netweaver;

public final class NetWeaverTestConstants {
    public static final String NETWEAVER_GATEWAY_URL = "https://sapes4.sapdevcenter.com/sap/opu/odata/IWFND/RMTSAMPLEFLIGHT";
    public static final String NETWEAVER_FLIGHT_COMMAND = "FlightCollection(carrid='AA',connid='0017',fldate=datetime'2016-04-20T00%3A00%3A00')";
    public static final String NETWEAVER_FLIGHT_BOOKING_COMMAND = NETWEAVER_FLIGHT_COMMAND + "/flightbooking";

    private NetWeaverTestConstants() {
    }
}
