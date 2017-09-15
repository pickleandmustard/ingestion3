
package dpla.ingestion3.harvesters.api

import java.net.URI

import dpla.ingestion3.confs.i3Conf
import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.apache.log4j.Logger
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._

import scala.util.{Failure, Success, Try}

/**
  * Class for harvesting records from the California Digital Library's Solr API
  *
  * Calisphere API documentation
  * https://help.oac.cdlib.org/support/solutions/articles/9000101639-calisphere-api
  *
  */
class CdlHarvester(shortName: String,
                   conf: i3Conf,
                   outputDir: String,
                   harvestLogger: Logger)
  extends ApiHarvester(shortName, conf, outputDir, harvestLogger) {

  override protected val mimeType: String = "application_json"

  override protected val queryParams: Map[String, String] = Map(
    "query" -> conf.harvest.query,
    "rows" -> conf.harvest.rows,
    "api_key" -> conf.harvest.apiKey
  ).collect{ case (key, Some(value)) => key -> value } // remove None values

  override protected def localApiHarvest: Unit = {
    harvestLogger.info(s"Query params: ${queryParams.toString}")

    implicit val formats = DefaultFormats

    // Mutable vars for controlling harvest loop
    var continueHarvest = true
    var cursorMark = "*"

    // Runtime tracking
    val startTime = System.currentTimeMillis()

    while(continueHarvest) getSinglePage(cursorMark) match {
      case error: ApiError with ApiResponse =>
        harvestLogger.error("Error returned by request %s\n%s\n%s".format(
          error.errorSource.url.getOrElse("Undefined url"),
          error.errorSource.queryParams,
          error.message
        ))
        continueHarvest = false
      case src: ApiSource with ApiResponse =>
        src.text match {
          case Some(docs) =>
            val json = parse(docs)
            val cdlRecords = (json \\ "docs").children.map(doc => {
              ApiRecord((doc \\ "identifier").toString, compact(render(doc)))
            })

            // @see ApiHarvester
            saveOut(cdlRecords)

            // Loop control
            cursorMark = (json \\ "cursorMark").extract[String]
            val nextCursorMark = (json \\ "nextCursorMark").extract[String]

            cursorMark.matches(nextCursorMark) match {
              case true => continueHarvest = false
              case false => cursorMark = nextCursorMark
            }
          case None =>
            harvestLogger.error(s"The body of the response is empty. Stopping run.\nCdlSource >> ${src.toString}")
            continueHarvest = false
        }
      case _ =>
        harvestLogger.error("Harvest returned None")
        continueHarvest = false
    }
  }

  /**
    * Get a single-page, un-parsed response from the CDL feed, or an error if
    * one occurs.
    *
    * @param cursorMark Uses cursor and not start/offset to paginate. Used to work around Solr
    *                   deep-paging performance issues.
    * @return ApiSource or ApiError
    */
  private def getSinglePage(cursorMark: String): ApiResponse = {
    getUri(queryParams.updated("cursorMark", cursorMark)) match {
      // Error building URL
      case Failure(e) =>
        val source = ApiSource(queryParams)
        ApiError(e.toString, source)
      case Success(url) => {
        getResponse(url) match {
          case Failure(e) =>
            ApiError(e.toString, ApiSource(queryParams, Some(url.toString)))
          case Success(response) => response.isEmpty match {
            case true => ApiError("Response body is empty", ApiSource(queryParams, Some(url.toString)))
            case false => ApiSource(queryParams, Some(url.toString), Some(response))
          }
        }
      }
    }
  }

  /**
    * Builds query URI from parameters
    *
    * @param queryParams
    * @return URI
    */
  private def getUri(queryParams: Map[String, String]): Try[URI] = Try {
    new URIBuilder()
      .setScheme("https")
      .setHost("solr.calisphere.org")
      .setPath("/solr/query")
      .setParameter("q", queryParams.getOrElse("query", "*:*"))
      .setParameter("cursorMark", queryParams.getOrElse("cursorMark", "*"))
      .setParameter("rows", queryParams.getOrElse("rows", "10"))
      .setParameter("sort", "id desc")
      .build()
    }

  /**
    * Makes request and returns stringified JSON response
    *
    * @param uri
    * @return
    */
  private def getResponse(uri: URI): Try[String] = Try {
    val httpclient = HttpClients.createDefault()
    val get = new HttpGet(uri)
    get.addHeader("X-Authentication-Token", queryParams.getOrElse("api_key", ""))
    var response: CloseableHttpResponse = null
    try {
      response = httpclient.execute(get)
      val entity = response.getEntity
      EntityUtils.toString(entity)
    } finally {
      IOUtils.closeQuietly(response)
    }
  }
}
