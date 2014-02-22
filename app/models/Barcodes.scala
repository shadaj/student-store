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
    get[Long]("product_id") map {
      case barcode~product_id => BarcodePair(barcode, Product.findById(product_id).get)
    }
  }

  def findByBarcode(barcode: String): Option[BarcodePair] = {
    DB.withConnection { implicit connection =>
      SQL("select * from barcode where barcode = {barcode}").on(
        'barcode -> barcode
      ).as(simple.singleOpt)
    }
  }
  
  def findAll: Seq[BarcodePair] = {
    DB.withConnection { implicit connection =>
      SQL("select * from barcode").as(simple *)
    }
  }
  
  def create(barcode: String, product_id: Long) = {
    DB.withConnection { implicit c =>
      SQL("""insert into barcode (barcode, product_id)
                          values ({barcode}, {product_id})""").on(
        'barcode -> barcode,
        'product_id -> product_id).executeUpdate()
    }
  }
  
  def delete(barcode: String) {
    DB.withConnection { implicit c =>
      SQL("delete from barcode where barcode = {barcode}").on(
        'barcode -> barcode).executeUpdate()
    }
  }
  
  def delete(productID: Long) {
    DB.withConnection { implicit c =>
      SQL("""delete from barcode where product_id = {product_id}""").on(
          'product_id -> productID).executeUpdate()
    }
  }
}