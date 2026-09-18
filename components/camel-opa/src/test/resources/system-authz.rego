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

# OPA system authorization policy used by OpaBearerTokenIT.
#
# The server is started with --authentication=token --authorization=basic, which makes OPA
# evaluate data.system.authz.allow for every API request. input.identity is the bearer token
# presented by the caller, so this policy is what turns the camel-opa bearerToken option into
# something observable: without the right token, requests are rejected with 401.

package system.authz

default allow := false

# The container wait strategy has no token to present, so let the health endpoint through.
allow if {
	input.path == ["health"]
}

# Everything else requires the token the test configures on the endpoint.
allow if {
	input.identity == "s3cr3t-opa-token"
}
