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

# Overview - 7 interconnected routes
Sleep 8s
Screenshot screenshots/camel-tui-topology-overview.svg

# Diagram tab - topology view
Type "3"
Sleep 4s
Screenshot screenshots/camel-tui-topology-diagram.svg

# Navigate to different routes
Down
Sleep 1s
Down
Sleep 1s
Down
Sleep 1s
Screenshot screenshots/camel-tui-topology-diagram-navigate.svg

# Toggle external endpoints
Type "e"
Sleep 3s
Screenshot screenshots/camel-tui-topology-diagram-external.svg

# Drill into a route
Enter
Sleep 3s
Screenshot screenshots/camel-tui-topology-diagram-drilldown.svg

# Back to topology
Escape
Sleep 1s

# Toggle external off
Type "e"
Sleep 1s

# Routes tab - all 7 routes with statistics
Type "4"
Sleep 3s
Screenshot screenshots/camel-tui-topology-routes.svg

# Errors tab - see failures
Type "9"
Sleep 3s
Screenshot screenshots/camel-tui-topology-errors.svg

# Log tab
Type "2"
Sleep 3s
Screenshot screenshots/camel-tui-topology-log.svg

# Health tab
Type "7"
Sleep 3s
Screenshot screenshots/camel-tui-topology-health.svg

# Endpoints tab
Type "5"
Sleep 3s
Screenshot screenshots/camel-tui-topology-endpoints.svg

# Back to overview
Type "1"
Sleep 2s

# Fun caption to end the demo
Ctrl+t
Sleep 500ms
Type "Thanks for watching! Happy integrating with Apache Camel!"
Enter
Sleep 4s
Screenshot screenshots/camel-tui-topology-final.svg

# Quit
Type "q"
