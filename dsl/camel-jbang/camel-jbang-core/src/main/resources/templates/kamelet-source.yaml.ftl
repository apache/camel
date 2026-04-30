<#--

    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
apiVersion: camel.apache.org/v1
kind: Kamelet
metadata:
  name: [=Name]
  labels:
    camel.apache.org/kamelet.type: "source"
spec:
  definition:
    title: "Timer Example"
    description: "Produces periodic events with a custom payload"
    required:
      - message
    properties:
      period:
        title: Period
        description: The time interval between two events
        type: integer
        default: 1000
      message:
        title: Message
        description: The message to generate
        type: string
  types:
    out:
      mediaType: text/plain
  template:
    from:
      uri: timer:tick
      parameters:
        period: "{{period}}"
      steps:
        - setBody:
            constant: "{{message}}"
        - setHeader:
            name: "Content-Type"
            constant: "text/plain"
        - to: "kamelet:sink"
