package models

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class PaymentRequest(recipient: String, token: String)

object PaymentRequest {

  implicit val newPaymentReads: Reads[PaymentRequest] = (
      (JsPath \ "recipient").read[String] and
      (JsPath \ "token").read[String]
    )(PaymentRequest.apply _)
}

