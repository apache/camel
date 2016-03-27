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

## This script runs the camel-itest-karaf in a more reliable way
## than Maven surefire will do as it can hang after a while
## The script also kills each karaf container after a test to ensure there is no Java JVMs
## danging around.

echo "Running tests and kill karaf after each test"

FILES=src/test/java/org/apache/camel/itest/karaf/*

for filename in $FILES
do
  testname=$(basename ${filename%.*})
  if [ $testname != "AbstractFeatureTest" ]
  then
    echo "Running test $testname"
    mvn test -Dtest=$testname
    ## TODO: wonder if we can get exit code from mvn, and fail if its not 0 ?
    echo "Killing Karaf to ensure no dangling karaf running"
    jps -l | grep karaf | cut -d ' ' -f 1 | xargs -n1 kill -kill
  fi  
done
