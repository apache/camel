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

# Overview tab shows running integrations
Sleep 4s
Screenshot screenshots/camel-tui-hello-overview.png

# Routes tab - see the timer route
Type "3"
Sleep 4s
Screenshot screenshots/camel-tui-hello-routes.png

# Route diagram
Type "D"
Sleep 4s
Screenshot screenshots/camel-tui-hello-diagram.png
Type "D"
Sleep 1s

# Endpoints tab
Type "5"
Sleep 4s
Screenshot screenshots/camel-tui-hello-endpoints.png

# Log tab - see Hello World messages flowing
Type "2"
Sleep 4s
Screenshot screenshots/camel-tui-hello-log.png

# Health tab
Type "7"
Sleep 4s
Screenshot screenshots/camel-tui-hello-health.png

# Back to overview
Type "1"
Sleep 4s

# Quit
Type "q"
