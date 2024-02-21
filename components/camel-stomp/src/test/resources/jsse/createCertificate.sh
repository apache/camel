#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

keytool -genkey -keystore server-side-keystore.jks -storepass password -keypass password -dname "CN=localhost, OU=Artemis, O=ActiveMQ, L=AMQ, S=AMQ, C=AMQ" -keyalg RSA
keytool -export -keystore server-side-keystore.jks -file server-side-cert.cer -storepass password
keytool -import -keystore client-side-truststore.jks -file server-side-cert.cer -storepass password -keypass password -noprompt
keytool -genkey -keystore client-side-keystore.jks -storepass password -keypass password -dname "CN=ActiveMQ Artemis Client, OU=Artemis, O=ActiveMQ, L=AMQ, S=AMQ, C=AMQ" -keyalg RSA
keytool -export -keystore client-side-keystore.jks -file client-side-cert.cer -storepass password
keytool -import -keystore server-side-truststore.jks -file client-side-cert.cer -storepass password -keypass password -noprompt