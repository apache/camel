{
    "name": FirstName & " " & Surname,
    "mobile": Phone[type = "mobile"].number,
    "emails": Email#$i[$i>0],
    "binding": $reverse(FirstName)
}