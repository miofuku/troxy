package reptor.replct;

import com.google.common.io.Files;
import reptor.distrbt.certify.mac.Authenticating;
import reptor.distrbt.certify.mac.MacCertifier;
import reptor.distrbt.certify.mac.PlainSingleMacFormat;
import reptor.distrbt.certify.mac.SingleMacFormat;
import reptor.distrbt.certify.trusted.JniTrinxImplementation;
import reptor.distrbt.certify.trusted.JniTrinxImplementation.*;
import reptor.distrbt.certify.trusted.TrinxCommands;
import reptor.distrbt.certify.trusted.TrinxImplementation;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.DataBuffer;
import reptor.jlib.hash.Hashing;

import javax.crypto.Mac;

import java.io.IOException;
import java.security.Key;

import static reptor.distrbt.certify.mac.Authenticating.hmacAlgorithm;

/**
 * Created by bijun on 18.11.17.
 */

public class MACTest {

    private static long elaps = 0;
    private static int loop, size = 0;
    private static String method;

    public static void main(String[] args) throws IOException, InterruptedException {

        String m_tsslib, m_tssenc, libname = "";
        TrinxImplementation tm = null;
        JniTrinx trinx = null;

        long starttime, endtime = 0;

        loop = Integer.parseInt(args[0]);
        size = Integer.parseInt(args[1]);
        method = String.valueOf(args[2]);
        Key key = Authenticating.createKey( "SECRET" );

        SingleMacFormat m_certformat = new PlainSingleMacFormat( hmacAlgorithm( Hashing.SHA256, "HmacSha256" ), null );
        Mac mac = m_certformat.getProofAlgorithm().macCreator( key );
        MacCertifier certifier = new MacCertifier(mac, m_certformat);

        Data msgdata = null;
        DataBuffer msgbuffer = null;

        switch (method)
        {
            case "mac-cert":
                for (int i=loop;i>0;i--)
                {
                    msgdata = new DataBuffer(size);
                    msgbuffer = new DataBuffer(certifier.getCertificateSize());

                    starttime = System.nanoTime();
                    certifier.createCertificate(msgdata, msgbuffer);
                    endtime = System.nanoTime();

                    storeResult(endtime-starttime);
                    Thread.sleep(2000);
                }

                showResult();
                break;

            case "mac-veri":
                for (int i=loop;i>0;i--)
                {
                    msgdata = new DataBuffer(size);
                    msgbuffer = new DataBuffer(certifier.getCertificateSize());

                    starttime = System.nanoTime();
                    certifier.verifyCertificate(msgdata, msgbuffer);
                    endtime = System.nanoTime();

                    storeResult(endtime-starttime);
                    Thread.sleep(2000);
                }

                showResult();
                break;

            case "trinx-cert":
                for (int i=loop;i>0;i--)
                {
                    m_tsslib = "/misc/testbed/usr/bli/reptor/build/trinx/lib/libtrinx-jni-sgx-s.so";
                    m_tssenc = "/misc/testbed/usr/bli/reptor/build/trinx/lib/libtrinx-opt-b.signed.so";

                    libname = Files.getNameWithoutExtension( m_tsslib ).substring( 3 );
                    tm = new JniTrinxImplementation( libname, m_tssenc, "SECRET" );
                    trinx = (JniTrinx) tm.createTrinx((short)1,1);

                    msgdata = new DataBuffer(size);
                    msgbuffer = new DataBuffer(certifier.getCertificateSize());

                    starttime = System.nanoTime();
                    trinx.executeCommand(new TrinxCommands.UnserializedTrinxCommand().createTrustedMac().tss(trinx.getID()).message(msgdata, msgbuffer));
                    endtime = System.nanoTime();

                    storeResult(endtime-starttime);
                    Thread.sleep(2000);
                }

                showResult();
                break;

            case "trinx-veri":
                for (int i=loop;i>0;i--)
                {
                    m_tsslib = "/misc/testbed/usr/bli/reptor/build/trinx/lib/libtrinx-jni-sgx-s.so";
                    m_tssenc = "/misc/testbed/usr/bli/reptor/build/trinx/lib/libtrinx-opt-b.signed.so";

                    libname = Files.getNameWithoutExtension(m_tsslib).substring(3);
                    tm = new JniTrinxImplementation(libname, m_tssenc, "SECRET");
                    trinx = (JniTrinx) tm.createTrinx((short) 1, 1);

                    msgdata = new DataBuffer(size);
                    msgbuffer = new DataBuffer(certifier.getCertificateSize());

                    starttime = System.nanoTime();
                    trinx.executeCommand(new TrinxCommands.UnserializedTrinxCommand().verifyTrustedMac().tss(trinx.getID()).message(msgdata, msgbuffer));
                    endtime = System.nanoTime();

                    storeResult(endtime - starttime);
                    Thread.sleep(2000);
                }

                showResult();
                break;

            case "open-cert":
                for (int i=loop;i>0;i--)
                {
                    m_tsslib = "/misc/testbed/usr/bli/reptor/build/trinx/lib/libtrinx-jni-ossl.so";
                    m_tssenc = "/misc/testbed/usr/bli/reptor/build/trinx/lib/libtrinx-opt-b.signed.so";

                    libname = Files.getNameWithoutExtension( m_tsslib ).substring( 3 );
                    tm = new JniTrinxImplementation( libname, m_tssenc, "SECRET" );
                    trinx = (JniTrinx) tm.createTrinx((short)1,1);

                    msgdata = new DataBuffer(size);
                    msgbuffer = new DataBuffer(certifier.getCertificateSize());

                    starttime = System.nanoTime();
                    trinx.executeCommand(new TrinxCommands.UnserializedTrinxCommand().createTrustedMac().tss(trinx.getID()).message(msgdata, msgbuffer));
                    endtime = System.nanoTime();

                    storeResult(endtime - starttime);
                    Thread.sleep(2000);
                }

                showResult();
                break;

            case "open-veri":
                for (int i=loop;i>0;i--)
                {
                    m_tsslib = "/misc/testbed/usr/bli/reptor/build/trinx/lib/libtrinx-jni-ossl.so";
                    m_tssenc = "/misc/testbed/usr/bli/reptor/build/trinx/lib/libtrinx-opt-b.signed.so";

                    libname = Files.getNameWithoutExtension( m_tsslib ).substring( 3 );
                    tm = new JniTrinxImplementation( libname, m_tssenc, "SECRET" );
                    trinx = (JniTrinx) tm.createTrinx((short)1,1);

                    msgdata = new DataBuffer(size);
                    msgbuffer = new DataBuffer(certifier.getCertificateSize());

                    starttime = System.nanoTime();
                    trinx.executeCommand(new TrinxCommands.UnserializedTrinxCommand().verifyTrustedMac().tss(trinx.getID()).message(msgdata, msgbuffer));
                    endtime = System.nanoTime();

                    storeResult(endtime - starttime);
                    Thread.sleep(2000);
                }

                showResult();
                break;
        }


    }

    private static void storeResult(long duration)
    {
        elaps += duration;
    }

    private static void showResult()
    {
        System.out.println("Method: "+method+" for size "+size+" takes "+elaps/loop+" ns.");
    }
}
