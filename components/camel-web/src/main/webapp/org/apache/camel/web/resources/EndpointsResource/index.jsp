<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Endpoints</title>
</head>
<body>

<h2>Endpoints</h2>

<ul>
  <c:forEach var="i" items="${it.endpoints}">
    <li><a class='endpoint' href='<c:url value="${i.href}"/>'>${i.uri}</a>
  </c:forEach>
</ul>

<h2>Create New Endpoint</h2>

<form action="<c:url value="/endpoints"/>" method="post" name="createEndpoint">
  <table>
    <c:if test="${not empty it.error}">
      <tr>
        <td colspan="2" align="center" class="error">
           ${it.error}
        </td>
      </tr>
    </c:if>
    <tr>
      <td>
        Please enter the new endpoint URI
      </td>
      <td>
        <input type="text" name="uri" value="${it.newUri}" width="80">
      </td>
    </tr>
    <tr>
      <td colspan="2" align="center">
         <input type="submit" value="Create"> <input type="reset">
      </td>
    </tr>
  </table>
</form>
        

</body>
</html>
