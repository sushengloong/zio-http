package zhttp.http

import io.netty.handler.codec.http.HttpMethod
import zhttp.http.HttpApp.InvalidMessage
import zhttp.internal.{HttpAppClient, HttpMessageAssertions}
import zhttp.service.EventLoopGroup
import zio.duration._
import zio.stream.ZStream
import zio.test.Assertion.{equalTo, isLeft, isNone}
import zio.test.TestAspect.{nonFlaky, timeout}
import zio.test.{assertM, DefaultRunnableSpec}
import zio.{Chunk, UIO, ZIO}

/**
 * Be prepared for some real nasty runtime tests.
 */
object HttpAppSpec extends DefaultRunnableSpec with HttpMessageAssertions {
  private val env                        = EventLoopGroup.auto(1)
  private val Ok: Response[Any, Nothing] = Response()

  def spec =
    suite("HttpApp")(
      EmptySpec,
      OkSpec,
      FailSpec,
      RequestSpec,
      EchoStreamingResponseSpec,
      IllegalMessageSpec,
      RemoteAddressSpec,
    ).provideCustomLayer(env) @@ timeout(10 seconds)

  /**
   * Spec for asserting Request fields and behavior
   */
  def RequestSpec = {
    suite("succeed(Request)")(
      testM("status is 200") {
        val res = Http.collect[Request] { case _ => Ok }.getResponse
        assertM(res)(isResponse(responseStatus(200)))
      } +
        testM("status is 500") {
          val res = Http.fromEffect(ZIO.fail(new Error("SERVER ERROR"))).getResponse
          assertM(res)(isResponse(responseStatus(500)))
        } +
        testM("status is 404") {
          val res = Http.empty.contramap[Request](i => i).getResponse
          assertM(res)(isResponse(responseStatus(404)))
        } +
        testM("status is 200 in collectM") {
          val res = Http.collectM[Request] { case _ => UIO(Ok) }.getResponse
          assertM(res)(isResponse(responseStatus(200)))
        },
    )
  }

  /**
   * Spec for asserting behavior of an failing endpoint
   */
  def FailSpec = {
    suite("fail(cause)")(
      testM("status is 500") {
        val res = HttpApp.fail(new Error("SERVER_ERROR")).getResponse
        assertM(res)(isResponse(responseStatus(500)))
      } +
        testM("content is SERVER_ERROR") {
          val res = HttpApp.fail(new Error("SERVER_ERROR")).getResponse
          assertM(res)(isResponse(isContent(hasBody("SERVER_ERROR"))))
        } +
        testM("headers are set") {
          val res = HttpApp.fail(new Error("SERVER_ERROR")).getResponse
          assertM(res)(isResponse(responseHeaderName("content-length")))
        },
    )
  }

  /**
   * Spec for an HttpApp that succeeds with a succeeding Http
   */
  def OkSpec = {
    suite("succeed(ok)")(
      testM("status is 200") {
        val res = (Http.succeed(Ok)).getResponse
        assertM(res)(isResponse(responseStatus(200)))
      } +
        suite("POST")(
          testM("status is 200") {
            val content = List("A", "B", "C")
            val res     = (Http.succeed(Ok)).getResponse(method = HttpMethod.POST, content = content)
            assertM(res)(isResponse(responseStatus(200)))
          },
        ),
      testM("headers are empty") {
        val res = (Http.succeed(Ok)).getResponse
        assertM(res)(isResponse(hasHeader("Content-Length", "0")))
      } +
        testM("headers are set") {
          val res = (Http.succeed(Response(headers = List(Header.custom("key", "value"))))).getResponse
          assertM(res)(isResponse(hasHeader("key", "value")))
        } +
        testM("version is 1.1") {
          val res = (Http.succeed(Ok)).getResponse
          assertM(res)(isResponse(version("HTTP/1.1")))
        } +
        testM("version is 1.1") {
          val res = (Http.succeed(Ok)).getResponse
          assertM(res)(isResponse(version("HTTP/1.1")))
        },
    )
  }

  /**
   * Spec for an HttpApp that is empty
   */
  def EmptySpec = {
    suite("empty") {
      suite("GET") {
        testM("status is 404") {
          val res = HttpApp.empty.getResponse
          assertM(res)(isResponse(responseStatus(404)))
        } +
          testM("headers are empty") {
            val res = HttpApp.empty.getResponse
            assertM(res)(isResponse(hasHeader("Content-Length", "0")))
          } +
          testM("version is 1.1") {
            val res = HttpApp.empty.getResponse
            assertM(res)(isResponse(version("HTTP/1.1")))
          } +
          testM("version is 1.1") {
            val res = HttpApp.empty.getResponse
            assertM(res)(isResponse(version("HTTP/1.1")))
          }
      } +
        suite("POST") {
          testM("status is 404") {
            val res = HttpApp.empty.getResponse(method = HttpMethod.POST, content = List("A", "B", "C"))
            assertM(res)(isResponse(responseStatus(404)))
          }
        } +
        suite("Response Count") {
          testM("pure") {
            val count = HttpApp.response(Response.ok).getResponseCount()
            assertM(count)(equalTo(1))
          } +
            testM("effect") {
              val count = HttpApp.fromEffectFunction(_ => UIO(Response.ok)).getResponseCount()
              assertM(count)(equalTo(1))
            } +
            testM("effect option") {
              val count = HttpApp.fromOptionFunction(_ => ZIO.fail(None)).getResponseCount()
              assertM(count)(equalTo(1))
            }
        }
    }
  }

  def EchoStreamingResponseSpec = {
    val streamingResponse = Response(data =
      HttpData.fromStream(
        ZStream
          .fromIterable(List("A", "B", "C", "D"))
          .map(text => Chunk.fromArray(text.getBytes))
          .flattenChunks,
      ),
    )

    suite("StreamingResponse") {
      testM("status is 200") {
        val res = Http.collect[Request] { case _ => streamingResponse }.getResponse
        assertM(res)(isResponse(responseStatus(200)))
      } +
        testM("content is 'ABCD'") {
          val content = Http.collect[Request] { case _ => streamingResponse }.getContent
          assertM(content)(equalTo("ABCD"))
        } @@ nonFlaky
    }
  }

  /**
   * Captures scenarios when an invalid message is sent to the HttpApp.
   */
  def IllegalMessageSpec = suite("IllegalMessage")(
    testM("throws exception") {
      val program = HttpAppClient.deploy(HttpApp.empty).flatMap(_.write("ILLEGAL_MESSAGE").either)
      assertM(program)(isLeft(equalTo(InvalidMessage("ILLEGAL_MESSAGE"))))
    },
  )

  def RemoteAddressSpec = suite("RemoteAddressSpec") {
    testM("remoteAddress") {
      val addr = (Http.succeed(Ok)).getRequest().map(_.remoteAddress)
      assertM(addr)(isNone)
    }
  }
}
