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

# Example authorization policy for the camel-opa component.
#
# Camel sends an input document shaped as:
#
#   {
#     "headers":    { "user": "alice", "CamelHttpMethod": "POST", ... },
#     "body":       ...,          # only when includeBody=true
#     "exchangeId": "...",
#     "routeId":    "..."
#   }
#
# The rule head queried by the endpoint is the endpoint path, so `opa:authz/allow`
# evaluates `allow` in the package below, and `opa:authz/decision` evaluates `decision`.

package authz

# Always give a decision rule a default. An undefined decision is reported by OPA as an
# evaluation error rather than as a deny, which the component then fails closed on.
default allow := false

# A plain boolean decision: readable directly from the CamelOpaDecisionAllow header.
allow if {
	input.headers.user == "alice"
}

allow if {
	input.headers.role == "admin"
}

# A decision that returns more than a boolean. The component reads the verdict from the
# `allow` key (configurable with allowKey) and puts the whole object in CamelOpaDecision,
# so a route can act on the reasons as well as on the verdict.
decision := {"allow": true, "reasons": []} if {
	input.headers.user == "alice"
}

decision := {"allow": false, "reasons": ["not the owner"]} if {
	input.headers.user != "alice"
}
