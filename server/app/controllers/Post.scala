package controllers

import com.ponkotuy.data._
import com.ponkotuy.data.master.MasterRemodel
import com.ponkotuy.value.KCServer
import controllers.Common._
import models.db
import play.api.mvc._
import scalikejdbc.{AutoSession, DBSession}

/**
 *
 * @author ponkotuy
 * Date: 14/02/21.
 */
object Post extends Controller {
  def basic = authAndParse[Basic] { case (auth, basic) =>
    val isChange = !db.Basic.findByUser(auth.id).exists(_.diff(basic) < 0.01)
    if(isChange) {
      db.Basic.create(basic, auth.id)
      Ok("Success")
    } else {
      Ok("No Change")
    }
  }

  def admiralSettings = authAndParse[KCServer] { case (auth, server) =>
    db.UserSettings.setBase(auth.id, server.number)
    Ok("Success")
  }

  def material = authAndParse[Material] { case (auth, material) =>
    val isChange = !db.Material.findByUser(auth.id).exists(_.diff(material) < 0.03)
    if(isChange) {
      db.Material.create(material, auth.id)
      Ok("Success")
    } else {
      Ok("No Change")
    }
  }

  def ship2 = authAndParse[List[Ship]] { case (auth, ships) =>
    db.Ship.deleteAllByUser(auth.id)
    db.Ship.bulkInsert(ships, auth.id)
    Ok("Success")
  }

  def updateShip() = authAndParse[List[Ship]] { case (auth, ships) =>
    db.Ship.bulkUpsert(ships, auth.id)
    Ok("Success")
  }

  def ndock = authAndParse[List[NDock]] { case (auth, docks) =>
    db.NDock.deleteAllByUser(auth.id)
    docks.foreach(dock => db.NDock.create(dock, auth.id))
    Ok("Success")
  }

  def createShip = authAndParse[CreateShipAndDock] { case (auth, CreateShipAndDock(ship, dock)) =>
    try {
      db.CreateShip.createFromKDock(ship, dock, auth.id)
    } catch {
      case e: Throwable =>
        Ok("Duplicate Entry")
    }
    Ok("Success")
  }

  def createShip2 = authAndParse[CreateShipWithId] { case (auth, CreateShipWithId(ship, id)) =>
    db.CreateShip.create(ship, auth.id, id)
    Ok("Success")
  }

  def createItem = authAndParse[CreateItem] { (auth, item) =>
    db.CreateItem.create(item, auth.id)
    for {
      id <- item.id
      slotitemId <- item.slotitemId
    } {
      db.SlotItem.create(auth.id, id, slotitemId)
    }
    Ok("Success")
  }

  def kdock = authAndParse[List[KDock]] { case (auth, docks) =>
    db.KDock.deleteByUser(auth.id)
    db.KDock.bulkInsert(docks.filterNot(_.completeTime == 0), auth.id)
    Ok("Success")
  }

  def deleteKDock() = authAndParse[DeleteKDock] { case (auth, kdock) =>
    db.KDock.destroy(auth.id, kdock.kDockId)
    Ok("Success")
  }

  def deckPort = authAndParse[List[DeckPort]] { case (auth, decks) =>
    try {
      db.DeckPort.deleteByUser(auth.id)
      db.DeckPort.bulkInsertEntire(decks, auth.id)
    } catch {
      case e: Exception => e.printStackTrace()
    }
    Ok("Success")
  }

  def shipBook = authAndParse[List[ShipBook]] { case (auth, ships) =>
    db.ShipBook.bulkUpsert(ships, auth.id)
    Ok("Success")
  }

  def itemBook = authAndParse[List[ItemBook]] { case (auth, items) =>
    db.ItemBook.bulkUpsert(items, auth.id)
    Ok("Success")
  }

  def mapInfo = authAndParse[List[MapInfo]] { case (auth, maps) =>
    db.MapInfo.deleteAllByUser(auth.id)
    db.MapInfo.bulkInsert(maps, auth.id)
    Ok("Success")
  }

  def slotItem = authAndParse[List[SlotItem]] { case (auth, items) =>
    db.SlotItem.deleteAllByUser(auth.id)
    db.SlotItem.bulkInsert(items, auth.id)
    Ok("Success")
  }

  def battleResult = authAndParse[(BattleResult, MapStart)] { case (auth, (result, map)) =>
    db.AGOProgress.incWithBattle(auth.id, result, map)
    db.BattleResult.create(result, map, auth.id)
    Ok("Success")
  }

  def mapStart = authAndParse[MapStart] { case (auth, mapStart) =>
    db.AGOProgress.incSortie(auth.id)
    Ok("Success")
  }

  def mapRoute = authAndParse[MapRoute] { case (auth, mapRoute) =>
    db.MapRoute.create(mapRoute, auth.id)
    Ok("Success")
  }

  def questlist = authAndParse[List[Quest]] { case (auth, quests) =>
    db.Quest.bulkUpsert(quests, auth.id)
    Ok("Success")
  }

  def remodelSlot() = authAndParse[RemodelSlotlist] { case (auth, request) =>
    db.RemodelSlot.bulkInsert(request, auth.id)
    Ok("Success")
  }

  def remodel() = authAndParse[Remodel] { case (auth, request) =>
    db.Remodel.create(request, auth.id)
    for {
      afterSlot <- request.afterSlot
      item <- db.SlotItem.find(request.slotId, auth.id)
    } {
      db.SlotItem(auth.id, item.id, item.slotitemId, item.locked, afterSlot.level).save()
    }
    Ok("Success")
  }

  def masterRemodel() = authAndParse[MasterRemodel] { case (auth, request) =>
    db.MasterRemodel.createFromData(request, auth.id)
    Ok("Success")
  }

  def ranking() = authAndParse[Ranking] { case (auth, request) =>
    db.Ranking.findNewest(auth.id) match {
      case None => insertRanking(auth, request)
      case Some(before) =>
        if(before.diff(request) > 0.0) insertRanking(auth, request) else Ok("No change")
    }
  }

  private def insertRanking(auth: db.Admiral, rank: Ranking)(implicit session: DBSession = AutoSession) = {
    db.Ranking.create(auth.id, rank.no, rank.rate, System.currentTimeMillis())
    Ok("Success")
  }
}
