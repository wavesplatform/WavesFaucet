package controllers

import javax.inject._

import com.typesafe.config.ConfigFactory
import models.{PaymentRequest, WavesPayment, WavesTransaction}
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc._
import play.api.cache._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(ws: WSClient, cache: CacheApi)(implicit exec: ExecutionContext) extends Controller {

  val log = Logger
  val url = ConfigFactory.load().getString("waves.node.url") + "/payment"
  val apiKey = ConfigFactory.load().getString("waves.node.apiKey")
  val payFrom = ConfigFactory.load().getString("faucet.payFrom")
  val payAmount = ConfigFactory.load().getLong("faucet.payAmount")
  val recaptchaSecret = ConfigFactory.load().getString("faucet.recaptchaSecret")

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index: Action[AnyContent] = Action {
    Ok("")
    //Ok(views.html.index("Your new application is ready."))
  }

  /**
    *
    */
  def payment = Action(BodyParsers.parse.json) { request =>

    log.info(s"IP: ${request.remoteAddress} Body: ${request.body.toString}")

    val currentTimestamp = System.currentTimeMillis

    cache.get[Long](request.remoteAddress) match {
      case Some(timestamp) => {
        val elapsed = currentTimestamp - timestamp
        val message = "Try again after "+((15.minutes.toMillis - elapsed)/1000).toString + " seconds"
        BadRequest(Json.obj("status" -> "Error", "message" -> message))
      }
      case _ => {
        val result = request.body.validate[PaymentRequest]
        result.fold(
          errors => {
            BadRequest(Json.obj("status" -> "Error", "message" -> "Invalid json request"))
          },
          payment => {
            if (isValidToken(payment.token, request.remoteAddress))
              Try(processPaymentRequest(payment, request.remoteAddress, currentTimestamp)) match {
                case Success(result) => result
                case Failure(e) => {
                  log.error(e.toString)
                  InternalServerError(Json.obj("status" -> "Error", "message" -> "Internal error. Try again later."))
                }
              }
            else BadRequest(Json.obj("status" -> "Error", "message" -> "Invalid captcha"))
          }
        )
      }
    }
  }


  private def isValidToken(token: String, ip: String) = {
    val future = ws.url("https://www.google.com/recaptcha/api/siteverify").post(Map(
      "secret" -> Seq(recaptchaSecret),
      "response" -> Seq(token),
      "remoteip" -> Seq(ip)
    ))
    val response = Await.result(future, 10.seconds)
    log.info(response.json.toString)
    (response.json \ "success").as[Boolean]
  }

  private def processPaymentRequest(payment: PaymentRequest, remoteAddress: String, currentTimestamp: Long) = {
    val wavesPayment = WavesPayment(payFrom, payment.recipient, payAmount, 100000L)
    val futureResponse = ws.url(url)
        .withHeaders("api_key" -> apiKey)
        .post(Json.toJson(wavesPayment))
    val response = Await.result(futureResponse, 5.seconds)
    val json = response.json
    (json \ "error").toOption match {
      case Some(error) => BadRequest(
        Json.obj("status" -> "Error", "message" -> (json \ "message").as[String]))
      case None => {
        val signature = (json \ "signature").as[String]
        val tx = WavesTransaction(signature, payment.recipient, wavesPayment.amount)

        cache.set(remoteAddress, currentTimestamp, 15.minutes)

        Ok(Json.obj("status" -> "OK", "tx" -> Json.toJson(tx)))
      }
    }
  }
}
