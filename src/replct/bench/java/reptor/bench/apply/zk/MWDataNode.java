package reptor.bench.apply.zk;

import java.io.Serializable;


public class MWDataNode implements Serializable
{

    private static final long serialVersionUID = 569250082077027234L;

    private static int        indexCounter     = 0;

    private final int         index;
    private final MWStat      stat;
    private byte[]            data;


    public MWDataNode(MWStat stat)
    {
        this.index = indexCounter++;
        this.stat = stat;
    }


    public int getIndex()
    {
        return index;
    }


    public MWStat getStat()
    {
        return stat;
    }


    public byte[] getData()
    {
        return data;
    }


    public void setData(byte[] data)
    {
        this.data = data;
    }

}
