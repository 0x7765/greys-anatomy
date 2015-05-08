package com.googlecode.greysanatomy.command;

import com.googlecode.greysanatomy.command.annotation.Cmd;
import com.googlecode.greysanatomy.command.annotation.NamedArg;
import com.googlecode.greysanatomy.command.view.KeyValueView;
import com.googlecode.greysanatomy.server.GaSession;
import com.googlecode.greysanatomy.util.GaStringUtils;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import static java.lang.String.format;

/**
 * �鿴�Ự״̬����
 * Created by vlinux on 15/5/3.
 */
@Cmd(named = "session", sort = 8, desc = "Show the session state.",
        eg = {
                "session",
                "session -c GBK",
                "session -c UTF-8"
        })
public class SessionCommand extends Command {

    @NamedArg(named = "c", hasValue = true, description = "change the charset of session")
    private String charsetString;

    @Override
    public Action getAction() {
        return new Action() {

            @Override
            public void action(final GaSession gaSession, final Info info, final Sender sender) throws Throwable {

                // ����������ַ���
                if (GaStringUtils.isNotBlank(charsetString)) {

                    try {
                        final Charset newCharset = Charset.forName(charsetString);
                        final Charset beforeCharset = gaSession.getCharset();
                        gaSession.setCharset(newCharset);

                        sender.send(true, format("change charset before[%s] -> new[%s]",
                                beforeCharset,
                                newCharset));

                    } catch (UnsupportedCharsetException e) {
                        sender.send(true, format("unsupported charset : \"%s\"", charsetString));
                    }


                }

                // չʾ�Ự״̬
                else {
                    sender.send(true, sessionToString(gaSession));
                }

            }

        };
    }

    /*
     * �Ự����
     */
    private String sessionToString(GaSession gaSession) {

        return new KeyValueView()
                .add("javaPid", gaSession.getJavaPid())
                .add("sessionId", gaSession.getSessionId())
                .add("duration", gaSession.getSessionDuration())
                .add("charset", gaSession.getCharset())
                .add("from", gaSession.getSocketChannel().socket().getRemoteSocketAddress())
                .add("to", gaSession.getSocketChannel().socket().getLocalSocketAddress())
                .draw();

    }

}
