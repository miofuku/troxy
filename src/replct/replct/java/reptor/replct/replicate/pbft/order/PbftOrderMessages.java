package reptor.replct.replicate.pbft.order;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;
import reptor.replct.agree.order.AbstractOrderHolderMessage;
import reptor.replct.agree.order.OrderMessages.CommandContainer;


public class PbftOrderMessages
{

    private static final int PBFTX_ORDER_BASE    = ProtocolID.PBFT | MessageCategoryID.ORDER;
    public  static final int PBFT_PREPREPARE_ID = PBFTX_ORDER_BASE + 1;
    public  static final int PBFT_PREPARE_ID    = PBFTX_ORDER_BASE + 2;
    public  static final int PBFT_COMMIT_ID     = PBFTX_ORDER_BASE + 3;


    public static class PbftPrePrepare extends AbstractOrderHolderMessage
    {

        public PbftPrePrepare(short sender, long orderno, int viewno, CommandContainer cmd)
        {
            super( sender, orderno, viewno, cmd );
        }


        public PbftPrePrepare(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( OrderMessageVariant.COMMAND, in, mapper );
        }


        @Override
        public int getTypeID()
        {
            return PBFT_PREPREPARE_ID;
        }

    }


    public static class PbftPrepare extends AbstractOrderHolderMessage
    {

        public PbftPrepare(short sender, long orderno, int viewno, CommandContainer command)
        {
            super( sender, orderno, viewno, command );
        }


        public PbftPrepare(short sender, long orderno, int viewno, ImmutableData data)
        {
            super( sender, orderno, viewno, data );
        }


        public PbftPrepare(OrderMessageVariant variant, ByteBuffer in, MessageMapper mapper) throws IOException
        {
            super( variant, in, mapper );
        }


        public static PbftPrepare readDataVersionFrom(ByteBuffer in, MessageMapper mapper, Object extcntxt)
                throws IOException
        {
            return new PbftPrepare( OrderMessageVariant.DATA, in, mapper );
        }


        public static PbftPrepare readCommandVersionFrom(ByteBuffer in, MessageMapper mapper, Object extcntxt)
                throws IOException
        {
            return new PbftPrepare( OrderMessageVariant.COMMAND, in, mapper );
        }


        @Override
        public int getTypeID()
        {
            return PBFT_PREPARE_ID;
        }

    }


    public static class PbftCommit extends AbstractOrderHolderMessage
    {

        public PbftCommit(short sender, long orderno, int viewno, CommandContainer command)
        {
            super( sender, orderno, viewno, command );
        }


        public PbftCommit(short sender, long orderno, int viewno, ImmutableData data)
        {
            super( sender, orderno, viewno, data );
        }


        public PbftCommit(OrderMessageVariant variant, ByteBuffer in, MessageMapper mapper) throws IOException
        {
            super( variant, in, mapper );
        }


        public static PbftCommit readDataVersionFrom(ByteBuffer in, MessageMapper mapper, Object extcntxt)
                throws IOException
        {
            return new PbftCommit( OrderMessageVariant.DATA, in, mapper );
        }


        public static PbftCommit readCommandVersionFrom(ByteBuffer in, MessageMapper mapper, Object extcntxt)
                throws IOException
        {
            return new PbftCommit( OrderMessageVariant.COMMAND, in, mapper );
        }


        @Override
        public int getTypeID()
        {
            return PBFT_COMMIT_ID;
        }

    }

}
