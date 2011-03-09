package cc.spray

import http._
import scala.collection.JavaConversions._
import org.apache.commons.io.IOUtils
import java.net.{UnknownHostException, InetAddress}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import java.io.ByteArrayInputStream

trait ServletConverter {
  
  protected[spray] def toSprayRequest(request: HttpServletRequest): HttpRequest = {
    val headers = buildHeaders(request)
    HttpRequest(
      HttpMethods.get(request.getMethod).get,
      request.getRequestURI,
      headers,
      buildParameters(request.getParameterMap.asInstanceOf[java.util.Map[String, Array[String]]]),
      readContent(request, headers),
      getRemoteHost(request),
      HttpVersions.get(request.getProtocol)
    )
  }

  protected def buildHeaders(request: HttpServletRequest): List[HttpHeader] = {
    for (
      name <- request.getHeaderNames.asInstanceOf[java.util.Enumeration[String]].toList;
      value <- request.getHeaders(name).asInstanceOf[java.util.Enumeration[String]].toList
    ) yield {
      HttpHeader(name, value)
    }
  }

  protected def buildParameters(parameterMap: java.util.Map[String, Array[String]]) = {
    (Map.empty[Symbol, String] /: parameterMap) {
      (map, entry) => {
        map.updated(Symbol(entry._1), if (entry._2.isEmpty) "" else entry._2(0))
      }
    }
  }
  
  protected def readContent(request: HttpServletRequest, headers: List[HttpHeader]): HttpContent = {
    HttpContent(IOUtils.toByteArray(request.getInputStream))
  }
  
  protected def getRemoteHost(request: HttpServletRequest) = {
    try {
      Some(HttpIp(InetAddress.getByName(request.getRemoteAddr)))
    } catch {
      case _: UnknownHostException => None 
    }
  }
  
  protected[spray] def fromSprayResponse(response: HttpResponse): HttpServletResponse => Unit = {
    hsr => {
      hsr.setStatus(response.status.code.value)
      for (HttpHeader(name, value) <- response.headers) {
        hsr.setHeader(name, value)
      }
      response.content match {
        case buffer: ContentBuffer => {
          IOUtils.copy(buffer.inputStream, hsr.getOutputStream)
          hsr.setContentLength(buffer.length)
        }
        case NoContent => if (!response.status.code.isInstanceOf[HttpSuccess]) {
          hsr.setContentType("text/plain")
          hsr.getWriter.write(response.status.reason)
          hsr.getWriter.close
        }
      }
      hsr.flushBuffer
    }
  }
  
}