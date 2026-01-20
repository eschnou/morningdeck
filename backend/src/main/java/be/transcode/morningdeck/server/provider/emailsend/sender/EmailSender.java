package be.transcode.morningdeck.server.provider.emailsend.sender;

import be.transcode.morningdeck.server.provider.emailsend.exceptions.EmailSendException;
import be.transcode.morningdeck.server.provider.emailsend.model.Email;

public interface EmailSender {
    void send(Email email) throws EmailSendException;
}
