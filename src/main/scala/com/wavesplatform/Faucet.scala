package com.wavesplatform

import com.google.common.cache.CacheBuilder
import com.wavesplatform.account.{Address, AddressScheme, PKKeyPair, PrivateKey}
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.common.utils.EitherExt2.explicitGet
import com.wavesplatform.transaction.Asset
import com.wavesplatform.transaction.transfer.TransferTransaction
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json}

import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

object Faucet {
  private val log = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    AddressScheme.current = new AddressScheme { override val chainId: Byte = sys.props("faucet.chain-id").head.toByte }

    val nodeUrl         = sys.props("faucet.waves-node-url")
    val payAmount       = sys.props("faucet.pay-amount").toLong
    val recaptchaSecret = sys.props("faucet.recaptcha-secret")
    val keyPair         = PKKeyPair(PrivateKey(ByteStr.decodeBase58(sys.props("faucet.private-key")).get))

    val rateLimit = CacheBuilder
      .newBuilder()
      .expireAfterWrite(15, TimeUnit.MINUTES)
      .build[String, java.lang.Long]()

    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "faucet")
    implicit val ec: ExecutionContext         = system.executionContext

    val corsHeaders = List(
      RawHeader("Access-Control-Allow-Origin", "*"),
      RawHeader("Access-Control-Allow-Methods", "POST, OPTIONS"),
      RawHeader("Access-Control-Allow-Headers", "Content-Type")
    )

    def jsonResponse(status: StatusCode, body: String): HttpResponse =
      HttpResponse(status, entity = HttpEntity(ContentTypes.`application/json`, body))

    def error(status: StatusCode, message: String): HttpResponse =
      jsonResponse(status, Json.obj("status" -> "Error", "message" -> message).toString())

    def isValidToken(token: String, ip: String): Future[Boolean] =
      Http()
        .singleRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri = "https://www.google.com/recaptcha/api/siteverify",
            entity = FormData("secret" -> recaptchaSecret, "response" -> token, "remoteip" -> ip).toEntity
          )
        )
        .flatMap(_.entity.toStrict(5.seconds))
        .map(e => (Json.parse(e.data.utf8String) \ "success").as[Boolean])

    def broadcast(recipient: String, timestamp: Long): Future[JsValue] = {
      val tx = TransferTransaction
        .selfSigned(
          version = 3.toByte,
          sender = keyPair,
          recipient = Address.fromString(recipient).explicitGet(),
          asset = Asset.Waves,
          amount = payAmount,
          feeAsset = Asset.Waves,
          fee = 100_000L,
          attachment = ByteStr.empty,
          timestamp = timestamp
        )
        .explicitGet()

      Http()
        .singleRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri = s"$nodeUrl/transactions/broadcast",
            entity = HttpEntity(ContentTypes.`application/json`, tx.json().toString())
          )
        )
        .flatMap(_.entity.toStrict(5.seconds))
        .map(e => Json.parse(e.data.utf8String))
    }

    val route = respondWithHeaders(corsHeaders) {
      options {
        complete(HttpResponse(StatusCodes.OK))
      } ~
        path("payment") {
          post {
            entity(as[String]) { rawBody =>
              extractClientIP { clientIp =>
                val ip  = clientIp.toOption.map(_.getHostAddress).getOrElse("unknown")
                val now = System.currentTimeMillis
                log.info(s"IP: $ip Body: $rawBody")

                Option(rateLimit.getIfPresent(ip)) match {
                  case Some(ts) =>
                    val remaining = (15.minutes.toMillis - (now - ts)) / 1000
                    complete(error(StatusCodes.BadRequest, s"Try again after $remaining seconds"))

                  case None =>
                    Try(Json.parse(rawBody)).toOption.flatMap { json =>
                      for {
                        recipient <- (json \ "recipient").asOpt[String]
                        token     <- (json \ "token").asOpt[String]
                      } yield (recipient, token)
                    } match {
                      case None =>
                        complete(error(StatusCodes.BadRequest, "Invalid json request"))

                      case Some((recipient, token)) =>
                        onComplete(isValidToken(token, ip)) {
                          case Failure(e) =>
                            log.error("reCAPTCHA check failed", e)
                            complete(error(StatusCodes.InternalServerError, "Internal error. Try again later."))
                          case Success(false) =>
                            complete(error(StatusCodes.BadRequest, "Invalid captcha"))
                          case Success(true) =>
                            onComplete(broadcast(recipient, now)) {
                              case Failure(e) =>
                                log.error("Broadcast failed", e)
                                complete(error(StatusCodes.InternalServerError, "Internal error. Try again later."))
                              case Success(txJson) =>
                                (txJson \ "error").toOption match {
                                  case Some(_) =>
                                    complete(error(StatusCodes.BadRequest, (txJson \ "message").as[String]))
                                  case None =>
                                    rateLimit.put(ip, now)
                                    complete(jsonResponse(StatusCodes.OK, Json.obj("status" -> "OK", "tx" -> txJson).toString()))
                                }
                            }
                        }
                    }
                }
              }
            }
          }
        }
    }

    val faucetAddress = Address.fromPublicKey(keyPair.publicKey)
    Http().newServerAt("0.0.0.0", 9000).bind(route)
    log.info(s"Faucet address: $faucetAddress, listening on :9000")
    Await.ready(system.whenTerminated, Duration.Inf)
  }
}
