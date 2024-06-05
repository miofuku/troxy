package reptor.test.distrbt.trusted;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import javax.crypto.Mac;

import reptor.distrbt.certify.CertificationMethod;
import reptor.distrbt.certify.debug.DebugCertifying;
import reptor.distrbt.certify.debug.DigestMacAlgorithm;
import reptor.distrbt.certify.debug.DigestToMac;
import reptor.distrbt.certify.debug.PlainSingleDigestMacFormat;
import reptor.distrbt.certify.mac.Authenticating;
import reptor.distrbt.certify.mac.AuthenticatorCertification;
import reptor.distrbt.certify.mac.MacAlgorithm;
import reptor.distrbt.certify.mac.PlainSingleMacFormat;
import reptor.distrbt.certify.signature.PlainSingleSignatureFormat;
import reptor.distrbt.certify.signature.SignatureAlgorithm;
import reptor.distrbt.certify.signature.Signing;
import reptor.distrbt.certify.suites.AuthorityInstanceStore;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeyStore;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.certify.suites.JavaAuthority;
import reptor.distrbt.certify.trusted.CASHImplementation;
import reptor.distrbt.certify.trusted.DummyTrinxImplementation;
import reptor.distrbt.certify.trusted.JavaTrinxImplementation;
import reptor.distrbt.certify.trusted.JniTrinxImplementation;
import reptor.distrbt.certify.trusted.TrinxImplementation;
import reptor.distrbt.certify.trusted.TrinxCommands.SerializedTrinxCommand;
import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCommandBuffer;
import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCommandType;
import reptor.distrbt.certify.trusted.TrinxCommands.UnserializedTrinxCommand;
import reptor.jlib.hash.HashAlgorithm;
import reptor.jlib.hash.Hashing;
import reptor.measr.sink.LongStatsSink;
import reptor.test.bench.MultiCoreBenchmarker;
import reptor.test.distrbt.trusted.MessageBenchmark.CertificationMode;


public class TrinxTest
{

    static final int WARMUP_RATIO   = 3;
    static final int COOLDOWN_RATIO = 3;


    enum CpuAffinityPattern
    {
        None,
        Progressive,
        Interlaced2,
        InterlacedC,
        ProgressiveN2,
        InterlacedCN2
    };


    static void writeSummary(PrintStream out, String aff, int tmid, int intno, LongStatsSink sum, double opsfac)
    {
        out.print( aff );
        out.print( ";" );

        if( tmid>=0 )
            out.print( tmid );
        out.print( ";" );

        if( intno>=0 )
            out.print( intno );
        out.print( ";" );

        double avg = sum.getSum() / (double) sum.getCount();

        out.print( sum.getCount() );
        out.print( ";" );
        out.print( sum.getSum() );
        out.print( ";" );
        out.print( sum.getMin() );
        out.print( ";" );
        out.print( sum.getMax() );
        out.print( ";" );
        out.print( (long) avg );
        out.print( ";" );
        out.println( (long) ( opsfac/avg ) );
    }


    static void writeFileAndCOut(PrintStream fout, String aff, int tmid, int intno, LongStatsSink sum, double opsfac)
    {
        if( fout!=null )
            writeSummary( fout, aff, tmid, intno, sum, opsfac );

        writeSummary( System.out, aff, tmid, intno, sum, opsfac );
    }


    static void writeResults(MultiCoreBenchmarker bench, String outfile) throws IOException
    {
        PrintStream fout = null;

        if( outfile!=null )
        {
            fout = new PrintStream( new FileOutputStream( outfile ) );
            fout.println( "aff;thread;intno;cnt;sum;min;max;avg;ops_per_sec" );
        }

        LongStatsSink   total    = new LongStatsSink();
        LongStatsSink[] tmtotals = new LongStatsSink[ bench.getNumberOfObjects() ];
        Arrays.setAll( tmtotals, x -> new LongStatsSink() );

        int warmup   = bench.getNumberOfIntervals() * WARMUP_RATIO / 10;
        int cooldown = bench.getNumberOfIntervals() -
                          bench.getNumberOfIntervals() * COOLDOWN_RATIO / 10;

        final double timefac = 1000000000.0;

        short tmid = 0;
        for( LongStatsSink[] ints : bench.getIntervals() )
        {
            String aff = bench.getCpuAffinity( tmid )>=0 ?
                            Integer.toString( bench.getCpuAffinity( tmid ) ) : "";

            LongStatsSink tmtotal = tmtotals[ tmid ];

            int intno = 0;
            for( LongStatsSink tmint : ints )
            {
                writeFileAndCOut( fout, aff, tmid, intno, tmint, timefac );

                if( intno>=warmup && intno<cooldown )
                    tmtotal.add( tmint );
                intno++;
            }

            total.add( tmtotal );
            tmid++;
        }

        StringBuilder afflist = new StringBuilder();
        tmid = 0;
        for( LongStatsSink tmtotal : tmtotals )
        {
            if( tmid>0 )
                afflist.append( " " );

            String aff;

            if( bench.getCpuAffinity( tmid )>=0 )
            {
                afflist.append( bench.getCpuAffinity( tmid ) );
                aff = Integer.toString( bench.getCpuAffinity( tmid ) );
            }
            else
            {
                afflist.append( "-" );
                aff = "";
            }

            writeFileAndCOut( fout, aff, tmid, -1, tmtotal, timefac );
            tmid++;
        }

        writeFileAndCOut( fout, afflist.toString(), -1, -1, total, timefac*bench.getNumberOfObjects() );

        if( fout!=null )
            fout.close();
    }



    public static void main(String[] args) throws Exception
    {
//        args = new String[] {
//                              "trinx", "trinx-jni-ossl", "",
////                              "jtm", "HmacSha256",
////                              "-n",
////                              "--serialcmd",
//                              "--nints", "30", "--intlen", "1000000", "-p",
////                              "--nints", "30", "--intlen", "1", "-p",
//                              "--nthreads", "1", "--affpat", "0", "--affoff", "0",
//                              "--certmode", "batcert", "--batchsize", "10",
//                              "--msgsize", "32" };

        int              curargidx = 0;
        boolean          check     = true;
        TrinxImplementation    tm        = null;
        MessageBenchmark bench     = null;
        float            intlenfac = 1.0f;

        switch( args[ curargidx++ ] )
        {
        case "dummy":
            tm = new DummyTrinxImplementation( CounterCheck.COUNTER_CERTIFICATE_SIZE, true );
            break;
        case "jtm":
        {
            Mac macproto;

            MacAlgorithm macalgo = Authenticating.tryParseMacAlgorithm( args[ curargidx ] );

            if( macalgo!=null )
                macproto = macalgo.macCreator( "secret" );
            else
                macproto = new DigestToMac( Hashing.tryParseHashAlgorithm( args[ curargidx ] ).digester() );

            tm = new JavaTrinxImplementation( macproto );

            curargidx++;

            break;
        }
        case "cert":
        {
            check = false;

            String algo = args[ curargidx++ ];

            HashAlgorithm hashalgo = Hashing.tryParseHashAlgorithm( algo );
            HashAlgorithm prehash  = null;

            if( hashalgo!=null )
                bench = new HashBenchmark( hashalgo );
            else
            {
                CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> certmethod;
                List<ConnectionKeys> keys   = new ArrayList<>();
                PrivateKey           prikey = null;
                DigestMacAlgorithm   dmacalgo;
                MacAlgorithm         macalgo;
                SignatureAlgorithm   sigalgo;

                if( algo.startsWith( "AUTH_" ) )
                {
                    certmethod = new AuthenticatorCertification<>( new PlainSingleMacFormat( Authenticating.HMAC_SHA256, null ) );

                    for( int i=1; i<=Integer.parseInt( algo.substring( 5, 6 ) ); i++ )
                        keys.add( new ConnectionKeyStore( i, Authenticating.createKey( "secret" ), (PublicKey) null, (short) -1 ) );

                    if( algo.endsWith( "pSHA256" ) )
                        prehash = Hashing.SHA256;
                }
                else if( ( dmacalgo = DebugCertifying.tryParseDigestMacAlgorithm( algo ) )!=null )
                {
                    certmethod = new PlainSingleDigestMacFormat( dmacalgo, null );
                    keys.add( new ConnectionKeyStore( 1, null, (PublicKey) null, (short) -1 ) );
                }
                else if( ( macalgo = Authenticating.tryParseMacAlgorithm( algo ) )!=null )
                {
                    certmethod = new PlainSingleMacFormat( macalgo, null );
                    keys.add( new ConnectionKeyStore( 1, Authenticating.createKey( "secret" ), (PublicKey) null, (short) -1 ) );
                }
                else if( ( sigalgo = Signing.tryParseSignatureAlgorithm( algo ) )!=null )
                {
                    KeyPair keypair = sigalgo.getKeyType().keyGenerator().generateKeyPair();

                    certmethod = new PlainSingleSignatureFormat( sigalgo, null );
                    keys.add( new ConnectionKeyStore( 1, null, keypair.getPublic(), (short) -1 ) );
                    prikey = keypair.getPrivate();

                    intlenfac = 0.005f;
                }
                else
                {
                    throw new IllegalArgumentException( algo );
                }

                JavaAuthority ja = new JavaAuthority( 0, prikey );
                AuthorityInstanceStore auths = new AuthorityInstanceStore( ja );

                bench = new CertificationBenchmark( auths, keys, certmethod );
                bench.setPreHashing( prehash );
            }

            break;
        }
        case "cash":
            tm = new CASHImplementation();
            break;
        case "trinx":
            tm = new JniTrinxImplementation( args[ curargidx++ ], args[ curargidx++ ], "secret" );
            break;
        default:
            throw new IllegalArgumentException( args[ curargidx-1 ] );
        }


        String             outfile   = null;
        CpuAffinityPattern affpat    = CpuAffinityPattern.None;
        int                affoff    = 4;
        short              ntms      = 1;
        boolean            serialcmd = false;

        CounterBenchmark ctrbench;

        if( tm!=null )
            bench = ctrbench = new CounterBenchmark( tm );
        else
            ctrbench = null;

        MultiCoreBenchmarker benchmarker = new MultiCoreBenchmarker( bench );

        while( curargidx<args.length )
        {
            String curarg = args[ curargidx++ ];

            if( "-s".equals( curarg ) )
                benchmarker.setMeasureSingleCalls( true );
            else if(  "-p".equals( curarg ) )
                benchmarker.setPrintIntervals( true );
            else if(  "-t".equals( curarg ) )
                ctrbench.setTouchOnly( true );
            else if(  "--serialcmd".equals( curarg ) )
                serialcmd = true;
            else if(  "--batchsize".equals( curarg ) )
            {
                int batchsize = Integer.parseInt( args[ curargidx++ ] );
                ctrbench.setBatchSize( batchsize );
                benchmarker.setIntervalMultiplier( batchsize );
            }
            else if(  "-n".equals( curarg ) )
                check = false;
            else if(  "-L".equals( curarg ) )
                benchmarker.setThreadLocalInit( false );
            else if( curargidx==args.length )
                throw new IllegalStateException( "Value expected!" );
            else if(  "--certmode".equals( curarg ) )
            {
                String cm = args[ curargidx++ ];

                switch( cm )
                {
                case "certify":
                case "cert":
                    bench.setCertificationMode( CertificationMode.CERTIFY );
                    break;
                case "verify":
                case "verf":
                    bench.setCertificationMode( CertificationMode.VERIFY );
                    break;
                case "verifydummy":
                    bench.setCertificationMode( CertificationMode.VERIFY_DUMMY );
                    break;
                case "batcert":
                    bench.setCertificationMode( CertificationMode.BATCH_CERTIFY );
                    break;
                default:
                    throw new IllegalArgumentException( cm );
                }
            }
            else if(  "--nthreads".equals( curarg ) )
                ntms = Short.parseShort( args[ curargidx++ ] );
            else if(  "--msgsize".equals( curarg ) )
                bench.setMessageSize( Integer.parseInt( args[ curargidx++ ] ) );
            else if(  "--nints".equals( curarg ) )
                benchmarker.initIntervals( Integer.parseInt( args[ curargidx++ ] ) );
            else if(  "--intlen".equals( curarg ) )
                benchmarker.setIntervalLength( Integer.parseInt( args[ curargidx++ ] ) );
            else if(  "--affpat".equals( curarg ) )
                affpat = CpuAffinityPattern.values()[ Integer.parseInt( args[ curargidx++ ] ) ];
            else if(  "--affoff".equals( curarg ) )
                affoff = Integer.parseInt( args[ curargidx++ ] );
            else if(  "--outfile".equals( curarg ) )
                outfile = args[ curargidx++ ];
            else
                throw new IllegalStateException( "Unexpected argument! " );
        }

        Function<TrinxCommandType, TrinxCommandBuffer> cmdfac = serialcmd ?
                SerializedTrinxCommand::new : t -> new UnserializedTrinxCommand().type( t );

        if( ctrbench!=null )
        {
            ctrbench.setCommandFactory( cmdfac );
        }

        if( check )
        {
            System.out.println( "Check implementation:" );

            CounterCheck.checkCounterImplementation( tm, cmdfac );

            System.out.println();
        }

        if( bench.getMessageSize()>0 )
            intlenfac = 1 / ( 1/intlenfac + bench.getMessageSize()/40.0f );

        benchmarker.setIntervalLength( Math.round( benchmarker.getIntervalLength()*intlenfac ) );
        benchmarker.initObjects( ntms );

        if( affpat!=CpuAffinityPattern.None )
        {
            int ncpus  = Runtime.getRuntime().availableProcessors();
            int ncores = ncpus/2;

            for( short tmid=0; tmid<benchmarker.getNumberOfObjects(); tmid++ )
            {
                int cpuid;
                int x = tmid+affoff;

                if( affpat==CpuAffinityPattern.Progressive )
                    cpuid = x % ncpus; // ( tmid % ncpus )*1 + ( tmid/ncpus ) % 1
                else if( affpat==CpuAffinityPattern.Interlaced2 )
                    cpuid = ( x % ncores )*2 + ( x/ncores ) % 2;
                else if( affpat==CpuAffinityPattern.InterlacedC )
                    cpuid = ( x % 2 )*ncores + ( x/2 ) % ncores;
                else if( affpat==CpuAffinityPattern.ProgressiveN2 )
                    cpuid = ( x % (ncores/2) )*2 + ( x/(ncores/2) ) % 2 + ( ( x/ncores ) % 2 )*ncores;
                else
                    cpuid = ( x % 2 )*ncores + ( ( x/2 )*2 ) % ncores + ( x/ncores ) % 2;


                benchmarker.setCpuAffinity( tmid, cpuid );
            }
        }

        System.out.println( "Benchmark:" );

        benchmarker.Run();

        System.out.println();
        System.out.println( "Results:" );

        writeResults( benchmarker, outfile );
    }

}
