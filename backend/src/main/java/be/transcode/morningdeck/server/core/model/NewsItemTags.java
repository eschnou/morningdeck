package be.transcode.morningdeck.server.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsItemTags {
    private List<String> topics;
    private List<String> people;
    private List<String> companies;
    private List<String> technologies;
    private String sentiment;
}
