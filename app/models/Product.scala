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
      case id~name~price~bought => Product(id, name, price, bought)
    }
  }

  def findById(id: Long): Option[Product] = {
    DB.withConnection { implicit connection =>
      SQL("select * from product where id = {id}").on(
        'id -> id
      ).as(simple.singleOpt)
    }
  }
  
  def findByName(name: String): Option[Product] = {
    DB.withConnection { implicit connection =>
      SQL("select * from product where name = {name}").on('name -> name).as(simple.singleOpt)
    }
  }

  def findAll: Seq[Product] = {
    DB.withConnection { implicit connection =>
      SQL("select * from product").as(simple *)
    }
  }
   
  def create(name: String, price: Double, bought: Double) = {
    DB.withConnection { implicit c =>
      SQL("""insert into product (name, price, bought)
                          values ({name}, {price}, {bought})""").on(
        'name -> name,
        'price -> price,
        'bought -> bought).executeUpdate()
    }
  }
  
  def update(id: Long, name: String, price: Double, bought: Double) = {
    DB.withConnection { implicit c =>
      SQL("""update product set (name, price, bought) = ({name}, {price}, {bought}) where id = {id}""").on(
        'name -> name,
        'price -> price,
        'bought -> bought,
        'id -> id).executeUpdate()
    }
  }

  def delete(id: Long) {
    Barcodes.delete(id)
    Purchase.delete(id)
    DB.withConnection { implicit c =>
      SQL("delete from product where id = {id}").on(
        'id -> id).executeUpdate()
    }
  }
}