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

## directory where the karaf unit tests are
testdir='src/test/java/org/apache/camel/itest'

## you can pass in the test name to start from eg run-tests.sh CamelHystrixTest
## to start testing from this test and onwards.
if [ "$#" -eq  "0" ]
then
  found=1
else
  found=0
fi  

## ensure the files are sorted
for filename in $(ls -f $testdir/*Test* | sort);
do
  testname=$(basename ${filename%.*})

  if [ $found -eq 0 ]
  then
    if [ $testname == "$1" ]
    then
     found=1
    fi 
  fi

  if [ $found -eq 1 ] && [ $testname != "AbstractFeatureTest" ]
  then
    echo "*******************************************************************"
    echo "Running test $testname"
    echo "*******************************************************************"
    if mvn test -Dtest=$testname ; then
      echo "\n"
      echo "*******************************************************************"
      echo "Test success: $testname"
      echo "*******************************************************************"
      echo "\n"
    else
      echo "\n"
      echo "*******************************************************************"
      echo "Test failure: $testname"
      echo "*******************************************************************"
      echo "\n"
      exit 1;
    fi  
    echo "Killing Karaf to ensure no dangling karaf running"
    ./kill-karaf.sh
  fi  
done
