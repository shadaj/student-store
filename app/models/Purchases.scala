package models

import play.api.db._
import play.api.Play.current
import anorm._
import anorm.SqlParser._
import scala.language.postfixOps
import org.joda.time.DateTime
import java.util.Date
import org.joda.time.DateTimeZone
import java.util.TimeZone

case class Purchase(date: DateTime, product: Product, quantity: Long)

object Purchase {
  val simple = {
    get[Date]("purchase_date") ~
    get[Long]("product_id") ~
    get[Long]("quantity") map {
      case date~product_id~quantity => Purchase(new DateTime(date), Product.findById(product_id).get, quantity)
    }
  }
  
  def findAll: Seq[Purchase] = {
    DB.withConnection { implicit connection =>
      SQL("select * from purchases").as(simple *)
    }
  }
  
  def create(date: Date, product_id: Long, quantity: Long) = {
    DB.withConnection { implicit c =>
      SQL("""insert into purchases (purchase_date, product_id, quantity)
                          values ({purchase_date}, {product_id}, {quantity})""").on(
        'purchase_date -> date,
        'product_id -> product_id,
        'quantity -> quantity).executeUpdate()
    }
  }
  
  def update(date: Date, product_id: Long, newQuantity: Long) = {
    DB.withConnection { implicit c =>
      SQL("""update purchases set quantity = {quantity} where purchase_date = {date} and product_id = {product_id}""").on(
          'quantity -> newQuantity,
          'date -> date,
          'product_id -> product_id).executeUpdate()
    }
  }
  
  def delete(date: Date) {
    DB.withConnection { implicit c =>
      SQL("delete from purchases where purchase_date = {purchase_date}").on(
        'purchase_date -> date).executeUpdate()
    }
  }
  
  def delete(date: Date, productID: Long) {
    DB.withConnection { implicit c =>
      SQL("""delete from purchases where product_id = {product_id} and purchase_date = {date}""").on(
          'product_id -> productID, 'date -> date).executeUpdate()
    }
  }
  
  def delete(productID: Long) {
    DB.withConnection { implicit c =>
      SQL("""delete from purchases where product_id = {product_id}""").on(
          'product_id -> productID).executeUpdate()
    }
  }
}