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
package org.apache.camel.component.aries.processor;

import io.nessus.aries.util.AssertState;
import io.nessus.aries.wallet.NessusWallet;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.aries.HyperledgerAriesComponent;

import static org.apache.camel.component.aries.Constants.HEADER_WALLET_NAME;
import static org.apache.camel.component.aries.Constants.HEADER_WALLET_RECORD;
import static org.apache.camel.component.aries.Constants.PROPERTY_HYPERLEDGER_ARIES_COMPONENT;

public abstract class AbstractAriesProcessor implements Processor {

    public static HyperledgerAriesComponent getAriesComponent(Exchange exchange) {
        HyperledgerAriesComponent component
                = exchange.getProperty(PROPERTY_HYPERLEDGER_ARIES_COMPONENT, HyperledgerAriesComponent.class);
        AssertState.notNull(component, "Cannot find exchange property: " + PROPERTY_HYPERLEDGER_ARIES_COMPONENT);
        return component;
    }

    public static String assertWalletName(Exchange exchange) {
        String walletName = exchange.getIn().getHeader(HEADER_WALLET_NAME, String.class);
        AssertState.notNull(walletName, "Cannot find exchange property: " + HEADER_WALLET_NAME);
        return walletName;
    }

    public static NessusWallet assertWallet(Exchange exchange) {
        NessusWallet walletRecord = exchange.getIn().getHeader(HEADER_WALLET_RECORD, NessusWallet.class);
        AssertState.notNull(walletRecord, "Cannot find exchange property: " + HEADER_WALLET_RECORD);
        return walletRecord;
    }

    public static <T> T getHeader(Exchange exchange, String prefix, Class<T> type) {
        return exchange.getIn().getHeader(prefix + type.getSimpleName(), type);
    }

    public static <T> T getHeader(Exchange exchange, Class<T> type) {
        return exchange.getIn().getHeader(type.getSimpleName(), type);
    }
}
