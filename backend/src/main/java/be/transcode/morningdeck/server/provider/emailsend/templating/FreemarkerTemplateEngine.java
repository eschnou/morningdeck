package be.transcode.morningdeck.server.provider.emailsend.templating;

import be.transcode.morningdeck.server.provider.emailsend.exceptions.TemplateProcessingException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.Map;

@Service
public class FreemarkerTemplateEngine implements EmailTemplateEngine {
    private final Configuration freemarkerConfig;

    public FreemarkerTemplateEngine(Configuration freemarkerConfig) {
        this.freemarkerConfig = freemarkerConfig;
    }

    @Override
    public String processTemplate(String templateName, Map<String, Object> parameters)
            throws TemplateProcessingException {
        try {
            // Get template directly using Freemarker's template loader
            Template template = freemarkerConfig.getTemplate("email/" + templateName + ".ftl");

            StringWriter writer = new StringWriter();
            template.process(parameters, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new TemplateProcessingException(
                    "Failed to process template: " + templateName, e
            );
        }
    }
}
