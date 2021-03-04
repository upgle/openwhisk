package org.apache.openwhisk.core.scheduler.grpc

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import org.apache.openwhisk.common.Logging
import org.apache.openwhisk.core.connector.{ActivationMessage, Message}
import org.apache.openwhisk.core.entity.{DocRevision, FullyQualifiedEntityName}
import org.apache.openwhisk.core.scheduler.queue._
import org.apache.openwhisk.grpc.{ActivationService, FetchRequest, FetchResponse, RescheduleRequest, RescheduleResponse}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

class ActivationServiceImpl()(implicit actorSystem: ActorSystem, logging: Logging) extends ActivationService {
  implicit val requestTimeout: Timeout = Timeout(5.seconds)
  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher

  override def rescheduleActivation(request: RescheduleRequest): Future[RescheduleResponse] = {
    logging.info(this, s"Try to reschedule activation ${request.invocationNamespace} ${request.fqn} ${request.rev}")
    Future(for {
      fqn <- FullyQualifiedEntityName.parse(request.fqn)
      rev <- DocRevision.parse(request.rev)
      msg <- ActivationMessage.parse(request.activationMessage)
    } yield (fqn, rev, msg)).flatMap(Future.fromTry) flatMap { res =>
      {
        val key = res._1.toDocId.asDocInfo(res._2)
        QueuePool.get(MemoryQueueKey(request.invocationNamespace, key)) match {
          case Some(queueValue) =>
            // enqueue activation message to reschedule
            logging.info(
              this,
              s"Enqueue activation message to reschedule ${request.invocationNamespace} ${request.fqn} ${request.rev}")
            queueValue.queue ? res._3
            Future.successful(RescheduleResponse(isRescheduled = true))
          case None =>
            logging.error(this, s"Queue not found for ${request.invocationNamespace} ${request.fqn} ${request.rev}")
            Future.successful(RescheduleResponse())
        }
      }
    }
  }

  override def fetchActivation(request: FetchRequest): Future[FetchResponse] = {
    Future(for {
      fqn <- FullyQualifiedEntityName.parse(request.fqn)
      rev <- DocRevision.parse(request.rev)
    } yield (fqn, rev)).flatMap(Future.fromTry) flatMap { res =>
      val key = res._1.toDocId.asDocInfo(res._2)
      QueuePool.get(MemoryQueueKey(request.invocationNamespace, key)) match {
        case Some(queueValue) =>
          (queueValue.queue ? GetActivation(
            res._1,
            request.containerId,
            request.warmed,
            request.lastDuration,
            request.alive))
            .mapTo[ActivationResponse]
            .map { response =>
              FetchResponse(response.serialize)
            }
            .recover {
              case t: Throwable =>
                logging.error(this, s"Failed to get message from QueueManager, error: ${t.getMessage}")
                FetchResponse(ActivationResponse(Left(NoActivationMessage())).serialize)
            }
        case None =>
          if (QueuePool.keys.exists { mkey =>
                mkey.invocationNamespace == request.invocationNamespace && mkey.docInfo.id == key.id
              })
            Future.successful(FetchResponse(ActivationResponse(Left(ActionMismatch())).serialize))
          else
            Future.successful(FetchResponse(ActivationResponse(Left(NoMemoryQueue())).serialize))
      }
    }
  }
}

object ActivationServiceImpl {

  def apply()(implicit actorSystem: ActorSystem, logging: Logging) =
    new ActivationServiceImpl()
}

case class GetActivation(action: FullyQualifiedEntityName,
                         containerId: String,
                         warmed: Boolean,
                         lastDuration: Option[Long],
                         alive: Boolean = true)
case class ActivationResponse(message: Either[MemoryQueueError, ActivationMessage]) extends Message {
  override def serialize = ActivationResponse.serdes.write(this).compactPrint
}

object ActivationResponse extends DefaultJsonProtocol {

  private implicit val noMessageSerdes = NoActivationMessage.serdes
  private implicit val noQueueSerdes = NoMemoryQueue.serdes
  private implicit val mismatchSerdes = ActionMismatch.serdes
  private implicit val messageSerdes = ActivationMessage.serdes
  private implicit val memoryqueueuErrorSerdes = MemoryQueueErrorSerdes.memoryQueueErrorFormat

  def parse(msg: String) = Try(serdes.read(msg.parseJson))

  implicit def rootEitherFormat[A: RootJsonFormat, B: RootJsonFormat] =
    new RootJsonFormat[Either[A, B]] {
      val format = DefaultJsonProtocol.eitherFormat[A, B]

      def write(either: Either[A, B]) = format.write(either)

      def read(value: JsValue) = format.read(value)
    }

  type ActivationResponse = Either[MemoryQueueError, ActivationMessage]
  implicit val serdes = jsonFormat(ActivationResponse.apply _, "message")

}
