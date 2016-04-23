package models

import play.api.libs.json._

case class NewPayment(recipient: String)

object NewPayment {

  implicit val newPaymentReads: Reads[NewPayment] =
    (__ \ "recipient").read[String].map { recipient => NewPayment(recipient) }

}

