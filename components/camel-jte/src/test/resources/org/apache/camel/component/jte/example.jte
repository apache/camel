@import org.apache.camel.component.jte.Model
@param Model model

Dear ${model.header("name")}. You ordered item ${model.exchangeProperty("item")} on ${model.strBody()}.