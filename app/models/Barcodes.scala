package models

import play.api.db._

import anorm._
import anorm.SqlParser._

import scala.language.postfixOps
import javax.inject.Inject

case class BarcodePair(barcode: String, product: Product)

class Barcodes @Inject() (db: Database, products: Products) {
  val simple = {
    get[String]("barcode") ~
      get[Long]("product_id") ~
      get[String]("mode") map { case barcode ~ product_id ~ mode =>
        BarcodePair(barcode, products.findById(product_id, mode).get)
      }
  }

  def findByBarcode(barcode: String, mode: String): Option[BarcodePair] = {
    db.withConnection { implicit connection =>
      SQL("select * from barcode where barcode = {barcode} and mode = {mode}")
        .on(
          "barcode" -> barcode,
          "mode" -> mode
        )
        .as(simple.singleOpt)
    }
  }

  def findAll(mode: String): Seq[BarcodePair] = {
    db.withConnection { implicit connection =>
      SQL("select * from barcode where mode = {mode}")
        .on("mode" -> mode)
        .as(simple *)
    }
  }

  def create(barcode: String, product_id: Long, mode: String) = {
    db.withConnection { implicit c =>
      SQL("""insert into barcode (barcode, product_id, mode)
                          values ({barcode}, {product_id}, {mode})""")
        .on("barcode" -> barcode, "product_id" -> product_id, "mode" -> mode)
        .executeUpdate()
    }
  }

  def delete(barcode: String, mode: String) = {
    db.withConnection { implicit c =>
      SQL("delete from barcode where barcode = {barcode} and mode = {mode}")
        .on("barcode" -> barcode, "mode" -> mode)
        .executeUpdate()
    }
  }

  def delete(product_id: Long, mode: String) = {
    db.withConnection { implicit c =>
      SQL(
        """delete from barcode where product_id = {product_id} and mode = {mode}"""
      ).on("product_id" -> product_id, "mode" -> mode).executeUpdate()
    }
  }
}
