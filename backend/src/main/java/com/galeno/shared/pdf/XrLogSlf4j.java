package com.galeno.shared.pdf;

import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openhtmltopdf.util.Diagnostic;
import com.openhtmltopdf.util.XRLogger;

/**
 * Los mensajes de openhtmltopdf por SLF4J, como el resto de la API (en producción, una línea JSON por evento). Por
 * defecto la librería escribe en la consola con su propio formato. Sus INFO ("TIME: parse stylesheets…", "Prepare to
 * fly") van a DEBUG: son detalle de cada PDF, no eventos.
 */
final class XrLogSlf4j implements XRLogger {

    @Override
    public void log(String where, Level level, String msg) {
        log(where, level, msg, null);
    }

    @Override
    public void log(String where, Level level, String msg, Throwable th) {
        Logger log = LoggerFactory.getLogger("com.openhtmltopdf." + where);
        int nivel = level.intValue();
        if (nivel >= Level.SEVERE.intValue()) {
            log.error(msg, th);
        } else if (nivel >= Level.WARNING.intValue()) {
            log.warn(msg, th);
        } else if (nivel >= Level.INFO.intValue()) {
            log.debug(msg, th);
        } else {
            log.trace(msg, th);
        }
    }

    @Override
    public void setLevel(String logger, Level level) {
        // El nivel lo decide la configuración de logging de Spring
    }

    @Override
    public boolean isLogLevelEnabled(Diagnostic diagnostic) {
        Logger log = LoggerFactory.getLogger("com.openhtmltopdf." + diagnostic.getLogMessageId().getWhere());
        int nivel = diagnostic.getLevel().intValue();
        return nivel >= Level.SEVERE.intValue() ? log.isErrorEnabled()
                : nivel >= Level.WARNING.intValue() ? log.isWarnEnabled()
                : nivel >= Level.INFO.intValue() ? log.isDebugEnabled()
                : log.isTraceEnabled();
    }
}
