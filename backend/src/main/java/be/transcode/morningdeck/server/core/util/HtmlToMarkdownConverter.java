package be.transcode.morningdeck.server.core.util;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

/**
 * Converts HTML content to Markdown format.
 * Used during feed ingestion to store content in a safe, renderable format.
 */
@Component
public class HtmlToMarkdownConverter {

    private final FlexmarkHtmlConverter converter;

    public HtmlToMarkdownConverter() {
        MutableDataSet options = new MutableDataSet();
        // Configure converter options
        options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false); // Use ATX style headers (###)
        options.set(FlexmarkHtmlConverter.OUTPUT_ATTRIBUTES_ID, false); // Don't output id attributes
        options.set(FlexmarkHtmlConverter.BR_AS_EXTRA_BLANK_LINES, false);

        this.converter = FlexmarkHtmlConverter.builder(options).build();
    }

    /**
     * Converts HTML content to Markdown.
     *
     * @param html The HTML content to convert
     * @return Markdown representation of the content, or empty string if input is null/blank
     */
    public String convert(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        String markdown = converter.convert(html);

        // Clean up excessive blank lines
        markdown = markdown.replaceAll("\n{3,}", "\n\n");

        return markdown.trim();
    }
}
