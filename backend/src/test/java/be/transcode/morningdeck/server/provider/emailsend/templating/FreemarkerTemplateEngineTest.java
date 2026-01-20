package be.transcode.morningdeck.server.provider.emailsend.templating;

import be.transcode.morningdeck.server.provider.emailsend.exceptions.TemplateProcessingException;
import freemarker.template.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static freemarker.template.Configuration.LEGACY_NAMING_CONVENTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FreemarkerTemplateEngineTest {

    @Mock
    private Configuration freemarkerConfig;

    @InjectMocks
    private FreemarkerTemplateEngine freemarkerTemplateEngine;

    @Test
    void testProcessTemplate_Success() throws Exception {
        String templateName = "testTemplate";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", "John Doe");

        when(freemarkerConfig.getIncompatibleImprovements()).thenReturn(Configuration.VERSION_2_3_28);
        when(freemarkerConfig.getNamingConvention()).thenReturn(LEGACY_NAMING_CONVENTION);
        when(freemarkerConfig.getObjectWrapper()).thenReturn(new freemarker.template.DefaultObjectWrapper());

        freemarker.template.Template mockTemplate = new freemarker.template.Template(
                templateName,
                "Hello ${name}!",
                freemarkerConfig
        );

        when(freemarkerConfig.getTemplate("email/" + templateName + ".ftl")).thenReturn(mockTemplate);

        String result = freemarkerTemplateEngine.processTemplate(templateName, parameters);
        assertEquals("Hello John Doe!", result);
    }


    @Test
    void testProcessTemplate_TemplateNotFound() throws IOException {
        String templateName = "nonExistentTemplate";
        Map<String, Object> parameters = new HashMap<>();

        when(freemarkerConfig.getTemplate("email/" + templateName + ".ftl"))
                .thenThrow(new freemarker.template.TemplateNotFoundException("email/" + templateName + ".ftl",null, null));

        assertThrows(TemplateProcessingException.class, () ->
                freemarkerTemplateEngine.processTemplate(templateName, parameters));
    }

}
