package com.googlecode.greysanatomy.exception;

/**
 * �������
 * Created by vlinux on 15/5/2.
 */
public class CommandNotFoundException extends CommandException {

    public CommandNotFoundException(String command) {
        super(command);
    }

}
