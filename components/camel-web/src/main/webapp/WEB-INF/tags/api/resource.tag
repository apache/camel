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
<%@ attribute name="resource" type="com.sun.jersey.server.impl.wadl.WadlResourceResource" required="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="api" tagdir="/WEB-INF/tags/api" %>

<h2>Resource: ${resource.fullPath}</h2>

<api:doc docs="${resource.resource.doc}"/>

<h3>Methods</h3>
<table>
  <tr>
    <th>Name</th>
    <th>Description</th>
    <th>ID</th>
    <th>Request</th>
    <th>Response</th>
  </tr>
  <c:forEach var="method" items="${resource.methods}">
    <tr>
      <td>${method.name}</td>
      <td>
        <api:doc docs="${method.doc}"/>
      </td>
      <td>${method.id}</td>
      <td>
        <c:forEach var="rep" items="${method.request.representation}">
          <li>${rep.mediaType}</li>
        </c:forEach>
      </td>
      <td>
        <c:forEach var="rep" items="${method.response.representationOrFault}">
          <li>${rep.value.mediaType}</li>
        </c:forEach>
      </td>
    </tr>
  </c:forEach>
</table>

<h3>Sub Resources</h3>
<table>
  <tr>
    <th>Resource</th>
    <th>Description</th>
  </tr>
  <c:forEach var="sub" items="${resource.children}">
    <tr>
      <td>
        <a href='<c:url value="/api/resources${resource.fullPathWithoutTrailingSlash}/${sub.path}"/>'>${sub.path}</a>
      </td>
      <td>
        <api:doc docs="${sub.doc}"/>
      </td>
    </tr>
  </c:forEach>
</table>




