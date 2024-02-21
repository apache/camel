#!/usr/bin/env bash
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

# This is a utility script to sign and sha512 all files in the given directory.

if [ "$#" -ne 1 ]; then
    echo "usage: $0 dir"
    exit 1
fi

dir=$1

pushd . && cd $dir

for f in $(ls);
do
    sha512sum -b $f > $f.sha512
    gpg --output $f.asc --armor --detach-sig $f
done

popd
