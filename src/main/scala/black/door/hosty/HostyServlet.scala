package black.door.hosty

import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariDataSource
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import io.jsonwebtoken.impl.crypto.MacProvider
import org.asynchttpclient.DefaultAsyncHttpClient
import org.jooq.{Record, SQLDialect}
import org.jooq.impl.{DSL, SQLDataType}
import org.json4s.{DefaultFormats, Formats, _}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.jooq.impl.DSL._
import org.json4s.JsonDSL._

import scala.compat.java8.FutureConverters
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.compat.java8.OptionConverters._
import scala.compat.java8.StreamConverters._
import scala.concurrent.Await

object HostyServlet {
  private val jwtKey = MacProvider.generateKey(SignatureAlgorithm.HS512)
}

class HostyServlet extends HostyStack with CorsSupport with JacksonJsonSupport {
  import HostyServlet._

  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  val conf = ConfigFactory.load()
  val jdbcUrl = conf getString "jdbc.url"
  val jdbcUsername = conf getString "jdbc.username"
  val jdbcPassword = conf getString "jdbc.password"

  val dockerUrl = conf getString "docker.url"

  // todo resource management
  val hikari = new HikariDataSource
  hikari.setJdbcUrl(jdbcUrl)
  hikari.setUsername(jdbcUsername)
  hikari.setPassword(jdbcPassword)

  val httpClient = new DefaultAsyncHttpClient

  before() {
    contentType = formats("json")
  }

  //cors support
  options("/*"){
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
  }

  get("/") {
    <html>
      <body>
        <h1>Hello, world!</h1>
        Say <a href="hello-scalate">hello to Scalate</a>.
      </body>
    </html>
  }

  post("/token") {
    val sql = DSL.using(hikari.getConnection, SQLDialect.POSTGRES)
    try{
      val email = params("username")
      val password = params("password")

      val passwordOption = sql.select(field("password"), field("salt"))
        .from(table("users"))
        .where(field("email", SQLDataType.VARCHAR).eq(email))
        .fetchOptional
        .asScala
        //.map r => r.

    }finally {
      sql.close()
    }
  }

  get("/services"){
    val sql = DSL.using(hikari.getConnection, SQLDialect.POSTGRES)

    // region parse token todo what the lifecycle is going on here? can this region be moved to a before()?
    val currentUser = Jwts.parser()
      .setSigningKey(jwtKey)
      .parseClaimsJws(request.header("Authorization")
        .map(h => h.replaceFirst("Bearer ", ""))
        .getOrElse("")
      ).getBody
      .getSubject
    //endregion

    try {
      val serviceNames = Set(sql.select(field("name", SQLDataType.VARCHAR))
        .from(table("services"))
        .where(field("owner", SQLDataType.VARCHAR).eq(currentUser))
        .stream().toScala[Stream]
        .map(record => record.value1()))

      /*return*/ serviceNames.toSeq.map(name =>
        httpClient.prepareGet(s"$dockerUrl/services/$name")
          .execute
          .toCompletableFuture.toScala)
        .map(future => future map(response => parse(response.getResponseBody)))
        .map(f => Await.result(f, 500 millis)) // todo UUUUUGH, handle failures, bad response codes, transform Seq[Future] to Future[Seq]

    }finally {
      sql.close()
    }
  }

  post("/services"){
    // region parse token todo what the lifecycle is going on here? can this region be moved to a before()?
    /*
    val currentUser = Jwts.parser()
      .setSigningKey(jwtKey)
      .parseClaimsJws(request.header("Authorization")
        .map(h => h.replaceFirst("Bearer ", ""))
        .getOrElse("")
      ).getBody
      .getSubject
      */
    //endregion

    val sql = DSL.using(hikari.getConnection, SQLDialect.POSTGRES)
    try{

      val body = parse(request.body)

      val json =
        ("Name" -> "FromBody") ~
        ("TaskTemplate" -> (
          "ContainerSpec" ->
            ("Image" -> "frombody") ~
            ("Args" -> List("frombody")) ~
            ("Env" -> List("frombody"))
        )
          ) ~
          ("Mode" -> ("Replicated" -> ("Replicas" -> 5/*frombody*/))) ~
          ("EndpointSpec" -> ("Ports" -> List(
            ("Protocol" -> "tcp") ~ /* in body */
              ("PublishedPort" -> 8080) ~
              ("TargetPort" -> 80)
          )))

      Await.result(
      httpClient.preparePost(s"$dockerUrl/services/create")
          .setHeader("Content-Type", "application/json")
        .setBody(compact(render(json)))
        .execute.toCompletableFuture.toScala
        ,500 millis)

    }finally{
      sql.close()
    }
  }

}
