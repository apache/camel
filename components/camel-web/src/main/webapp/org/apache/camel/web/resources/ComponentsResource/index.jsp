<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Components</title>
</head>
<body>

<h1>Components</h1>


<table>
  <tr>
    <th>Component</th>
    <th>Documentation</th>
  </tr>
  <c:forEach items="${it.componentIds}" var="id">
    <tr>
      <td><a href="components/${id}">${id}</a></td>
      <td><a href="http://camel.apache.org/${id}.html">documentation</a></td>
    </tr>
  </c:forEach>
</table>

</body>
</html>
