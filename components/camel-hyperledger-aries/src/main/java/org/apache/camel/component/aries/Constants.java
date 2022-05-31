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
package org.apache.camel.component.aries;

public final class Constants {

    // The ACA-Py API path 
    public static final String HEADER_SERVICE = "service";

    public static final String HEADER_WALLET_NAME = "WalletName";
    public static final String HEADER_WALLET_RECORD = "WalletRecord";

    public static final String HEADER_SCHEMA_NAME = "SchemaName";
    public static final String HEADER_SCHEMA_VERSION = "SchemaVersion";

    // Properties on the Exchange 
    public static final String PROPERTY_HYPERLEDGER_ARIES_COMPONENT = HyperledgerAriesComponent.class.getSimpleName();

    // The name of the TRUSTEE wallet that can onboard others to the ledger 
    public static final String HEADER_MULTITENANCY_TRUSTEE_WALLET = "multitenancy/trustee-wallet";

    // The Indy ledger role for this wallet
    public static final String HEADER_MULTITENANCY_LEDGER_ROLE = "multitenancy/ledger-role";

    // Allow this wallet to self register with ledger. This is cheating and required access to the ledger's admin interface
    public static final String HEADER_MULTITENANCY_SELF_REGISTER_NYM = "multitenancy/self-register-nym";

    private Constants() {
        // hide ctor
    }
}
