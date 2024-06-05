package refit.hybstero.order;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;
import reptor.replct.agree.common.order.AbstractOrderHolderMessage;
import reptor.replct.agree.common.order.OrderMessages;


public class HybsterOrderMessages
{

    private static final int HYBSTER_ORDER_BASE = ProtocolID.HYBSTER | MessageCategoryID.ORDER;
    public  static final int HYBSTER_PREPARE_ID = HYBSTER_ORDER_BASE + 1;
    public  static final int HYBSTER_COMMIT_ID  = HYBSTER_ORDER_BASE + 2;


    public static class HybsterPrepare extends AbstractOrderHolderMessage
    {

        public HybsterPrepare(short sender, long orderno, int viewno, OrderMessages.CommandContainer cmd)
        {
            super( sender, orderno, viewno, cmd );
        }


        public HybsterPrepare(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( OrderMessageVariant.COMMAND, in, mapper );
        }


        @Override
        public int getTypeID()
        {
            return HYBSTER_PREPARE_ID;
        }

    }


    public static class HybsterCommit extends AbstractOrderHolderMessage
    {

        public HybsterCommit(short sender, long orderno, int viewno, OrderMessages.CommandContainer command)
        {
            super( sender, orderno, viewno, command );
        }


        public HybsterCommit(short sender, long orderno, int viewno, ImmutableData data)
        {
            super( sender, orderno, viewno, data );
        }


        public HybsterCommit(OrderMessageVariant variant, ByteBuffer in, MessageMapper mapper) throws IOException
        {
            super( variant, in, mapper );
        }


        public static HybsterCommit readDataVersionFrom(ByteBuffer in, MessageMapper mapper, Object extcntxt)
                throws IOException
        {
            return new HybsterCommit( OrderMessageVariant.DATA, in, mapper );
        }


        public static HybsterCommit readCommandVersionFrom(ByteBuffer in, MessageMapper mapper, Object extcntxt)
                throws IOException
        {
            return new HybsterCommit( OrderMessageVariant.COMMAND, in, mapper );
        }


        @Override
        public int getTypeID()
        {
            return HYBSTER_COMMIT_ID;
        }

    }

}
