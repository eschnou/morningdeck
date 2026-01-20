package be.transcode.morningdeck.server.provider.emailsend.templating;

import be.transcode.morningdeck.server.provider.emailsend.exceptions.TemplateProcessingException;

import java.util.Map;

public interface EmailTemplateEngine {
    String processTemplate(String templateName, Map<String, Object> parameters) throws TemplateProcessingException;
}
