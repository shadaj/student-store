package models

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

import scala.language.postfixOps

case class BarcodePair(barcode: String, product: Product)

object Barcodes {
  val simple = {
    get[String]("barcode") ~
      get[Long]("product_id") ~
      get[String]("mode") map {
      case barcode ~ product_id ~ mode => BarcodePair(barcode, Product.findById(product_id, mode).get)
    }
  }

  def findByBarcode(barcode: String, mode: String): Option[BarcodePair] = {
    DB.withConnection {
      implicit connection =>
        SQL("select * from barcode where barcode = {barcode} and mode = {mode}").on(
          'barcode -> barcode,
          'mode -> mode
        ).as(simple.singleOpt)
    }
  }

  def findAll(mode: String): Seq[BarcodePair] = {
    DB.withConnection {
      implicit connection =>
        SQL("select * from barcode where mode = {mode}").on('mode -> mode).as(simple *)
    }
  }

  def create(barcode: String, product_id: Long, mode: String) = {
    DB.withConnection {
      implicit c =>
        SQL( """insert into barcode (barcode, product_id, mode)
                          values ({barcode}, {product_id}, {mode})""").on(
            'barcode -> barcode,
            'product_id -> product_id,
            'mode -> mode).executeUpdate()
    }
  }

  def delete(barcode: String, mode: String) {
    DB.withConnection {
      implicit c =>
        SQL("delete from barcode where barcode = {barcode} and mode = {mode}").on(
          'barcode -> barcode,
          'mode -> mode).executeUpdate()
    }
  }

  def delete(product_id: Long, mode: String) {
    DB.withConnection {
      implicit c =>
        SQL( """delete from barcode where product_id = {product_id} and mode = {mode}""").on(
          'product_id -> product_id,
          'mode -> mode).executeUpdate()
    }
  }
}