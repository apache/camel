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

# A policy that decides from the bundle's *data* document rather than from the rule bodies.
#
# `opa build` packs the data.json sitting beside this file into the bundle, and a server that
# loads the bundle answers against it. The WebAssembly module carries no data of its own, so
# camel-opa has to apply the bundle's data to every evaluation for the two to agree - which is
# what OpaWasmEvaluatorTest.appliesTheDataDocumentPackedInTheBundle asserts.

package roles

default allow := false

allow if {
	input.headers.user == data.admins[_]
}
