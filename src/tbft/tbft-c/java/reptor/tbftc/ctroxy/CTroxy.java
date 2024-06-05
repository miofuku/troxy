package reptor.tbftc.ctroxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosDomain;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.TimeKey;
import reptor.chronos.context.TimerHandler;
import reptor.distrbt.com.VerificationException;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.domains.ChannelHandler;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.ssl.SslConfiguration;
import reptor.replct.connect.Connections;
import reptor.tbft.Troxy;
import reptor.tbft.TroxyClientResults;
import reptor.tbft.TroxyHandshakeResults;
import reptor.tbft.invoke.TransBFTInvocation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public class CTroxy implements Troxy, SelectorDomainContext, ChronosAddress
{
    private static final Logger s_logger = LoggerFactory.getLogger( CTroxy.class );

    private long troxy_instance;
    private byte clioff;
    private TroxyHandshakeResults hresults[];
    private TroxyClientResults cresults[];
    //private ByteBuffer replies[];

    private native long initialize(byte repno, int nhshandls, int nclients, int clinooff, boolean distcontacts, int invwindow, int verifiers, boolean usessl, byte[] cert, byte[] key);
    private native void resetHandshakeNative(long troxy, short hsno, boolean clear, TroxyHandshakeResults thr);
    private native void acceptNative(long troxy, short hsno, TroxyHandshakeResults thr);
    private native int processHandshakeInboundDataNative(long troxy, short hsno, ByteBuffer src, int pos, int rem, TroxyHandshakeResults thr);
    private native int retrieveHandshakeOutboundData(long troxy, short hsno, ByteBuffer dest, int pos, int rem, TroxyHandshakeResults thr);
    private native void saveStateNative(long troxy, short hsno, TroxyClientResults tcr);
    private native void openNative(long troxy, short clino, TroxyClientResults tcr);
    private native void initClientNative(long troxy, short clino);
    private native int processClientInboundDataNative(long troxy, short clino, ByteBuffer src, int pos, int rem, ByteBuffer dest, int destpos, int destrem, TroxyClientResults tcr);
    private native int retrieveClientOutboundDataNative(long troxy, short clino, ByteBuffer dest, int pos, int rem, TroxyClientResults tcr);
    private native int handleForwardedRequestNative(long troxy, short clino, byte[] request, int pos, int rem, ByteBuffer dest, int destpos, int destrem, TroxyClientResults tcr);
    private native int handleRequestExecutedNative(long troxy, short clino, long invno, ByteBuffer src, int srcpos, int srcrem, boolean replyfull, ByteBuffer dest, int destpos, int destrem, TroxyClientResults tcr);
    private native int handleReplyNative(long troxy, short clino, byte[] result, int pos, int rem, TroxyClientResults tcr, int destpos, int destrem);
    private native void handleRepliesNative(long troxy, short clino, ByteBuffer results, int pos, int rem, TroxyClientResults tcr);
    private native void verifyProposalNative(long troxy, int osno, byte[] msg, int offset, int len);
    private native void verifyProposalsNative(long troxy, int osno, byte[] msgs);

    static
    {
        String cur = System.getProperty("java.library.path");
//        cur = cur.concat(":/home/weichbr/repos/tbft/src/tbft/tbft-c/c/jni");
        cur = cur.concat(":/home/bijun/git/reptor/src/tbft/tbft-c/c/jni");
        System.setProperty("java.library.path", cur);
        System.out.println(System.getProperty("java.library.path"));
        //System.loadLibrary("tbftc-native");
    }

    public CTroxy(byte repno, TransBFTInvocation invoke, boolean enclave) throws Exception
    {
        if (enclave)
        {
            s_logger.debug("Starting enclave troxy");
//            System.load("/home/weichbr/repos/tbft/src/tbft/tbft-c/c/jni/libtbftc-enclave.so");
            System.load("/home/bijun/git/reptor/src/tbft/tbft-c/c/jni/libtbftc-enclave.so");
        }
        else
        {
            s_logger.debug("Starting native troxy");
 //           System.load("/home/weichbr/repos/tbft/src/tbft/tbft-c/c/jni/libtbftc-native.so");
            System.load("/home/bijun/git/reptor/src/tbft/tbft-c/c/jni/libtbftc-native.so");
        }
        Connections connect = invoke.getConnections();

        SslConfiguration sslconf = connect.getSslType()!=null && connect.useSslForClientConnections() ? connect.createSslConfiguration( repno ) : null;
        byte cert[] = sslconf != null ? sslconf.getPrivateKeyCertificate().getEncoded() : null;
        byte key[] = sslconf != null ? sslconf.getPrivateKey().getEncoded() : null;
        int nclients = invoke.getClientToWorkerAssignment().getNumberOfClients();

        clioff = invoke.getReplicaGroup().size();
        troxy_instance = initialize(repno, invoke.getNumberOfHandshakeHandlers(), nclients, clioff, invoke.getUseDistributedContacts(), invoke.getInvocationWindowSize(), invoke.getOrdering().getNumberOfWorkers(), sslconf != null, cert, key);
        hresults = new TroxyHandshakeResults[invoke.getNumberOfHandshakeHandlers()];
        cresults = new TroxyClientResults[nclients];
        /*replies = new ByteBuffer[nclients];
        for (int i = 0; i < replies.length; ++i)
        {
            replies[i] = ByteBuffer.allocateDirect(1024 * 16 * 2);
        }
        //*/
    }

    @Override
    public void initHandshake(short hsno, TroxyHandshakeResults results)
    {
        hresults[hsno] = results;
        s_logger.debug("initHandshake called: %d %s\n", hsno, results);
    }

    @Override
    public TroxyHandshakeResults resetHandshake(short hsno, boolean clear) {
        resetHandshakeNative(troxy_instance, hsno, clear, hresults[hsno]);
        s_logger.debug(hresults[hsno].toString());
        return hresults[hsno];
    }

    @Override
    public TroxyHandshakeResults accept(short hsno, InetSocketAddress remaddr) throws IOException {
        acceptNative(troxy_instance, hsno, hresults[hsno]);
        s_logger.debug(hresults[hsno].toString());
        return hresults[hsno];
    }

    @Override
    public int getHandshakeInboundMinimumBufferSize(short hsno) {
        return 1024*16;
    }

    @Override
    public int getHandshakeOutboundMinimumBufferSize(short hsno) {
        return 1024*16;
    }

    @Override
    public TroxyHandshakeResults processHandshakeInboundData(short hsno, ByteBuffer src) throws IOException {
        TroxyHandshakeResults thr = hresults[hsno];
        int read = processHandshakeInboundDataNative(troxy_instance, hsno, src, src.position(), src.remaining(), thr);
        src.position(read);
        s_logger.debug(thr.toString());
        return thr;
    }

    @Override
    public TroxyHandshakeResults retrieveHandshakeOutboundData(short hsno, ByteBuffer dst) throws IOException {
        TroxyHandshakeResults thr = hresults[hsno];
        int written = retrieveHandshakeOutboundData(troxy_instance, hsno, dst, dst.position(), dst.remaining(), thr);
        s_logger.debug("Written "+written+" bytes");
        dst.position(written);
        s_logger.debug(thr.toString());
        return thr;
    }

    @Override
    public void saveState(short hsno) {
        TroxyClientResults tcr = cresults[hresults[hsno].getRemoteEndpoint().getProcessNumber() - clioff];
        saveStateNative(troxy_instance, hsno, tcr);
        s_logger.debug(tcr.toString());
    }

    @Override
    public void initClientHandler(short clino, TroxyClientResults results)
    {
        cresults[clino-clioff] = results;
        initClientNative(troxy_instance, clino);
        s_logger.debug("initClientHandler called: %d %s\n", clino, results);
    }

    @Override
    public TroxyClientResults open(short clino)
    {
        openNative(troxy_instance, clino, cresults[clino - clioff]);
        s_logger.debug(cresults[clino - clioff].toString());
        return cresults[clino - clioff];
    }

    @Override
    public int getClientInboundMinimumBufferSize(short clino) {
        return 1024*16;
    }

    @Override
    public int getClientOutboundMinimumBufferSize(short clino) {
        return 1024*5000;
    }

    @Override
    public TroxyClientResults processClientInboundData(short clino, ByteBuffer src) throws IOException {
        TroxyClientResults tcr = cresults[clino - clioff];
        ByteBuffer dest = tcr.getMessageBuffer();
        int read = processClientInboundDataNative(troxy_instance, clino, src, src.position(), src.remaining(), dest, dest.position(), dest.remaining(), tcr);
        src.position(read);
        s_logger.debug(tcr.toString());
        return tcr;
    }

    @Override
    public TroxyClientResults retrieveClientOutboundData(short clino, ByteBuffer dst) throws IOException {
        TroxyClientResults tcr = cresults[clino - clioff];
        int written = retrieveClientOutboundDataNative(troxy_instance, clino, dst, dst.position(), dst.remaining(), tcr);
        dst.position(written);
        s_logger.debug("Written " + written + " bytes");
        s_logger.debug(tcr.toString());
        return tcr;
    }

    @Override
    public TroxyClientResults retrieveOutboundMessages(short clino)
    {
        TroxyClientResults tcr = cresults[clino - clioff];
        s_logger.debug(tcr.toString());
        return tcr;
    }

    @Override
    public TroxyClientResults handleForwardedRequest(short clino, Data request) throws VerificationException
    {
        TroxyClientResults tcr = cresults[clino - clioff];
        ByteBuffer r = request.byteBuffer();
        ByteBuffer dest = tcr.getMessageBuffer();
        int written = handleForwardedRequestNative(troxy_instance, clino, r.array(), r.position(), r.remaining(), dest, dest.position(), dest.remaining(), tcr);
        dest.position(written);
        s_logger.debug(tcr.toString());
        return tcr;
    }

    @Override
    public TroxyClientResults handleRequestExecuted(short clino, long invno, ImmutableData result, boolean replyfull) throws VerificationException
    {
        TroxyClientResults tcr = cresults[clino - clioff];
        ByteBuffer src = result.byteBuffer();
        ByteBuffer dest = tcr.getMessageBuffer();
        int written = handleRequestExecutedNative(troxy_instance, clino, invno, src, src.position(), src.remaining(), replyfull, dest, dest.position(), dest.remaining(), tcr);
        dest.position(written);
        s_logger.debug(tcr.toString());
        return tcr;
    }

    @Override
    public TroxyClientResults handleReply(short clino, Data reply) throws VerificationException
    {
        TroxyClientResults tcr = cresults[clino - clioff];

        ByteBuffer dest = tcr.getMessageBuffer();
        ByteBuffer r = reply.byteBuffer();
        /*boolean batchComplete = false;
        ByteBuffer repls = replies[clino - clioff];
        // Dirty hack to determine if we have a reply already:
        if (repls.position() > 0)
            batchComplete = true;
        repls.put(r.array(), r.position(), r.remaining());

        if (batchComplete)
        {
            handleRepliesNative(troxy_instance, clino, repls, 0, repls.position(), tcr);
            repls.clear();
            return tcr;
        }
        else
        {
            return tcr;
        }
        //*/
        int written = handleReplyNative(troxy_instance, clino, r.array(), r.position(), r.remaining(), tcr, dest.position(), dest.remaining());
        dest.position(written);
        //s_logger.debug(tcr.toString());
        return tcr;
    }

    @Override
    public void verifyProposal(int osno, Data[] cmdbuf, int ncmds) throws VerificationException
    {
        if (ncmds == 1)
            verifyProposalNative(troxy_instance, osno, cmdbuf[0].array(), cmdbuf[0].arrayOffset(), cmdbuf[0].size());
        else
        {
            // Get size of all
            int size = 0;
            for (int i = 0; i < ncmds; ++i)
            {
                size += cmdbuf[i].size();
            }
            ByteBuffer bb = ByteBuffer.allocate(size);
            for (int i = 0; i < ncmds; ++i)
            {
                Data d = cmdbuf[i];
                bb.put(d.array(), d.arrayOffset(), d.size());
            }
            verifyProposalsNative(troxy_instance, osno, bb.array());
        }
    }

    // Chronos stuff down here

    @Override
    public SelectionKey registerChannel(ChannelHandler handler, SelectableChannel channel, int ops)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void prepareMigrationOfRegisteredChannel(SelectionKey key)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SelectionKey migrateRegisteredChannel(SelectionKey key, ChannelHandler handler)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PushMessageSink<Portal<?>> createChannel(ChronosAddress origin)
    {
        return null;
    }

    @Override
    public ChronosAddress getDomainAddress()
    {
        return this;
    }

    @Override
    public long time()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public TimeKey registerTimer(TimerHandler handler)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean checkDomain()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChronosDomain getCurrentDomain()
    {
        throw new UnsupportedOperationException();
    }
}
