package models

import play.api.libs.json._

case class PaymentRequest(recipient: String)

object PaymentRequest {

  implicit val newPaymentReads: Reads[PaymentRequest] =
    (__ \ "recipient").read[String].map { recipient => PaymentRequest(recipient) }

}

