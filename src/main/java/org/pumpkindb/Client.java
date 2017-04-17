/**
 * Copyright (c) 2017, All Contributors (see CONTRIBUTORS file)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.pumpkindb;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class Client {

    private final String host;
    private final int port;
    private final MessageHandler messageHandler;

    private Channel channel;
    private EventLoopGroup workerGroup;

    public Client(String host, int port, MessageHandler messageHandler) {
        this.host = host;
        this.port = port;
        this.messageHandler = messageHandler;
    }

    public Client(String host, MessageHandler messageHandler) {
        this(host, 9981, messageHandler);
    }

    public void connect() {
        workerGroup = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);

        b.handler(new ChannelInitializer<SocketChannel>() {

            @Override protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addFirst(new LoggingHandler(LogLevel.DEBUG));

                ch.pipeline().addLast(new LengthFieldPrepender(4));
                ch.pipeline().addLast(new FrameEncoder());

                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));

                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        ByteBuf buf = (ByteBuf) msg;
                        messageHandler.accept(buf);
                    }
                });
            }

        });


        ChannelFuture channelFuture = b.connect(host, port).syncUninterruptibly();

        channel = channelFuture.channel();

    }

    public void send(Encodable encodable) {
        channel.writeAndFlush(encodable);
    }

    public void shutdown() {
        channel.disconnect().syncUninterruptibly();
        channel.close().syncUninterruptibly();
        workerGroup.shutdownGracefully();
    }
    private static class FrameEncoder extends MessageToByteEncoder<Encodable> {

        @Override protected void encode(ChannelHandlerContext ctx, Encodable msg, ByteBuf out) throws Exception {
            msg.encode(out);
        }
    }
}
