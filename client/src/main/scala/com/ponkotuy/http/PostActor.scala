package com.ponkotuy.http

import akka.actor.{Actor, Props}
import com.netaporter.uri.Uri
import com.ponkotuy.data.{Auth, MyFleetAuth}
import com.ponkotuy.parser.Query
import com.ponkotuy.restype.{Authentication, HttpPostable}
import com.ponkotuy.value.KCServer
import org.json4s._

import scala.collection.mutable

/**
 * @author ponkotuy
 * Date: 15/04/11.
 */
class PostActor extends Actor {
  import PostActor._

  val WaitTime = 100L // 送信後のWaitTime

  implicit private[this] var auth: Option[Auth] = None
  implicit private[this] var auth2: Option[MyFleetAuth] = None
  private[this] val queue = mutable.Queue[HttpPostable]() // 認証待ちQueue
  private[this] var isAuth = false

  override def receive = {
    case pos: HttpPostable =>
      if(isAuth) {
        MFGHttp.post(pos)
        pos.printMessage
        Thread.sleep(WaitTime)
      } else queue.enqueue(pos)
    case a: Authentication => // これが来たら認証待ちQueueを吐き出す
      auth = Some(a.auth)
      auth2 = a.auth2
      if(!isAuth) {
        isAuth = true
        queue.dequeueAll(_ => true).foreach(self ! _)
        printAuthMessage()
      }
  }
}

object PostActor {
  private def printAuthMessage()(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    println()
    println("============================================")
    if(auth2.isEmpty) println("パスワード認証無し") else println("パスワード認証に成功")
    println("MyFleetGirlsサーバへの接続に成功しました")
    println(s"URL: https://myfleet.moe/user/${auth.get.memberId}")
    println("============================================")
    println()
  }
}

class ControllerActor extends Actor {
  lazy val poster = context.actorOf(Props[PostActor], "postActor")

  override def receive = {
    case q: Query =>
      val typ = q.resType.get
      val req = q.reqMap
      val obj: JValue = {
        q.resJson match {
          case Left(value) => value
          case Right(message) =>
            println(message)
            JObject()
        }
      }
      typ.postables(req, obj).foreach(poster ! _)
  }
}

object Host {
  private[this] var kcServer: Option[KCServer] = None
  def get(): Option[KCServer] = kcServer
  def set(uri: Uri): Unit = {
    if(kcServer.isEmpty) {
      val opt = for {
        host <- uri.host
        server <- KCServer.fromIP(host)
      } yield server
      kcServer = opt
    }
  }
}
