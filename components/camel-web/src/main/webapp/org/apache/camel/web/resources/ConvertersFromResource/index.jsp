<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Type Converters from: ${it.type.canonicalName}</title>
</head>
<body>

<h1>Type Converters from: ${it.type.canonicalName}</h1>

<table>
  <tr>
    <th>To Type</th>
    <th>Converter</th>
  </tr>
  <c:forEach items="${it.converters}" var="entry">
    <tr>
      <td>${entry.key}</td>
      <td>${entry.value}</td>
    </tr>
  </c:forEach>
</table>


</body>
</html>
