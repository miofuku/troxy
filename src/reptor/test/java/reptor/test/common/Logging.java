package reptor.test.common;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;


public class Logging
{

    public static void initRootLogger(Level level)
    {
        LoggerContext logcntxt = (LoggerContext) LoggerFactory.getILoggerFactory();

        PatternLayoutEncoder patenc = new PatternLayoutEncoder();
        patenc.setPattern( "%-5level - %msg%n" );
        patenc.setContext( logcntxt );
        patenc.start();

        ConsoleAppender<ILoggingEvent> conapp = new ConsoleAppender<>();
        conapp.setEncoder( patenc );
        conapp.setContext( logcntxt );
        conapp.start();

        Logger logger = (Logger) LoggerFactory.getLogger( Logger.ROOT_LOGGER_NAME );
        logger.detachAndStopAllAppenders();
        logger.setLevel( level );
        logger.addAppender( conapp );
  }

}
