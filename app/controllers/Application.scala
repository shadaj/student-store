package controllers

import java.io.File
import org.joda.time.DateTime
import com.github.tototoshi.csv.CSVWriter

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.JodaForms._

import models._
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class Application @Inject() (components: ControllerComponents, purchases: Purchases, Barcodes: Barcodes, Product: Products)(implicit ec: ExecutionContext) extends AbstractController(components) {
  def removeTime(date: DateTime): DateTime = {
    date.withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
  }

  def index = Redirect(routes.Application.scan)

  val scanForm = Form("barcode" -> nonEmptyText)

  def scan = IsAuthenticated {
    mode =>
      implicit request => {
        val today = new DateTime
        val date = removeTime(today)
        val purchasesToday = purchases.findForDate(mode, date)
        Ok(views.html.index(purchasesToday.map(p => p.product.price * p.quantity).sum, purchasesToday, mode))
      }
  }

  def processBarcode = IsAuthenticated {
    mode => {
      implicit request =>
        scanForm.bindFromRequest().fold(
          errors => Redirect(routes.Application.scan),
          barcode => {
            val today = new DateTime
            val date = removeTime(today)
            Barcodes.findByBarcode(barcode, mode) match {
              case Some(BarcodePair(_, product)) => {
                purchases.findForDateAndProduct(mode, date, product) match {
                  case Some(Purchase(_, _, quantity)) => {
                    purchases.update(date, product.id, quantity + 1, mode)
                  }

                  case None => {
                    purchases.create(date, product.id, 1, mode)
                  }
                }

                Redirect(routes.Application.scan)
              }

              case None => Redirect(routes.Application.scan).flashing("warning" -> "Barcode was not found")
            }
          })
    }
  }

  val switchModeForm = Form("mode" -> nonEmptyText)
  def switchMode = IsAuthenticated {
    oldMode =>
      implicit request => {
        switchModeForm.bindFromRequest().fold(
          errors => Redirect(routes.Application.scan),
          newMode => {
            Redirect(routes.Application.scan).withSession("mode" -> newMode)
          })
      }
  }

  def manageProducts = IsAuthenticated {
    mode => {
      implicit request =>
        Ok(views.html.manageProducts(Product.findAll(mode), mode))
    }
  }

  val createProductForm = Form(tuple("name" -> nonEmptyText, "price" -> nonEmptyText, "bought" -> nonEmptyText))

  def createProduct = IsAuthenticated {
    mode => {
      implicit request =>
        createProductForm.bindFromRequest().fold(
          errors => Redirect(routes.Application.manageProducts),
          formValues => {
            val (name, price, bought) = formValues
            Product.create(name, price.toDouble, bought.toDouble, mode)
            Redirect(routes.Application.manageProducts)
          })
    }
  }

  def deleteProduct(id: Long) = IsAuthenticated {
    mode => {
      _ =>
        Product.delete(id, mode)
        Redirect(routes.Application.manageProducts)
    }
  }

  def editProduct(id: Long) = IsAuthenticated {
    mode => {
      _ =>
        Product.findById(id, mode) match {
          case Some(product) => Ok(views.html.editProduct(product, mode))
          case None => Redirect(routes.Application.manageProducts)
        }
    }
  }

  def updateProduct(id: Long) = IsAuthenticated {
    mode => {
      implicit request =>
        createProductForm.bindFromRequest().fold(
          errors => Redirect(routes.Application.manageProducts),
          formValues => {
            val (name, price, bought) = formValues
            Product.update(id, name, price.toDouble, bought.toDouble, mode)
            Redirect(routes.Application.manageProducts)
          })
    }
  }

  def manageBarcodes = IsAuthenticated {
    mode => {
      _ =>
        Ok(views.html.manageBarcodes(Barcodes.findAll(mode), Product.findAll(mode), mode))
    }
  }

  val createBarcodeForm = Form(tuple("barcode" -> nonEmptyText, "product" -> nonEmptyText))

  def createBarcode = IsAuthenticated {
    mode => {
      implicit request =>
        createBarcodeForm.bindFromRequest().fold(
          errors => Redirect(routes.Application.manageBarcodes),
          formValues => {
            val (barcode, product) = formValues
            val productObject = Product.findByName(product, mode)
            Barcodes.create(barcode, productObject.get.id, mode)
            Redirect(routes.Application.manageBarcodes)
          })
    }
  }

  def deleteBarcode(barcode: String) = IsAuthenticated {
    mode => {
      _ =>
        Barcodes.delete(barcode, mode)
        Redirect(routes.Application.manageBarcodes)
    }
  }

  def managePurchases = IsAuthenticated {
    mode => {
      _ =>
        Ok(views.html.managePurchases(purchases.findAllSorted(mode), Product.findAll(mode), mode))
    }
  }

  val addPurchaseForm = Form(tuple("date" -> jodaDate("MM/dd/yyyy"), "product" -> nonEmptyText, "quantity" -> number))

  def addPurchase = IsAuthenticated {
    mode => {
      implicit request =>
        addPurchaseForm.bindFromRequest().fold(
          errors => Redirect(routes.Application.managePurchases),
          formValues => {
            val (date, product, quantity) = formValues
            if (quantity == 0) {
              purchases.findForDateAndProduct(mode, date, Product.findByName(product, mode).get) match {
                case Some(p) => purchases.delete(p.date, Product.findByName(product, mode).get.id, mode)
                case None =>
              }
            } else {
              purchases.findForDateAndProduct(mode, date, Product.findByName(product, mode).get) match {
                case Some(_) => {
                  purchases.update(date, Product.findByName(product, mode).get.id, quantity, mode)
                }

                case None => {
                  purchases.create(date, Product.findByName(product, mode).get.id, quantity, mode)
                }
              }
            }

            Redirect(routes.Application.managePurchases)
          })
    }
  }

  def export = IsAuthenticated {
    mode => {
      _ =>
        Ok(views.html.export(purchases.findAll(mode).map(_.date.toString("MM/YYYY")).distinct, mode))
    }
  }

  val getExportForm = Form("date" -> jodaDate("MM/YYYY"))

  def getExport = IsAuthenticated {
    mode => {
      implicit request =>
        val moneyFormat = "%.2f"
        getExportForm.bindFromRequest().fold(
          errors => Redirect(routes.Application.export),
          date => {
            new File("./exports").mkdir()
            val file = new File("./exports/studentStoreExport.csv")
            val writer = CSVWriter.open(file)
            writer.writeRow(List("Student Store Purchases"))
            writer.writeRow(List())
            implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
            var overallTotal = 0D
            purchases.findAll(mode).filter(p => p.date.getMonthOfYear() == date.getMonthOfYear() && p.date.getYear() == p.date.getYear()).
              groupBy(_.date).toSeq.sortBy(_._1).foreach {
              case (date, found) =>
                writer.writeRow(List(s"Total for day ${date.monthOfYear.get}/${date.dayOfMonth.get}/${date.year.get}", "Item", "Price", "Unit Sold", "Money Earned", "Price Bought", "Profit", "Total Profit"))
                var total = 0D
                found.foreach {
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

  def authenticate = Action {
    implicit request =>
      loginForm.bindFromRequest().fold(
        formWithErrors => Redirect(routes.Application.login),
        user => {
          if (user._1 == "***REMOVED***" && user._2 == "***REMOVED***") {
            Redirect(routes.Application.scan).withSession("mode" -> "Student Store")
          } else {
            Redirect(routes.Application.login)
          }
        })
  }

  def logout = Action {
    Redirect(routes.Application.login).withNewSession
  }

  private def mode(request: RequestHeader) = request.session.get("mode")

  private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.login)

  def IsAuthenticated(f: => String => Request[AnyContent] => Result) =
    Security.Authenticated(mode, onUnauthorized) {
      mode =>
        Action(request => f(mode)(request))
    }
}