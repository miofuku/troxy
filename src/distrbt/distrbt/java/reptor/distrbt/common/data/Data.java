package reptor.distrbt.common.data;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.Signature;

import javax.crypto.Mac;

import com.google.common.hash.PrimitiveSink;


public interface Data
{

    void writeTo(byte[] out);
    void writeTo(byte[] out, int otheroffset, int size, int offset);
    void writeTo(MutableData out);
    void writeTo(MutableData out, int otheroffset, int size, int offset);
    void writeTo(ByteBuffer out);
    void writeTo(ByteBuffer out, int offset, int size);

    void writeTo(PrimitiveSink out);
    void writeTo(PrimitiveSink out, int offset, int size);
    void writeTo(MessageDigest out);
    void writeTo(MessageDigest out, int offset, int size);
    void writeTo(Mac out);
    void writeTo(Mac out, int offset, int size);
    void writeTo(Signature out);
    void writeTo(Signature out, int offset, int size);

    ByteBuffer    byteBuffer();
    ByteBuffer    byteBuffer(int offset);
    ByteBuffer    byteBuffer(int offset, int size);

    Data slice(int offset);
    Data slice(int offset, int size);

    MutableData   mutable();
    MutableData   copy();
    ImmutableData immutableCopy();

    byte[]  array();
    int     arrayOffset();
    int     size();

    void    adaptSlice(int delta);
    void    adaptSlice(ByteBuffer buffer);
    void    adaptSlice(int offset, int size);
    void    resetSlice();

    boolean matches(byte[] other, int otheroffset, int size, int offset);
    boolean matches(Data other, int otheroffset, int size, int offset);

    boolean equals(byte[] other);
    boolean equals(Data other);

    @Override
    boolean equals(Object obj);

    @Override
    public int hashCode();

}
