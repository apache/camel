<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Languages</title>
</head>
<body>

<h1>Languages</h1>


<table>
  <tr>
    <th>Language</th>
    <th>Documentation</th>
  </tr>
  <c:forEach items="${it.languageIds}" var="id">
    <tr>
      <td><a href="languages/${id}">${id}</a></td>
      <td><a href="http://camel.apache.org/${id}.html">documentation</a></td>
    </tr>
  </c:forEach>
</table>

</body>
</html>
