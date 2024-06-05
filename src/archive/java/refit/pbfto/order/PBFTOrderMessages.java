package refit.pbfto.order;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;
import reptor.replct.agree.common.order.AbstractOrderHolderMessage;
import reptor.replct.agree.common.order.OrderMessages;


public class PBFTOrderMessages
{

    private static final int PBFT_ORDER_BASE     = ProtocolID.PBFT | MessageCategoryID.ORDER;
    public  static final int PBFT_PRE_PREPARE_ID = PBFT_ORDER_BASE + 1;
    public  static final int PBFT_PREPARE_ID     = PBFT_ORDER_BASE + 2;
    public  static final int PBFT_COMMIT_ID      = PBFT_ORDER_BASE + 3;


    public static class PBFTPrePrepare extends AbstractOrderHolderMessage
    {

        public PBFTPrePrepare(short sender, long orderno, int viewno, OrderMessages.CommandContainer message)
        {
            super( sender, orderno, viewno, message );
        }


        public PBFTPrePrepare(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( OrderMessageVariant.COMMAND, in, mapper );
       }


        @Override
        public int getTypeID()
        {
            return PBFT_PRE_PREPARE_ID;
        }

    }


    public static class PBFTPrepare extends AbstractOrderHolderMessage
    {

        public PBFTPrepare(short sender, long orderno, int viewno, OrderMessages.CommandContainer message)
        {
            super( sender, orderno, viewno, message );
        }


        public PBFTPrepare(short sender, long orderno, int viewno, ImmutableData data)
        {
            super( sender, orderno, viewno, data );
        }


        public PBFTPrepare(OrderMessageVariant variant, ByteBuffer in, MessageMapper mapper) throws IOException
        {
            super( variant, in, mapper );
        }


        public static PBFTPrepare readDataVersionFrom(ByteBuffer in, MessageMapper mapper, Object extcntxt)
                throws IOException
        {
            return new PBFTPrepare( OrderMessageVariant.DATA, in, mapper );
        }


        public static PBFTPrepare readCommandVersionFrom(ByteBuffer in, MessageMapper mapper, Object extcntxt)
                throws IOException
        {
            return new PBFTPrepare( OrderMessageVariant.COMMAND, in, mapper );
        }


        @Override
        public int getTypeID()
        {
            return PBFT_PREPARE_ID;
        }

    }


    public static class PBFTCommit extends AbstractOrderHolderMessage
    {

        public PBFTCommit(short sender, long orderno, int viewno, OrderMessages.CommandContainer message)
        {
            super( sender, orderno, viewno, message );
        }


        public PBFTCommit(short sender, long orderno, int viewno, ImmutableData data)
        {
            super( sender, orderno, viewno, data );
        }


        public PBFTCommit(OrderMessageVariant variant, ByteBuffer in, MessageMapper mapper) throws IOException
        {
            super( variant, in, mapper );
        }


        public static PBFTCommit readDataVersionFrom(ByteBuffer in, MessageMapper mapper, Object extcntxt)
                throws IOException
        {
            return new PBFTCommit( OrderMessageVariant.DATA, in, mapper );
        }


        public static PBFTCommit readCommandVersionFrom(ByteBuffer in, MessageMapper mapper, Object extcntxt)
                throws IOException
        {
            return new PBFTCommit( OrderMessageVariant.COMMAND, in, mapper );
        }


        @Override
        public int getTypeID()
        {
            return PBFT_COMMIT_ID;
        }

    }

}
