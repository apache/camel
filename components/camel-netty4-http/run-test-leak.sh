#!/bin/sh

## ---------------------------------------------------------------------------
## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements.  See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License.  You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
## ---------------------------------------------------------------------------

echo 'Running tests with Netty leak detection ...'
mvn clean install -Dio.netty.leakDetectionLevel=paranoid -Dio.netty.leakDetection.maxRecords=20

echo 'Checking log file if there is any leaks ...'

if grep LEAK target/camel-netty4-http-test.log; then
    echo 'LEAK found'
    exit 1
else
    echo 'No LEAK found'
    exit 0
fi
