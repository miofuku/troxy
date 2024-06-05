package reptor.bench.apply.zero;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

/**
 * Created by bli on 16.08.17.
 */
public class GenerateCommand
{
    private int eventCounter;
    private long eventValueAggregation;

    private int overallCounter;
    private long overallValue;

    private long startrun, endrun;

    private SSLSocketFactory factory;

    public GenerateCommand() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, UnrecoverableKeyException {
        reset();
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(null,null,null);
        factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    private void reset()
    {
        this.eventCounter = 0;
        this.eventValueAggregation = 0;

        this.overallCounter = 0;
        this.overallValue = 0;

        this.startrun = this.endrun = 0;
    }

    private void end()
    {
        overallCounter = eventCounter;
        overallValue = eventValueAggregation;
    }

    private void result()
    {
        System.out.println("Throughput: " + ((long) overallCounter/((endrun - startrun)/1000)) + " r/s, Average latency: " + overallValue/overallCounter + " us");
    }

    private Thread startBenchmarking(long durtot, long start, int reqsize)
    {
        Thread worker = new Thread() {
            public void run()
            {
                SSLSocket sock = null;
                int interval = 10;
                while (System.nanoTime()-start<=durtot)
                {
                    if (sock==null||sock.isClosed())
                    {
                        try {
                            sock = (SSLSocket) factory.createSocket("127.0.0.1", 5000);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    OutputStream outputStream = null;
                    byte[] cmd = new byte[reqsize];
                    try {
                        sock.setEnabledCipherSuites(sock.getSupportedCipherSuites());
                        outputStream = sock.getOutputStream();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    long startTime = System.nanoTime();
                    try {
                        outputStream.write(cmd);
                        outputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    InputStream inputStream = null;
                    byte[] reply = new byte[32*1024];
                    try {
                        inputStream = sock.getInputStream();
                        inputStream.read(reply);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    long endTime = System.nanoTime();

                    statistic((long) ((endTime - startTime)/1000));

                    if ((System.nanoTime()-start)/1000000000==interval) {
                        try {
                            sock.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        interval+=10;
                    }
                }
            }
        };
        return new Thread(worker);
    }

    private void statistic(long value)
    {
        eventCounter++;
        eventValueAggregation += value;
//        System.out.println("counter: "+eventCounter+" value: "+eventValueAggregation);
    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, KeyManagementException, CertificateException, KeyStoreException, UnrecoverableKeyException {
        long durtot   = TimeUnit.SECONDS.toNanos( Integer.parseInt( args[ 0 ] ) );
        int clino     = Integer.parseInt( args[1] );
        int reqsize   = Integer.parseInt( args[2] );
        GenerateCommand gc = new GenerateCommand();

        Thread[] workers = new Thread[clino];
        for (int i=0;i<workers.length;i++)
        {
            long currentTime = System.nanoTime();
            workers[i] = gc.startBenchmarking(durtot, currentTime, reqsize);
            workers[i].start();
        }

        Thread stat = new Thread() {
            public void run() {
                try {
                    System.out.println("Start benchmarking...");
                    Thread.sleep( durtot*6/10/1000000 );
                    gc.reset();
                    gc.startrun = System.nanoTime()/1000000;
                    System.out.println( "Start logging..." );
                    Thread.sleep( durtot*3/10/1000000 );
                    gc.end();
                    gc.endrun = System.nanoTime()/1000000;
                    System.out.println( "Stopped logging." );
                    Thread.sleep( durtot*1/10/1000000 );
                    gc.result();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        stat.start();

        return;
    }
}
