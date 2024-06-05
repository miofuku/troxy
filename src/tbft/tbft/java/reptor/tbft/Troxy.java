package reptor.tbft;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import reptor.distrbt.com.VerificationException;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;


public interface Troxy
{
    // Methods that may change the results objects, return always the result object that was passed by the respective
    // initialization method.
    void                  initHandshake(short hsno, TroxyHandshakeResults results);
    TroxyHandshakeResults resetHandshake(short hsno, boolean clear);
    TroxyHandshakeResults accept(short hsno, InetSocketAddress remaddr) throws IOException;
    int                   getHandshakeInboundMinimumBufferSize(short hsno);
    int                   getHandshakeOutboundMinimumBufferSize(short hsno);
    TroxyHandshakeResults processHandshakeInboundData(short hsno, ByteBuffer src) throws IOException;
    TroxyHandshakeResults retrieveHandshakeOutboundData(short hsno, ByteBuffer dst) throws IOException;
    void                  saveState(short hsno);

    void                  initClientHandler(short clino, TroxyClientResults results);
    TroxyClientResults    open(short clino);
    int                   getClientInboundMinimumBufferSize(short clino);
    int                   getClientOutboundMinimumBufferSize(short clino);
    TroxyClientResults    processClientInboundData(short clino, ByteBuffer src) throws IOException;
    TroxyClientResults    retrieveClientOutboundData(short clino, ByteBuffer dst) throws IOException;

    TroxyClientResults    retrieveOutboundMessages(short clino);

    TroxyClientResults    handleForwardedRequest(short clino, Data request) throws VerificationException;
    // TODO: Combine handleRequestExecuted and handleReply to allow buffering of f+1 of these messages.
    TroxyClientResults    handleRequestExecuted(short clino, long invno, ImmutableData result, boolean replyfull)
                                throws VerificationException;
    TroxyClientResults    handleReply(short clino, Data reply) throws VerificationException;

    void                  verifyProposal(int osno, Data[] cmdbuf, int ncmds) throws VerificationException;
}
