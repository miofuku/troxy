#!/bin/bash

PATH_BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

: ${MAIN_DIR:="$PATH_BASE/.."}
: ${BLDBIN_DIR:="$MAIN_DIR/build/trinx/bin"}
: ${BLDLIB_DIR:="$MAIN_DIR/build/trinx/lib"}
: ${BLDCLS_DIR:="$MAIN_DIR/build/classes"}
: ${WRK_DIR:="$MAIN_DIR/wrkdir"}
: ${RES_DIR:="$WRK_DIR/results"}

: ${RESPREF:=`hostname`}
: ${RESSUM_FILE:="$RES_DIR/$RESPREF-summary.csv"}

: ${JAVA:=java}
: ${JAVA_FLAGS:=-Xmx8g -Xms8g -Xmn6g -server}

if [ -z "$JAVA_CLSPAT" ]; then
    JAVA_CLSPAT=${MAIN_DIR}/build/deps/*
    for proj in "distrbt/distrbt" "base/jlib" "exprmt/measr" "reptor/replct" "base/chronos" "reptor/test"; do
        JAVA_CLSPAT+=":${BLDCLS_DIR}/$proj"
    done
fi

: ${PRINTCMDS:=0}
: ${DRYRUN:=0}
: ${EXTRA:=}

if [ "$DEBUG" = "1" ]; then
    : ${PRINTINTS:=1}
    : ${NINTS:=3}
    : ${INTLEN:=1000000}
    : ${WITHSGX:=0}
    : ${WITHJAVA:=0}
    : ${WITHCASH:=0}
    : ${SAVERES:=0}
    : ${TOUCH_ONLY:=0}
else
    : ${PRINTINTS:=0}
    : ${NINTS:=10}
    : ${INTLEN:=1000000}
    : ${WITHSGX:=1}
    : ${WITHJAVA:=1}
    : ${WITHCASH:=0}
    if [ $DRYRUN -eq 1 ]; then
        SAVERES=0
    else
        : ${SAVERES:=1}
    fi
fi

if [ "$BENCH" = "javacert" ]; then
    : ${MSGSIZE:="0 16 32 52 64 116 128 180 192 244 256 512 1024 2048 4096"}
    : ${CERTMODE:="certify verify"}
    : ${TOUCH_ONLY:=0}
    : ${THREADS:=1}

    if [ -z "$MODE" ]; then
        MODE="javacert"

        if [ $WITHSGX -eq 1 ]; then
            MODE+=" jnisgxopt_sb"
            ALGO_jnisgxopt_sb="HMAC_SHA256"
        fi
    fi

    if [ -z "$ALGO" ]; then
        ALGO="MD5 SHA1 SHA256"
        ALGO+=" DMAC_MD5 DMAC_SHA1 DMAC_SHA256"
        ALGO+=" HMAC_MD5 HMAC_SHA1 HMAC_SHA256"
        ALGO+=" AUTH_3_HMAC_SHA256 AUTH_4_HMAC_SHA256 AUTH_3_HMAC_SHA256pSHA256 AUTH_4_HMAC_SHA256pSHA256"
        ALGO+=" DSA_1024_SHA256 DSA_2048_SHA256 RSA_1024_SHA256 RSA_2048_SHA256 ECDSA_192_SHA256 ECDSA_256_SHA256"
    fi
elif [ -z "$MODE" ]; then
#    MODE="dummy ossl sgxlibstd sgxlibopt"
    MODE="dummy ossl sgxlibopt"

    if [ $WITHJAVA -eq 1 ]; then
        MODE+=" jnidummy jniossl jnisgxlibopt javadummy javajtm javacert"

        ALGO_javacert="RSA_1024_SHA256"

        if [ $WITHCASH -eq 1 ]; then
            MODE+=" cash jnicash javacash"
        fi
    fi

    if [ $WITHSGX -eq 1 ]; then
#        MODE+=" sgxdummy sgxstd sgxopt"
        for m in s m; do
            for b in b u; do
                MODE+=" sgxdummy_$m$b sgxopt_$m$b"

                if [ $WITHJAVA -eq 1 ]; then
                    MODE+=" jnisgxdummy_$m$b jnisgxopt_$m$b"
                fi
            done
        done
    fi
fi

#: ${SINGLE_CALLS:=0 1}
: ${SINGLE_CALLS:=0}
: ${TOUCH_ONLY:=0 1}
: ${CPUTOP:=3} # CPU numbering scheme 1: progressive - (0 1) (2 3) (4 5) (6 7); 3: interlaced ncores - (0 4) (1 5) (2 6) (3 7)
: ${WITHNUMA:=0}
: ${THREADINIT:=1}
: ${MSGSIZE:=32}
: ${ALGO:="HMAC_SHA256"}
: ${CERTMODE:="certify"} # certify, verify, verifydummy
: ${CHECK:=1}

# THREADS = nthreads[/affpat[+affoff]]
if [ -z "$THREADS" ]; then
    declare ncpus=`getconf _NPROCESSORS_ONLN`
    declare ncores=$(( $ncpus / 2 ))

    # Let the operating system schedule the threads
    for nthreads in 1 `seq 2 2 $ncpus`; do
        THREADS+=" $nthreads"
    done

    # Place single threads on an increasing number of cores
    if [ $CPUTOP -eq 1 ]; then
        declare threadaffpat=2
    else
        declare threadaffpat=1
    fi

    for nthreads in `seq 1 $ncores`; do
        THREADS+=" $nthreads/$threadaffpat"

        if [ "$WITHNUMA" -eq 1 ]; then
            THREADS+=" $nthreads/4"
        fi
    done

    # Use all threads of an increasing number of cores
    if [ $CPUTOP -eq 1 ]; then
        declare coreaffpat=1
    else
        declare coreaffpat=3
    fi

    for nthreads in `seq 2 2 $ncpus`; do
        THREADS+=" $nthreads/$coreaffpat"

        if [ "$WITHNUMA" -eq 1 ]; then
            THREADS+=" $nthreads/5"
        fi
    done

    # The first cores might be a special case, thus we also test the last one
    THREADS+=" 2/$coreaffpat+$(( $ncpus - 2 ))"

    echo "Thread configs: $THREADS"
fi


run_test()
{
    declare mode=$1
    declare nthreads=$2
    declare affpat=$3
    declare affoff=$4
    declare thread_init=$5
    declare touch_only=$6
    declare single_calls=$7
    declare outfile="$8"
    declare msgsize=$9
    declare algo=${10}
    declare certmode=${11}

    declare params

    # jnidummy jniossl jnisgxlibopt jnisgxdummy_{s|m}{b|u} jnisgxopt_{s|m}{b|u} javadummy javajtm cash jnicash javacash
    if [[ $mode == jni* ]]; then
        declare isjava=1

        if [[ $mode == *sgx* && $mode != *sgxlib* ]]; then
            libadd=sgx-${mode: -2:-1} # s | m
        else
            libadd="${mode:3} \"\""
        fi

        params+=" trinx trinx-jni-$libadd"
        jflagsadd+=" -Djava.library.path=${BLDLIB_DIR}"
    elif [[ $mode == java* ]]; then
        declare isjava=1
        params+=" ${mode:4}"

        if [[ $mode == javajtm || $mode == javacert ]]; then
            params+=" $algo"
        fi
    else
        declare isjava=0
    fi


    if [[ $mode == *sgx* && $mode != *sgxlib* ]]; then
        sgxmode=${mode#*sgx}
        if [[ ${sgxmode: -2:-1} = m ]]; then
            sgxmodesuf=-2
        else
            sgxmodesuf=-1
        fi
        sgxmode=${sgxmode%_*}-${sgxmode: $sgxmodesuf}
        params+=" ${BLDLIB_DIR}/libtrinx-$sgxmode.signed.so"
        binadd=sgx-${mode: -2:-1}
    else
        binadd=$mode
    fi

    if [[ $CHECK -ne 1 || $mode == *dummy* ]]; then
        params+=" -n"
    fi

    params+=" --nints $NINTS"
    params+=" --msgsize $msgsize"
    params+=" --certmode $certmode"

    if [[ $mode == *cash ]]; then
        params+=" --intlen $(( $INTLEN / 10 ))"
    else
        params+=" --intlen $INTLEN"
    fi

    params+=" --nthreads $nthreads"
    params+=" --affpat $affpat"
    params+=" --affoff $affoff"

    if [ $thread_init -ne 1 ]; then
        params+=" -L"
    fi

    if [ $PRINTINTS -eq 1 ]; then
        params+=" -p"
    fi

    if [ $touch_only -eq 1 ]; then
        params+=" -t"
    fi

    if [ $single_calls -eq 1 ]; then
        params+=" -s"
    fi

    if [ -n "$outfile" ]; then
        params+=" --outfile $outfile"
    fi

	params+=" $EXTRA"

    if [ "$isjava" -eq 1 ]; then
        cmd="$JAVA $JAVA_FLAGS $jflagsadd -cp $JAVA_CLSPAT reptor.test.distrbt.trusted.TrinxTest $params"
    else
        cmd="${BLDBIN_DIR}/trinx-test-$binadd $params"
    fi

    if [ "$PRINTCMDS" -eq 1 ]; then
        echo $cmd
    fi

    if [ "$DRYRUN" -eq 0 ]; then
        eval $cmd
    fi
}


mkdir -p "${RES_DIR}"

if [[ "$SAVERES" -eq 1 && ! -e "$RESSUM_FILE" ]]; then
    echo "time;platform;mode;msgsize;certmode;single_calls;touch_only;thread_init;affpat;affoff;nthreads;affinities;thread;intno;cnt;sum;min;max;avg;ops_per_sec" >> "$RESSUM_FILE"
fi

declare nruns=0

for mode in $MODE ; do
    for msgsize in $MSGSIZE ; do
    for certmode in $CERTMODE ; do
    algoname="ALGO_$mode"
    if [ -n "${!algoname}" ]; then
        algolist=${!algoname}
    else
        algolist=$ALGO
    fi
    for algo in $algolist ; do
    for single_calls in $SINGLE_CALLS ; do
        if [ $mode = javacert ]; then
            tolist="0"
        else
            tolist=$TOUCH_ONLY
        fi
        for touch_only in $tolist ; do
            for thread_init in $THREADINIT ; do
                for tc in $THREADS ; do
                    declare nthreads=${tc%/*}

                    if [[ $nthreads -gt 1 && $mode == *cash* ]]; then
                        continue
                    fi

                    declare affpat=0
                    declare affoff=0

                    if [[ $tc == */* ]]; then
                        affpat=${tc#*/}
                        affpat=${affpat%+*}
                    fi

                    if [[ $tc == *+* ]]; then
                        affoff=${tc#*+}
                    fi

                    if [ $mode = javajtm ]; then
                        modename="javatm-$algo"
                    elif [ $mode = javacert ]; then
                        modename="javacert-$algo"
                    else
                        modename=$mode
                    fi

                    nruns=$(( $nruns + 1 ))
                    echo
                    echo "--> $nruns. m $modename ms $msgsize cm $certmode sc $single_calls to $touch_only ap $affpat nt $nthreads"

                    if [ "$SAVERES" -eq 1 ]; then
                        declare ts=`date +%Y_%m_%d-%H_%M_%S`
                        declare of="${RES_DIR}/$RESPREF-$ts-$modename-$msgsize-$certmode-${single_calls}${touch_only}-${affpat}_${affoff}_${thread_init}-$nthreads.csv"
                    fi

                    run_test $mode $nthreads $affpat $affoff $thread_init $touch_only $single_calls "$of" $msgsize $algo $certmode

                    if [ "$SAVERES" -eq 1 ]; then
                        echo "$ts;$RESPREF;$modename;$msgsize;$certmode;$single_calls;$touch_only;$thread_init;$affpat;$affoff;$nthreads;`tail -1 $of`" >> "$RESSUM_FILE"
                    fi
                done
            done
        done
    done
    done
    done
    done
done
