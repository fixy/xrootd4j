/**
 * Copyright (C) 2011-2013 dCache.org <support@dcache.org>
 *
 * This file is part of xrootd4j.
 *
 * xrootd4j is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * xrootd4j is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with xrootd4j.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.dcache.xrootd.core;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;

import org.dcache.xrootd.plugins.AuthorizationFactory;
import org.dcache.xrootd.plugins.AuthorizationHandler;
import org.dcache.xrootd.protocol.XrootdProtocol.FilePerm;
import org.dcache.xrootd.protocol.messages.AbstractResponseMessage;
import org.dcache.xrootd.protocol.messages.CloseRequest;
import org.dcache.xrootd.protocol.messages.DirListRequest;
import org.dcache.xrootd.protocol.messages.MkDirRequest;
import org.dcache.xrootd.protocol.messages.MvRequest;
import org.dcache.xrootd.protocol.messages.OpenRequest;
import org.dcache.xrootd.protocol.messages.PathRequest;
import org.dcache.xrootd.protocol.messages.PrepareRequest;
import org.dcache.xrootd.protocol.messages.ProtocolRequest;
import org.dcache.xrootd.protocol.messages.ReadRequest;
import org.dcache.xrootd.protocol.messages.ReadVRequest;
import org.dcache.xrootd.protocol.messages.RmDirRequest;
import org.dcache.xrootd.protocol.messages.RmRequest;
import org.dcache.xrootd.protocol.messages.StatRequest;
import org.dcache.xrootd.protocol.messages.StatxRequest;
import org.dcache.xrootd.protocol.messages.SyncRequest;
import org.dcache.xrootd.protocol.messages.WriteRequest;
import org.dcache.xrootd.protocol.messages.XrootdRequest;
import org.dcache.xrootd.util.OpaqueStringParser;
import org.dcache.xrootd.util.ParseException;

import static io.netty.channel.ChannelHandler.Sharable;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_ArgMissing;
import static org.dcache.xrootd.protocol.XrootdProtocol.kXR_NotAuthorized;

@Sharable
public class XrootdAuthorizationHandler extends XrootdRequestHandler
{
    private static final Logger _log =
        LoggerFactory.getLogger(XrootdAuthorizationHandler.class);

    private final AuthorizationFactory _authorizationFactory;

    public XrootdAuthorizationHandler(AuthorizationFactory authorizationFactory)
    {
        _authorizationFactory = authorizationFactory;
    }

    @Override
    protected AbstractResponseMessage doOnStat(ChannelHandlerContext ctx,
                                               StatRequest req)
        throws XrootdException
    {
        authorize(ctx, req, FilePerm.READ);
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnStatx(ChannelHandlerContext ctx,
                                                StatxRequest req)
        throws XrootdException
    {
        if (req.getPaths().length == 0) {
            throw new XrootdException(kXR_ArgMissing, "no paths specified");
        }

        String[] paths = req.getPaths();
        String[] opaques = req.getOpaques();
        for (int i = 0; i < paths.length; i++) {
            paths[i] = authorize(ctx,
                                 req,
                                 FilePerm.READ,
                                 paths[i],
                                 opaques[i]);
        }
        req.setPaths(paths);

        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnRm(ChannelHandlerContext ctx,
                                             RmRequest req)
        throws XrootdException
    {
        if (req.getPath().isEmpty()) {
            throw new XrootdException(kXR_ArgMissing, "no path specified");
        }
        authorize(ctx, req, FilePerm.DELETE);
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnRmDir(ChannelHandlerContext ctx,
                                                RmDirRequest req)
        throws XrootdException
    {
        if (req.getPath().isEmpty()) {
            throw new XrootdException(kXR_ArgMissing, "no path specified");
        }

        authorize(ctx, req, FilePerm.DELETE);
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnMkDir(ChannelHandlerContext ctx,
                                                MkDirRequest req)
        throws XrootdException
    {
        if (req.getPath().isEmpty()) {
            throw new XrootdException(kXR_ArgMissing, "no path specified");
        }

        authorize(ctx, req, FilePerm.WRITE);
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnMv(ChannelHandlerContext ctx,
                                             MvRequest req)
        throws XrootdException
    {
        String sourcePath = req.getSourcePath();
        if (sourcePath.isEmpty()) {
            throw new XrootdException(kXR_ArgMissing, "No source path specified");
        }

        String targetPath = req.getTargetPath();
        if (targetPath.isEmpty()) {
            throw new XrootdException(kXR_ArgMissing, "No target path specified");
        }

        req.setSourcePath(authorize(ctx,
                                    req,
                                    FilePerm.DELETE,
                                    req.getSourcePath(),
                                    req.getOpaque()));
        req.setTargetPath(authorize(ctx,
                                    req,
                                    FilePerm.WRITE,
                                    req.getTargetPath(),
                                    req.getOpaque()));
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnDirList(ChannelHandlerContext ctx,
                                                  DirListRequest req)
        throws XrootdException
    {
        String path = req.getPath();
        if (path.isEmpty()) {
            throw new XrootdException(kXR_ArgMissing, "no source path specified");
        }

        authorize(ctx, req, FilePerm.READ);
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnPrepare(ChannelHandlerContext ctx,
                                                  PrepareRequest req)
    {
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnOpen(ChannelHandlerContext ctx,
                                               OpenRequest req)
        throws XrootdException
    {
        FilePerm neededPerm;
        if (req.isNew() || req.isReadWrite()) {
            neededPerm = FilePerm.WRITE;
        } else {
            neededPerm = FilePerm.READ;
        }
        authorize(ctx, req, neededPerm);
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnRead(ChannelHandlerContext ctx,
                                               ReadRequest req)
        throws XrootdException
    {
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnReadV(ChannelHandlerContext ctx,
                                                ReadVRequest req)
        throws XrootdException
    {
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnWrite(ChannelHandlerContext ctx,
                                                WriteRequest req)
        throws XrootdException
    {
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnSync(ChannelHandlerContext ctx,
                                               SyncRequest req)
        throws XrootdException
    {
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnClose(ChannelHandlerContext ctx,
                                                CloseRequest req)
        throws XrootdException
    {
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    @Override
    protected AbstractResponseMessage doOnProtocolRequest(ChannelHandlerContext ctx,
                                                          ProtocolRequest req)
        throws XrootdException
    {
        ctx.nextInboundMessageBuffer().add(req);
        return null;
    }

    private void authorize(ChannelHandlerContext ctx,
                           PathRequest request,
                           FilePerm neededPerm)
        throws XrootdException
    {
        request.setPath(authorize(ctx,
                                  request,
                                  neededPerm,
                                  request.getPath(),
                                  request.getOpaque()));
    }

    /**
     * Performs authorization check and path mapping.
     *
     * @param ctx The ChannelHandlerContext for this handler
     * @param request The xrootd message
     * @param neededPerm The permission level that is required for the operation
     * @param path The path to which access is requested
     * @param opaque Opaque data sent with the request
     * @return The path to which access is granted.
     * @throws XrootdException if the request is denied
     */
    private String authorize(ChannelHandlerContext ctx,
                             XrootdRequest request,
                             FilePerm neededPerm,
                             String path,
                             String opaque)
        throws XrootdException
    {
        try {
            Channel channel = ctx.channel();
            InetSocketAddress localAddress =
                (InetSocketAddress) channel.localAddress();
            InetSocketAddress remoteAddress =
                (InetSocketAddress) channel.remoteAddress();

            AuthorizationHandler handler =
                _authorizationFactory.createHandler();
            return handler.authorize(request.getSubject(),
                                     localAddress,
                                     remoteAddress,
                                     path,
                                     OpaqueStringParser.getOpaqueMap(opaque),
                                     request.getRequestId(),
                                     neededPerm);
        } catch (GeneralSecurityException e) {
            throw new XrootdException(kXR_NotAuthorized,
                                      "Authorization check failed: " +
                                      e.getMessage());
        } catch (SecurityException e) {
            throw new XrootdException(kXR_NotAuthorized,
                                      "Permission denied: " + e.getMessage());
        } catch (ParseException e) {
            throw new XrootdException(kXR_NotAuthorized,
                                      "Invalid opaque data: " + e.getMessage() +
                                      " (opaque=" + opaque + ")");
        }
    }
}
