package dpla.ingestion3.harvesters.resourceSync

import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

import scala.collection.mutable.Stack

/**
  *
  */
class ResourceSyncIterator() extends Iterator[String] {

  private[this] val buffer = new Stack[(String)]
  private[this] val httpClient = HttpClients.createDefault

  /**
    *
    * @return
    */
  override def hasNext(): Boolean = {
    buffer.nonEmpty
  }

  /**
    *
    * @return
    */
  override def next(): String = {
    val url = buffer.pop()
    val httpGet = new HttpGet(url)
    httpGet.addHeader("Accept", "text/turtle")  // Explicitly limited to text/turtle for hybox testing
    val rsp = httpClient.execute(httpGet)

    try {
      val entity = rsp.getEntity
      EntityUtils.toString(entity)
    } finally {
      rsp.close()
    }
  }

  /**
    * Fills the buffter of items to fetch
    *
    * @param itemUrls
    */
  def fillBuffer(itemUrls: Seq[String]): Unit = {
    buffer.pushAll(itemUrls)
  }
}
