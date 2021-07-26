package models

import play.api.db._
import anorm._
import anorm.SqlParser._
import scala.language.postfixOps
import org.joda.time.DateTime
import java.util.Date

import javax.inject.Inject
import javax.inject.Provider

case class Purchase(date: DateTime, product: Product, quantity: Long)

class Purchases @Inject() (db: Database, productsProvider: Provider[Products]) {
  lazy val products = productsProvider.get()

  val simple = {
    (get[Date]("purchase_date") ~
      get[Long]("product_id") ~
      get[Long]("quantity") ~
      get[String]("mode")).map {
      case date ~ product_id ~ quantity ~ mode =>
        Purchase(
          new DateTime(date),
          products.findById(product_id, mode).get,
          quantity
        )
    }
  }

  def findAll(mode: String): Seq[Purchase] =
    db.withConnection { implicit connection =>
      SQL("select * from purchases where mode = {mode}")
        .on("mode" -> mode)
        .as(simple *)
    }

  def findAllSorted(mode: String): Seq[Purchase] =
    db.withConnection { implicit connection =>
      SQL(
        "select * from purchases where mode = {mode} order by purchase_date desc"
      ).on("mode" -> mode).as(simple *)
    }

  def findForDate(mode: String, date: DateTime): Seq[Purchase] =
    db.withConnection { implicit connection =>
      SQL(
        "select * from purchases where mode = {mode} and purchase_date = {purchase_date}"
      ).on("mode" -> mode, "purchase_date" -> date.toDate).as(simple *)
    }

  def findForDateAndProduct(
    mode: String,
    date: DateTime,
    product: Product
  ): Option[Purchase] =
    db.withConnection { implicit connection =>
      SQL(
        "select * from purchases where mode = {mode} and purchase_date = {purchase_date} and product_id = {product_id}"
      ).on(
          "mode"          -> mode,
          "purchase_date" -> date.toDate,
          "product_id"    -> product.id
        )
        .as(simple.singleOpt)
    }

  def create(date: DateTime, product_id: Long, quantity: Long, mode: String) =
    db.withConnection { implicit c =>
      SQL(
        """insert into purchases (purchase_date, product_id, quantity, mode)
                          values ({purchase_date}, {product_id}, {quantity}, {mode})"""
      ).on(
          "purchase_date" -> date.toDate,
          "product_id"    -> product_id,
          "quantity"      -> quantity,
          "mode"          -> mode
        )
        .executeUpdate()
    }

  def update(date: DateTime, product_id: Long, quantity: Long, mode: String) =
    db.withConnection { implicit c =>
      SQL(
        """update purchases set quantity = {quantity} where purchase_date = {purchase_date} and product_id = {product_id} and mode = {mode}"""
      ).on(
          "quantity"      -> quantity,
          "purchase_date" -> date.toDate,
          "product_id"    -> product_id,
          "mode"          -> mode
        )
        .executeUpdate()
    }

  def delete(date: DateTime, mode: String) =
    db.withConnection { implicit c =>
      SQL(
        "delete from purchases where purchase_date = {purchase_date} and mode = {mode}"
      ).on(
          "purchase_date" -> date.toDate,
          "mode"          -> mode
        )
        .executeUpdate()
    }

  def delete(date: DateTime, product_id: Long, mode: String) =
    db.withConnection { implicit c =>
      SQL(
        """delete from purchases where product_id = {product_id} and purchase_date = {purchase_date} and mode = {mode}"""
      ).on(
          "product_id"    -> product_id,
          "purchase_date" -> date.toDate,
          "mode"          -> mode
        )
        .executeUpdate()
    }

  def delete(product_id: Long, mode: String) =
    db.withConnection { implicit c =>
      SQL(
        """delete from purchases where product_id = {product_id} and mode = {mode}"""
      ).on(
          "product_id" -> product_id,
          "mode"       -> mode
        )
        .executeUpdate()
    }
}
