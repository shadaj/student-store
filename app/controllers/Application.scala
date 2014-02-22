package controllers

import java.io.File
import java.util.TimeZone
import org.joda.time.DateTime
import org.joda.time.LocalDateTime
import com.github.tototoshi.csv.CSVWriter

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

import models._
import views._

import org.joda.time.DateTimeZone

object Application extends Controller {
  def sameDate(a: DateTime, b: DateTime) = a.getDayOfMonth() == b.getDayOfMonth() && a.getMonthOfYear() == b.getMonthOfYear() && a.getYear() == b.getYear()

  def index = Redirect(routes.Application.scan)

  val scanForm = Form("barcode" -> nonEmptyText)
  def scan = IsAuthenticated { _ =>
    implicit request => {
      val today = new DateTime
      Ok(views.html.index(Purchase.findAll.filter(p => sameDate(p.date, today)).map(p => p.product.price * p.quantity).sum, Purchase.findAll.filter(p => sameDate(p.date, today))))
    }
  }

  def processBarcode = IsAuthenticated { _ =>
    { implicit request =>
      scanForm.bindFromRequest.fold(
        errors => Redirect(routes.Application.scan),
        barcode => {
          val today = new DateTime
          Barcodes.findByBarcode(barcode) match {
            case Some(bp) => {
              val product = bp.product
              Purchase.findAll.find(p => sameDate(today, p.date) && p.product == product) match {
                case Some(Purchase(_, _, quantity)) => {
                  Purchase.update(today.toDate(), product.id, quantity + 1)
                }

                case None => {
                  Purchase.create(today.toDate(), product.id, 1)
                }
              }
              
              Redirect(routes.Application.scan())
            }

            case None => Redirect(routes.Application.scan()).flashing("warning" -> "Barcode was not found")
          }
        })
    }
  }

  def manageProducts = IsAuthenticated { _ =>
    { implicit request =>
      Ok(views.html.manageProducts(Product.findAll))
    }
  }

  val createProductForm = Form(tuple("name" -> nonEmptyText, "price" -> nonEmptyText, "bought" -> nonEmptyText))
  def createProduct = IsAuthenticated { _ =>
    { implicit request =>
      createProductForm.bindFromRequest.fold(
        errors => Redirect(routes.Application.manageProducts),
        formValues => {
          val (name, price, bought) = formValues
          Product.create(name, price.toDouble, bought.toDouble)
          Redirect(routes.Application.manageProducts)
        })
    }
  }

  def deleteProduct(id: Long) = IsAuthenticated { _ =>
    { _ =>
      Product.delete(id)
      Redirect(routes.Application.manageProducts)
    }
  }
  
  def editProduct(id: Long) = IsAuthenticated { _ =>
    { _ =>
      Product.findById(id) match {
        case Some(product) => Ok(views.html.editProduct(product))
        case None => Redirect(routes.Application.manageProducts) 
      }
    }
  }
  
  def updateProduct(id: Long) = IsAuthenticated { _ =>
    { implicit request =>
      createProductForm.bindFromRequest.fold(
        errors => Redirect(routes.Application.manageProducts),
        formValues => {
          val (name, price, bought) = formValues
          Product.update(id, name, price.toDouble, bought.toDouble)
          Redirect(routes.Application.manageProducts)
        })
    }
  }

  def manageBarcodes = IsAuthenticated { _ =>
    { _ =>
      Ok(views.html.manageBarcodes(Barcodes.findAll, Product.findAll))
    }
  }

  val createBarcodeForm = Form(tuple("barcode" -> nonEmptyText, "product" -> nonEmptyText))
  def createBarcode = IsAuthenticated { _ =>
    { implicit request =>
      createBarcodeForm.bindFromRequest.fold(
        errors => Redirect(routes.Application.manageBarcodes),
        formValues => {
          val (barcode, product) = formValues
          val productObject = Product.findByName(product)
          Barcodes.create(barcode, productObject.get.id)
          Redirect(routes.Application.manageBarcodes)
        })
    }
  }

  def deleteBarcode(barcode: String) = IsAuthenticated { _ =>
    { _ =>
      Barcodes.delete(barcode)
      Redirect(routes.Application.manageBarcodes)
    }
  }

  def managePurchases = IsAuthenticated { _ =>
    { _ =>
      Ok(views.html.managePurchases(Purchase.findAll, Product.findAll))
    }
  }

  val addPurchaseForm = Form(tuple("date" -> jodaDate("MM/dd/yyyy"), "product" -> nonEmptyText, "quantity" -> nonEmptyText))
  def addPurchase = IsAuthenticated { _ =>
    { implicit request =>
      addPurchaseForm.bindFromRequest.fold(
        errors => Redirect(routes.Application.managePurchases),
        formValues => {
          val (date, product, quantity) = formValues
          if (quantity == "0") {
            Purchase.findAll.find(p => sameDate(p.date, date) && p.product == Product.findByName(product).get) match {
              case Some(p) => Purchase.delete(p.date.toDate, Product.findByName(product).get.id)
              case None =>
            }
          } else {
            Purchase.findAll.find(p => sameDate(p.date, date) && p.product == Product.findByName(product).get) match {
              case Some(_) => {
                Purchase.update(date.toDate, Product.findByName(product).get.id, quantity.toInt)
              }

              case None => {
                Purchase.create(date.toDate(), Product.findByName(product).get.id, quantity.toInt)
              }
            }
          }

          Redirect(routes.Application.managePurchases)
        })
    }
  }

  def export = IsAuthenticated { _ =>
    { _ =>
      Ok(views.html.export(Purchase.findAll.map(_.date.toString("MM/YYYY")).distinct))
    }
  }

  val getExportForm = Form("date" -> jodaDate("MM/YYYY"))
  def getExport = IsAuthenticated { _ =>
    { implicit request =>
      val moneyFormat = "%.2f"
      getExportForm.bindFromRequest.fold(
        errors => Redirect(routes.Application.export),
        date => {
          new File("./exports").mkdir()
          val file = new File("./exports/studentStoreExport.csv")
          val writer = CSVWriter.open(file)
          writer.writeRow(List("Student Store Purchases"))
          writer.writeRow(List())
          implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
          var overallTotal = 0D
          Purchase.findAll.filter(p => p.date.getMonthOfYear() == date.getMonthOfYear() && p.date.getYear() == p.date.getYear()).
            groupBy(_.date).toSeq.sortBy(_._1).foreach {
              case (date, purchases) =>
                writer.writeRow(List(s"Total for day ${date.monthOfYear.get}/${date.dayOfMonth.getAsText}/${date.year.getAsText}", "Item", "Price", "Unit Sold", "Money Earned", "Price Bought", "Profit", "Total Profit"))
                var total = 0D
                purchases.foreach {
                  case Purchase(_, product, quantity) =>
                    val moneyEarned = product.price * quantity
                    val individualProfit = product.price - product.bought
                    val totalProfit = individualProfit * quantity

                    total += moneyEarned
                    writer.writeRow(List("", product.name, "$" + product.price, quantity, "$" + moneyFormat.format(moneyEarned), "$" + product.bought, "$" + moneyFormat.format(individualProfit), "$" + moneyFormat.format(totalProfit)))
                }

                writer.writeRow(List("", "", "", "", "", "", "", "", "Total Earned: $" + moneyFormat.format(total)))

                overallTotal += total
            }

          writer.writeRow(Seq("Total for all days: $" + moneyFormat.format(overallTotal)))
          writer.close()
          Ok.sendFile(file)
        })
    }
  }

  val loginForm = Form(
    tuple(
      "user" -> text,
      "password" -> text))

  def login = Action {
    Ok(views.html.loginPage())
  }

  def authenticate = Action { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => Redirect(routes.Application.login),
      user => {
        if (user._1 == "***REMOVED***" && user._2 == "***REMOVED***") {
          Redirect(routes.Application.scan).withSession("user" -> user._1)
        } else {
          Redirect(routes.Application.login)
        }
      })
  }

  def logout = Action {
    Redirect(routes.Application.login).withNewSession
  }

  private def username(request: RequestHeader) = request.session.get("user")

  private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.login)

  def IsAuthenticated(f: => String => Request[AnyContent] => Result) =
    Security.Authenticated(username, onUnauthorized) { user =>
      Action(request => f(user)(request))
    }
}