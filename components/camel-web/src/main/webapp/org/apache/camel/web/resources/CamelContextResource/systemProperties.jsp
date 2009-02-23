<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>System Properties</title>
</head>
<body>

<h1>System Properties</h1>


<table>
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <c:forEach items="${it.systemProperties}" var="entry">
    <tr>
      <td>${entry.key}</td>
      <td>${entry.value}</td>
    </tr>
  </c:forEach>
</table>


</body>
</html>
