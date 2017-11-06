package com.careercolony.neo4jServices.routes

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.ActorMaterializer
import com.careercolony.neo4jServices.factories.{DatabaseAccess, Experience, Media, GetExperience}
import spray.json.DefaultJsonProtocol
import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.CorsSettings

import akka.http.scaladsl.model.HttpMethods._
import scala.collection.immutable

import scala.collection.mutable.MutableList;
import spray.json._;



object UserJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val MediaFormats = jsonFormat7(Media)
  implicit val CreatePostFormats = jsonFormat9(Experience)
  implicit val GetPostFormats = jsonFormat8(GetExperience)
  
}

trait ProfileService extends DatabaseAccess {

  import UserJsonSupport._

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  val logger = Logging(system, getClass)

  implicit def myExceptionHandler = {
    ExceptionHandler {
      case e: ArithmeticException =>
        extractUri { uri =>
          complete(HttpResponse(StatusCodes.InternalServerError,
            entity = s"Data is not persisted and something went wrong"))
        }
    }
  }
  
  val settings = CorsSettings.defaultSettings.copy(allowedMethods = immutable.Seq(GET, PUT, POST, HEAD, OPTIONS))
  val profileRoutes: Route = cors(settings){
    post {
      path("new-experience") {
        entity(as[Experience]) { entity =>
          complete {
            try {
              val isPersisted: MutableList[GetExperience] = insertExperience(entity)
              isPersisted match {
                case _: MutableList[_] =>
                  var response: StringBuilder = new StringBuilder("[")
                  isPersisted.foreach(
                      x => response.append(x.toJson).append(",")
                    )
                  response.deleteCharAt(response.length - 1)
                  response.append("]");  
                  HttpResponse(StatusCodes.OK, entity = response.toString()) //data.toString())
                  case _ => HttpResponse(StatusCodes.BadRequest,
                   entity = s"User already exist")
              }
            } catch {
              case ex: Throwable =>
                logger.error(ex, ex.getMessage)
                HttpResponse(StatusCodes.InternalServerError,
                  entity = "Error while persisting data, please try again")
            }
          }
        }
      }
    }  ~ path("get" / "experiences" / Segment) { (memberID: String) =>
      get {
        complete {
          try {
            val idAsRDD: MutableList[GetExperience] = retrievePost(memberID.toInt)
            idAsRDD match {
              case _: MutableList[_] =>
                var response: StringBuilder = new StringBuilder("[")
                idAsRDD.foreach(
                    x => response.append(x.toJson).append(",")
                  )
                response.deleteCharAt(response.length - 1)
                response.append("]");  
                HttpResponse(StatusCodes.OK, entity = response.toString()) //data.toString())
              //case None => HttpResponse(StatusCodes.InternalServerError,
              //  entity = s"No user found")
            }
          } catch {
            case ex: Throwable =>
              logger.error(ex, ex.getMessage)
              HttpResponse(StatusCodes.InternalServerError,
                entity = s"Data is not fetched and something went wrong")
          }
        }
      }
    } ~ path("update-post") {
      put {
         entity(as[GetExperience]) { entity =>
          complete {
            try {
              val isPersisted = updatepost(entity)
              isPersisted match {
                case true => HttpResponse(StatusCodes.Created,
                entity = s"Data is successfully persisted")
              case false => HttpResponse(StatusCodes.InternalServerError,
                entity = s"Error found for post")
              }
            } catch {
              case ex: Throwable =>
                logger.error(ex, ex.getMessage)
                HttpResponse(StatusCodes.InternalServerError,
                  entity = "Error while persisting data, please try again")
            }
          }
        }
      }
    }  ~ path("delete" / "expID" / Segment) { (expID: String) =>
      get {
        complete {
          try {
            val idAsRDD = deleteRecord(expID)
            idAsRDD match {
              case 1 => HttpResponse(StatusCodes.OK, entity = "Data is successfully deleted")
              case 0 => HttpResponse(StatusCodes.InternalServerError,
                entity = s"Data is not deleted and something went wrong")
            }
          } catch {
            case ex: Throwable =>
              logger.error(ex, ex.getMessage)
              HttpResponse(StatusCodes.InternalServerError,
                entity = s"Error found for post")
          }
        }
      }
    }
  }
}
