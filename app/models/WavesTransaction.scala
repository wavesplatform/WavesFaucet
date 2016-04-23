package models

import play.api.libs.functional.syntax._
import play.api.libs.json._


case class WavesTransaction(signature: String, recipient: String, amount: Long)

object WavesTransaction {

  implicit val wavesTransactionWrites: Writes[WavesTransaction] = (
    (JsPath \ "signature").write[String] and
      (JsPath \ "recipient").write[String] and
      (JsPath \ "amount").write[Long]
    )(unlift(WavesTransaction.unapply))
}
