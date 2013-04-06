<%--
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
--%>
<%@ include file="top.jsp" %>
Here's the list of names of your public and private Google calendars:
<p>
<% for (String calendarName : (java.util.List<String>)request.getAttribute("calendarNames")) { %>
<%= calendarName %><br>
<% } %>
<p>
<a href="https://www.google.com/accounts/IssuedAuthSubTokens">Click here</a> if 
you want to revoke access to your Google Calendar.
<%@ include file="bottom.jsp" %>
