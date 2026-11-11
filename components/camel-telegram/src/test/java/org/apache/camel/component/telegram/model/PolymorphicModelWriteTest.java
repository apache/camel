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
package org.apache.camel.component.telegram.model;

import java.util.List;

import org.apache.camel.component.telegram.model.payments.RevenueWithdrawalState;
import org.apache.camel.component.telegram.model.payments.RevenueWithdrawalStatePending;
import org.apache.camel.component.telegram.model.payments.TransactionPartner;
import org.apache.camel.component.telegram.model.payments.TransactionPartnerOther;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The polymorphic models carry their type id in their "type" property: they are written with it once, and read back.
 */
public class PolymorphicModelWriteTest {

    private final ObjectMapper mapper = JsonMapper.builderWithJackson2Defaults().build();

    @Test
    public void paidMedia() {
        PaidMediaPhoto photo = new PaidMediaPhoto();
        photo.setPhoto(List.of());
        String json = mapper.writeValueAsString(photo);
        assertThat(json).isEqualTo("{\"type\":\"photo\",\"photo\":[]}");
        assertThat(mapper.readValue(json, PaidMedia.class)).isInstanceOf(PaidMediaPhoto.class);
    }

    @Test
    public void transactionPartner() {
        String json = mapper.writeValueAsString(new TransactionPartnerOther());
        assertThat(json).isEqualTo("{\"type\":\"other\"}");
        assertThat(mapper.readValue(json, TransactionPartner.class)).isInstanceOf(TransactionPartnerOther.class);
    }

    @Test
    public void revenueWithdrawalState() {
        String json = mapper.writeValueAsString(new RevenueWithdrawalStatePending());
        assertThat(json).isEqualTo("{\"type\":\"pending\"}");
        assertThat(mapper.readValue(json, RevenueWithdrawalState.class)).isInstanceOf(RevenueWithdrawalStatePending.class);
    }
}
