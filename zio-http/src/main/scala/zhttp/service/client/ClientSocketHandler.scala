package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}

case class ClientSocketHandler() extends ChannelInboundHandlerAdapter {
  override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
    super.userEventTriggered(ctx, evt)
  }
}
