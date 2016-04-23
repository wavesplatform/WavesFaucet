package models

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Writes}

/**
  *
  */
case class WavesPayment(sender: String, recipient: String, amount: Long, fee: Long)


object WavesPayment {

  implicit val wavesPaymentWrites : Writes[WavesPayment] = (
    (JsPath \ "sender").write[String] and
      (JsPath \ "recipient").write[String] and
      (JsPath \ "amount").write[Long] and
      (JsPath \ "fee").write[Long]
    )(unlift(WavesPayment.unapply))
}