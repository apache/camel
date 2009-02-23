<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Type Converters</title>
</head>
<body>

<h1>Type Converters</h1>


<table>
  <tr>
    <th>From Type</th>
  </tr>
  <c:forEach items="${it.fromClassTypes}" var="entry">
    <tr>
      <td><a href="converters/${entry.value.name}">${entry.key}</a></td>
    </tr>
  </c:forEach>
</table>


</body>
</html>
