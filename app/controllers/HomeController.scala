package controllers

import javax.inject._

import models.{NewPayment, WavesPayment, WavesTransaction}
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc._
import play.api.cache._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(ws: WSClient, cache: CacheApi)(implicit exec: ExecutionContext) extends Controller {


  val url = "http://52.58.115.4:6869/payment"
  val payFrom = "jACSbUoHi4eWgNu6vzAnEx583NwmUAVfS"

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {
    Ok("")
    //Ok(views.html.index("Your new application is ready."))
  }

  def payment = Action(BodyParsers.parse.json) { request =>

    val currentTimestamp = System.currentTimeMillis / 1000

    cache.get(request.remoteAddress) match {
      case Some(timestamp) => {
        return BadRequest(Json.obj("status" -> "Error", "message" -> "Try again after "+(currentTimestamp - timestamp)))
      }
    }

    val result = request.body.validate[NewPayment]
    result.fold(
      errors => {
        BadRequest(Json.obj("status" -> "Error", "message" -> "Invalid json request"))
      },
      payment => {
        val wavesPayment = WavesPayment(payFrom, payment.recipient, 100L, 1L)
        val futureResponse = ws.url(url).post(Json.toJson(wavesPayment))
        val response = Await.result(futureResponse, 5.seconds)
        val json = response.json
        (json \ "error").toOption match {
          case Some(error) => BadRequest(
            Json.obj("status" -> "Error", "message" -> (json \ "message").as[String]))
          case None => {
            val signature = (json \ "signature").as[String];
            val tx = WavesTransaction(signature, payment.recipient, 100L)

            cache.set(request.remoteAddress, currentTimestamp, 15.minutes)
            Ok(Json.obj("status" -> "OK", "tx" -> Json.toJson(tx)))
          }
        }
      }
    )
  }
}
