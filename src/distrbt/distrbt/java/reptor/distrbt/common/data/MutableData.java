package reptor.distrbt.common.data;

import java.nio.ByteBuffer;

import reptor.chronos.Orphic;


public interface MutableData extends Data, Orphic
{

    void readFrom(byte[] in);
    void readFrom(byte[] in, int otheroffset, int size, int offset);
    void readFrom(Data in);
    void readFrom(Data in, int otheroffset, int size, int offset);
    void readFrom(ByteBuffer in);
    void readFrom(ByteBuffer in, int size, int offset);

    @Override
    MutableData slice(int offset);
    @Override
    MutableData slice(int offset, int size);

}
