package com.github.dexluthor.server.exceptions;

import java.io.File;

public class DirectoryForbiddenException extends RuntimeException {
    private final File forbiddenDir;

    public DirectoryForbiddenException(final File forbiddenDir) {
        this.forbiddenDir = forbiddenDir;
    }

    public DirectoryForbiddenException(final String message, final File forbiddenDir) {
        super(message);
        this.forbiddenDir = forbiddenDir;
    }

    public DirectoryForbiddenException(final String message, final Throwable cause, final File forbiddenDir) {
        super(message, cause);
        this.forbiddenDir = forbiddenDir;
    }

    public DirectoryForbiddenException(final Throwable cause, final File forbiddenDir) {
        super(cause);
        this.forbiddenDir = forbiddenDir;
    }

    public DirectoryForbiddenException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace, final File forbiddenDir) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.forbiddenDir = forbiddenDir;
    }

    public File getForbiddenDir() {
        return forbiddenDir;
    }
}
