package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import models._
import views._
import org.joda.time.DateTime
import org.joda.time.LocalDateTime
import java.io.File
import com.github.tototoshi.csv.CSVWriter

object Application extends Controller {

  def index = Redirect(routes.Application.scan)

  // def homework = IsAuthenticated { username =>
  //   _ =>
  //     User.findByEmail(username).map { user =>
  //       Ok(views.html.index(Homework.all(), homeworkForm, user))
  //     }.getOrElse(Redirect(routes.Application.login))
  // }

  // val homeworkForm = Form(tuple(
  //   "teacher" -> nonEmptyText,
  //   "subject" -> nonEmptyText,
  //   "due" -> jodaDate("MM/dd/yyyy"),
  //   "assignment" -> nonEmptyText))

  // def newTask = Action { implicit request =>
  //   homeworkForm.bindFromRequest.fold(
  //     errors => {
  //       Redirect(routes.Application.homework)
  //     },
  //     value => {
  //       val (teacher, subject, due, assignment) = value
  //       Homework.create(teacher, subject, due, assignment)
  //       Redirect(routes.Application.homework)
  //     })
  // }

  // def deleteTask(id: Long) = Action {
  //   Homework.delete(id)
  //   Redirect(routes.Application.homework)
  // }

  val scanForm = Form("barcode" -> nonEmptyText)
  def scan = IsAuthenticated { _ =>
    _ => {
      Ok(views.html.index())
    }
  }

  def processBarcode = IsAuthenticated { _ => { implicit request =>
    scanForm.bindFromRequest.fold(
      errors => Redirect(routes.Application.scan),
      barcode => {
        val today = (new LocalDateTime).toLocalDate().toDateTimeAtStartOfDay()
        val product = Barcodes.findByBarcode(barcode).get.product
        Purchase.findAll.find(p => p.date == today && p.product == product) match {
          case Some(Purchase(_, _, quantity)) => {
            Purchase.update(today.toDate(), product.id, quantity + 1)
          }

          case None => {
            Purchase.create(today.toDate(), product.id, 1)
          }
        }

        Redirect(routes.Application.scan())
      })
  }}

  def manageProducts = IsAuthenticated { _ => { implicit request =>
    Ok(views.html.manageProducts(Product.findAll))
  }}

  val createProductForm = Form(tuple("name" -> nonEmptyText, "price" -> nonEmptyText, "bought" -> nonEmptyText))
  def createProduct = IsAuthenticated { _ => { implicit request =>
    createProductForm.bindFromRequest.fold(
      errors => Redirect(routes.Application.manageProducts),
      formValues => {
        val (name, price, bought) = formValues
        Product.create(name, price.toDouble, bought.toDouble)
        Redirect(routes.Application.manageProducts)
      })
  }}

  def deleteProduct(id: Long) = IsAuthenticated { _ => { _ =>
    Product.delete(id)
    Redirect(routes.Application.manageProducts)
  }}

  def manageBarcodes = IsAuthenticated { _ => { _ =>
    Ok(views.html.manageBarcodes(Barcodes.findAll, Product.findAll))
  }}

  val createBarcodeForm = Form(tuple("barcode" -> nonEmptyText, "product" -> nonEmptyText))
  def createBarcode = IsAuthenticated { _ => { implicit request =>
    createBarcodeForm.bindFromRequest.fold(
      errors => Redirect(routes.Application.manageBarcodes),
      formValues => {
        val (barcode, product) = formValues
        val productObject = Product.findByName(product)
        Barcodes.create(barcode, productObject.get.id)
        Redirect(routes.Application.manageBarcodes)
      })
  }}

  def deleteBarcode(barcode: String) = IsAuthenticated { _ => { _ =>
    Barcodes.delete(barcode)
    Redirect(routes.Application.manageBarcodes)
  }}

  def managePurchases = IsAuthenticated { _ => { _ =>
    Ok(views.html.managePurchases(Purchase.findAll, Product.findAll))
  }}

  val addPurchaseForm = Form(tuple("date" -> jodaDate("MM/dd/yyyy"), "product" -> nonEmptyText, "quantity" -> nonEmptyText))
  def addPurchase = IsAuthenticated { _ => { implicit request =>
    addPurchaseForm.bindFromRequest.fold(
      errors => Redirect(routes.Application.managePurchases),
      formValues => {
        val (date, product, quantity) = formValues
        if (quantity == "0") {
          Purchase.delete(date.toDate, Product.findByName(product).get.id)
        } else {
          Purchase.findAll.find(p => p.date == date && p.product == Product.findByName(product).get) match {
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
  }}

  def export = IsAuthenticated { _ => { _ =>
    Ok(views.html.export(Purchase.findAll.map(_.date.toString("MM/YYYY")).distinct))
  }}

  val getExportForm = Form("date" -> jodaDate("MM/YYYY"))
  def getExport = IsAuthenticated { _ => { implicit request =>
    val moneyFormat = "%.2f"
    getExportForm.bindFromRequest.fold(
      errors => Redirect(routes.Application.export),
      date => {
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
  }}

  // def settings = IsAuthenticated { username =>
  //   _ => {
  //     User.findByEmail(username).map { user =>
  //       Ok(views.html.settings())
  //     }.getOrElse(Redirect(routes.Application.login))
  //   }
  // }

  // def teacherManage = IsAuthenticated { username =>
  //   _ =>
  //     User.findByEmail(username).map { user =>
  //       val cleanedTeachers = user.teachers.filter(_ != "") //Remove blank teacher that comes from empty SQL entry
  //       Ok(views.html.teacherManage(cleanedTeachers, School.findByName(user.school).get.teachers))
  //     }.getOrElse(Redirect(routes.Application.login))
  // }

  // val addTeacherForm = Form("name" -> nonEmptyText)

  // def addTeacher = IsAuthenticated { username =>
  //    implicit request => {
  //     addTeacherForm.bindFromRequest.fold(
  //         errors => Redirect(routes.Application.teacherManage),
  //         teacherName => {
  //           User.addTeacher(username, teacherName)
  //           Redirect(routes.Application.teacherManage)
  //         }
  //     )
  //   }
  // }

  // def removeTeacher(name: String) = IsAuthenticated { username =>
  //    request => {
  //     User.removeTeacher(username, name)
  //     Redirect(routes.Application.teacherManage)
  //   }
  // }

  // -- Authentication

  val loginForm = Form(
    tuple(
      "user" -> text,
      "password" -> text))

  /**
   * Login page.
   */
   def login = Action {
     Ok(views.html.login())
   }

  // val registerForm = Form(
  //   tuple(
  //     "name" -> text,
  //     "email" -> text,
  //     "knownschool" -> text,
  //     "otherschool" -> text,
  //     "password" -> text))

  // def register = Action { implicit request =>
  //   registerForm.bindFromRequest.fold(
  //     formWithErrors => Redirect(routes.Application.homework),
  //     user => {
  //       val (name, email, knownschool, otherschool, password) = user
  //       if (knownschool == "Other") {
  //         School.create(School(otherschool, Nil))
  //         User.create(User(name, email, password, otherschool, Nil))
  //       } else {
  //         User.create(User(name, email, password, knownschool, Nil))
  //       }
  //       Redirect(routes.Application.homework).withSession("email" -> user._2)
  //     })
  // }

  // def delete = IsAuthenticated { username =>
  //   _ =>
  //     User.findByEmail(username).map { user =>
  //       User.delete(user)
  //       Ok(views.html.index(Homework.all(), homeworkForm, user))
  //       Redirect(routes.Application.login).withNewSession.flashing(
  //         "success" -> "Your account has been deleted")
  //     }.getOrElse(Redirect(routes.Application.login))
  // }

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

  // /**
  //  * Logout and clean the session.
  //  */
   def logout = Action {
     Redirect(routes.Application.login).withNewSession
   }

  // /**
  //  * Retrieve the connected user email.
  //  */
   private def username(request: RequestHeader) = request.session.get("user")

  // /**
  //  * Redirect to login if the user in not authorized.
  //  */
   private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.login)

  // // --

  // /**
  //  * Action for authenticated users.
  //  */
   def IsAuthenticated(f: => String => Request[AnyContent] => Result) =
     Security.Authenticated(username, onUnauthorized) { user =>
       Action(request => f(user)(request))
     }
}