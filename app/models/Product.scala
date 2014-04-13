package models

import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._

import scala.language.postfixOps

case class Product(id: Long, name: String, price: Double, bought: Double)

object Product {
  val simple = {
    get[Long]("id") ~
      get[String]("name") ~
      get[Double]("price") ~
      get[Double]("bought") map {
      case id ~ name ~ price ~ bought => Product(id, name, price, bought)
    }
  }

  def findById(id: Long, mode: String): Option[Product] = {
    DB.withConnection {
      implicit connection =>
        SQL("select * from product where id = {id} and mode = {mode}").on(
          'id -> id,
          'mode -> mode
        ).as(simple.singleOpt)
    }
  }

  def findByName(name: String, mode: String): Option[Product] = {
    DB.withConnection {
      implicit connection =>
        SQL("select * from product where name = {name} and mode = {mode}").on('name -> name, 'mode -> mode).as(simple.singleOpt)
    }
  }

  def findAll(mode: String): Seq[Product] = {
    DB.withConnection {
      implicit connection =>
        SQL("select * from product where mode = {mode}").on('mode -> mode).as(simple *)
    }
  }

  def create(name: String, price: Double, bought: Double, mode: String) = {
    DB.withConnection {
      implicit c =>
        SQL( """insert into product (name, price, bought, mode)
                          values ({name}, {price}, {bought}, {mode})""").on(
            'name -> name,
            'price -> price,
            'bought -> bought,
            'mode -> mode).executeUpdate()
    }
  }

  def update(product_id: Long, name: String, price: Double, bought: Double, mode: String) = {
    DB.withConnection {
      implicit c =>
        SQL( """update product set (name, price, bought) = ({name}, {price}, {bought}) where id = {id} and mode = {mode}""").on(
          'name -> name,
          'price -> price,
          'bought -> bought,
          'id -> product_id,
          'mode -> mode).executeUpdate()
    }
  }

  def delete(product_id: Long, mode: String) {
    Barcodes.delete(product_id, mode)
    Purchase.delete(product_id, mode)
    DB.withConnection {
      implicit c =>
        SQL("delete from product where id = {id} and mode = {mode}").on(
          'id -> product_id,
          'mode -> mode).executeUpdate()
    }
  }
}