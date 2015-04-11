package com.ponkotuy.parser

import java.io.ByteArrayOutputStream

import com.ponkotuy.data._
import com.ponkotuy.data.master._
import com.ponkotuy.http.MFGHttp
import com.ponkotuy.tool.{Checksum, TempFileTool}
import com.ponkotuy.util.Log
import com.ponkotuy.value.KCServer
import org.jboss.netty.buffer.ChannelBuffer
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.Serialization.write

import scala.util.Try

/**
 * Date: 14/06/01.
 */
object Post extends Log {
  implicit val formats = DefaultFormats

  def master(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    masterShip(obj)
    masterMission(obj)
    masterSlotitem(obj)
    masterSType(obj)
  }

  def masterShip(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val masterGraph = MasterShipGraph.fromJson(obj \ "api_mst_shipgraph")
    val filenames = masterGraph.map(it => it.id -> it.filename).toMap
    val masterShip = MasterShip.fromJson(obj \ "api_mst_ship", filenames)
    val hash = Checksum.fromSeq(masterShip.map(_.base.name))
    val existingHash = MFGHttp.get("/master/ship/hash", 2).map(_.toLong)
    if(existingHash.exists(_ != hash)) {
      MFGHttp.masterPost("/master/ship", write(masterShip))
      println("Success sending MasterShip data")
    }
  }

  def masterMission(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val masterMission = MasterMission.fromJson(obj \ "api_mst_mission")
    val hash = Checksum.fromSeq(masterMission.map(_.name))
    val existingHash = MFGHttp.get("/master/mission/hash", 2).map(_.toLong)
    if(existingHash.exists(_ != hash)) {
      MFGHttp.masterPost("/master/mission", write(masterMission))
      println("Success sending MasterMission data")
    }
  }

  def masterSlotitem(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val masterSlotitem = MasterSlotItem.fromJson(obj \ "api_mst_slotitem")
    val hash = Checksum.fromSeq(masterSlotitem.map(_.name))
    val existingHash = MFGHttp.get("/master/slotitem/hash", 2).map(_.toLong)
    if(existingHash.exists(_ != hash)) {
      MFGHttp.masterPost("/master/slotitem", write(masterSlotitem))
      println("Success sending MasterSlotitem data")
    }
  }

  def masterSType(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val masterSType = MasterSType.fromJson(obj \ "api_mst_stype")
    val hash = Checksum.fromSeq(masterSType.map(_.name))
    val existingHash = MFGHttp.get("/master/stype/hash", 2).map(_.toLong)
    if(existingHash.exists(_ != hash)) {
      MFGHttp.masterPost("/master/stype", write(masterSType))
      println("Success sending MasterStype data")
    }
  }

  var basicMessage = false
  def basic(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val basic = Basic.fromJSON(obj)
    val status = MFGHttp.post("/basic", write(basic))
    if(!basicMessage && (status / 100) == 2) {
      basicMessage = true
      printBasicMessage()
    }
    println(basic.summary)
  }

  private def printBasicMessage(): Unit = {
    println()
    println("============================================")
    if(auth2.isEmpty) println("パスワード認証無し") else println("パスワード認証に成功")
    println("MyFleetGirlsサーバへの接続に成功しました")
    println(s"URL: https://myfleet.moe/user/${auth.get.memberId}")
    println("============================================")
    println()
  }

  def admiralSettings(kcServer: KCServer)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    MFGHttp.post("/admiral_settings", write(kcServer))
    println(s"所属： ${kcServer.name}")
  }

  def ship(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val ship = Ship.fromJson(obj)
    MFGHttp.post("/ship", write(ship), ver = 2)
    println(s"所持艦娘数 -> ${ship.size}")
  }

  def update_ship(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val update = Ship.fromJson(obj \ "api_ship_data")
    MFGHttp.post("/update_ship", write(update))
  }

  def ndock(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val docks = NDock.fromJson(obj)
    MFGHttp.post("/ndock", write(docks))
    docks.filterNot(_.shipId == 0).map(_.summary).foreach(println)
  }

  def material(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val material = Material.fromJson(obj)
    MFGHttp.post("/material", write(material))
    println(material.summary)
  }

  def slotitem(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val items = SlotItem.fromJson(obj)
    MFGHttp.post("/slotitem", write(items))
    println(s"所持装備数 -> ${items.size}")
  }

  def book(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val books = Book.fromJson(obj)
    if(books.isEmpty) return
    books.head match {
      case _: ShipBook => MFGHttp.post("/book/ship", write(books))
      case _: ItemBook => MFGHttp.post("/book/item", write(books))
    }
  }

  def mapinfo(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val maps = MapInfo.fromJson(obj)
    MFGHttp.post("/mapinfo", write(maps))
  }

  def questlist(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    val qList = QuestList.fromJson(obj)
    if (qList.nonEmpty) {
      MFGHttp.post("/questlist", write(qList))
    }
  }

  def swfShip(q: Query)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    parseKey(q.toString).filterNot(MFGHttp.existsImage).foreach { key =>
      val swf = allRead(q.res.getContent)
      TempFileTool.save(swf, "swf") { file =>
        MFGHttp.postFile("/swf/ship/" + key, "image")(file)
      }
    }
  }

  def mp3kc(q: Query)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    SoundUrlId.parseURL(q.toString).filterNot(MFGHttp.existsSound).foreach { case SoundUrlId(shipKey, soundId) =>
      val sound = allRead(q.res.getContent)
      TempFileTool.save(sound, "mp3") { file =>
        MFGHttp.postFile(s"/mp3/kc/${shipKey}/${soundId}", "sound")(file)
      }
    }
  }

  def remodelSlot(obj: JValue, req: Map[String, String])(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit =
    Remodel.fromJson(obj, req).foreach { remodel =>
      MFGHttp.post("/remodel", write(remodel))
    }

  private def parseKey(str: String): Option[String] =
    Try {
      val filename = str.split('/').last
      filename.takeWhile(_ != '.')
    }.toOption

  private def allRead(cb: ChannelBuffer): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    cb.getBytes(0, baos, cb.readableBytes())
    baos.toByteArray
  }

  def rankingList(obj: JValue)(implicit auth: Option[Auth], auth2: Option[MyFleetAuth]): Unit = {
    auth.map(_.memberId).orElse(auth2.map(_.id)).foreach { memberId =>
      Ranking.fromJson(obj).filter(_.memberId == memberId).foreach { rank =>
        MFGHttp.post("/ranking", write(rank))
      }
    }
  }
}

case class SoundUrlId(shipKey: String, soundId: Int)

object SoundUrlId {
  val pattern = """.*/kcs/sound/kc([a-z]+)/(\d+).mp3""".r

  def parseURL(url: String): Option[SoundUrlId] = {
    url match {
      case pattern(ship, sound) => Try { SoundUrlId(ship, sound.toInt) }.toOption
      case _ => None
    }
  }
}
