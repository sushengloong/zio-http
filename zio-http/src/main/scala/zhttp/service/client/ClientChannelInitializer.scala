package zhttp.service.client

import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.http.websocketx.{
  WebSocketClientHandshakerFactory,
  WebSocketClientProtocolHandler,
  WebSocketVersion,
}
import io.netty.handler.codec.http.{HttpClientCodec, HttpObjectAggregator}
import zhttp.http.Header
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.{CLIENT_SOCKET_HANDLER, HTTP_CLIENT_CODEC, OBJECT_AGGREGATOR, WEB_SOCKET_CLIENT_PROTOCOL_HANDLER}

import java.net.URI

final case class ClientChannelInitializer[R](
  channelHandler: ChannelHandler,
  scheme: String,
  sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
) extends ChannelInitializer[Channel]() {
  override def initChannel(ch: Channel): Unit = {
    val p: ChannelPipeline = ch
      .pipeline()
      .addLast(HTTP_CLIENT_CODEC, new HttpClientCodec)
      .addLast(OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue))
      .addLast(channelHandler)

    if (scheme == "ws") {
      // TODO Move handshaker/config to its own encoding. Check if host, and origin would be set by netty or not.
      p.addAfter(
        OBJECT_AGGREGATOR,
        WEB_SOCKET_CLIENT_PROTOCOL_HANDLER,
        new WebSocketClientProtocolHandler(
          WebSocketClientHandshakerFactory
            .newHandshaker(
              URI.create("/subscriptions"),
              WebSocketVersion.V13,
              null,
              false,
              Header.disassemble(List(Header("HOST", "localhost"), Header("Origin", "localhost"))),
              1280000,
            ),
        ),
      )
      p.addAfter(WEB_SOCKET_CLIENT_PROTOCOL_HANDLER, CLIENT_SOCKET_HANDLER, ClientSocketHandler())
    }
    if (scheme == "https") {
      p.addFirst(ClientSSLHandler.ssl(sslOption).newHandler(ch.alloc))
    }
    ()
  }
}
