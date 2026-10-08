%dw 2.0
output application/json
---
{
    greeting: "Hello, " ++ payload.firstName ++ " " ++ payload.lastName
}
