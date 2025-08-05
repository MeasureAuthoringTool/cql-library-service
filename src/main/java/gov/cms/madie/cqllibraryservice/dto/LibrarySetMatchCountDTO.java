package gov.cms.madie.cqllibraryservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class LibrarySetMatchCountDTO {
    @Field("_id")
    private String librarySetId;
    private int matchCount;
    private String matchedLibraryId;
}
