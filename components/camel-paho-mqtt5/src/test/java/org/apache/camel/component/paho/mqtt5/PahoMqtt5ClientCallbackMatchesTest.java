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
package org.apache.camel.component.paho.mqtt5;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The topic filter matching of the shared client callback follows MQTT 5 section 4.7: the examples of the
 * specification, empty levels, topics that start with $ and shared subscriptions.
 */
class PahoMqtt5ClientCallbackMatchesTest {

    @ParameterizedTest(name = "{0} matches {1}: {2}")
    @CsvSource({
            // 4.7.1.2 multi-level wildcard
            "sport/tennis/player1/#, sport/tennis/player1, true",
            "sport/tennis/player1/#, sport/tennis/player1/ranking, true",
            "sport/tennis/player1/#, sport/tennis/player1/score/wimbledon, true",
            "sport/#, sport, true",
            "'#', sport/tennis/player1, true",
            "sport/tennis/#, sport/tennis, true",
            "sport/tennis/#, sport/tennisplayer1, false",
            // # that is not the last level is not a valid filter
            "sport/tennis/#/ranking, sport/tennis/player1/ranking, false",
            // 4.7.1.3 single-level wildcard
            "sport/tennis/+, sport/tennis/player1, true",
            "sport/tennis/+, sport/tennis/player2, true",
            "sport/tennis/+, sport/tennis/player1/ranking, false",
            "sport/+, sport, false",
            "sport/+, sport/, true",
            "+, sport, true",
            "+/+, /finance, true",
            "/+, /finance, true",
            "+, /finance, false",
            "+, /, false",
            "+/tennis/#, sport/tennis/player1, true",
            // + matches an empty level
            "+/a, /a, true",
            "a/+/b, a//b, true",
            "a/+, a/, true",
            // a trailing / is one more (empty) level
            "a/b, a/b/, false",
            "a/b, a/b, true",
            "a/b, a/c, false",
            // 4.7.2 topics that start with $
            "'#', $SYS/x, false",
            "+/x, $SYS/x, false",
            "+/monitor/Clients, $SYS/monitor/Clients, false",
            "$SYS/#, $SYS/x, true",
            "$SYS/monitor/+, $SYS/monitor/Clients, true",
            // 4.8.2 shared subscriptions deliver the messages of the filter after $share/<group>/
            "$share/g/sport/+, sport/tennis, true",
            "$share/g/sport/+, news/tennis, false",
            "$share/g/#, sport/tennis/player1, true",
            "$share/g/#, $SYS/x, false"
    })
    void matches(String filter, String topic, boolean expected) {
        assertThat(PahoMqtt5ClientCallback.matches(filter, topic)).isEqualTo(expected);
    }
}
