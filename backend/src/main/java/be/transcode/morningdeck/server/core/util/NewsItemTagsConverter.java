package be.transcode.morningdeck.server.core.util;

import be.transcode.morningdeck.server.core.model.NewsItemTags;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for storing NewsItemTags as JSON in TEXT column.
 * Compatible with both PostgreSQL and H2.
 */
@Converter
public class NewsItemTagsConverter implements AttributeConverter<NewsItemTags, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(NewsItemTags attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting NewsItemTags to JSON", e);
        }
    }

    @Override
    public NewsItemTags convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, NewsItemTags.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting JSON to NewsItemTags", e);
        }
    }
}
