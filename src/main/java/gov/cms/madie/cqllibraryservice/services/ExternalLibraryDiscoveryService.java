package gov.cms.madie.cqllibraryservice.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Discovers valid CQL Libraries from a FHIR NPM package.
 *
 * <p>A valid CQL Library must satisfy:
 *
 * <ul>
 *   <li>{@code resourceType == "Library"}
 *   <li>{@code type.coding[*].code == "logic-library"}
 *   <li>At least one {@code content} entry with {@code contentType == "text/cql"}
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalLibraryDiscoveryService {

  /**
   * Discovers all valid CQL logic libraries from the given package path.
   *
   * @param packagePath the filesystem path of the extracted NPM package
   * @param igPackageId the IG package ID (e.g. {@code hl7.fhir.us.qicore})
   * @param igPackageVersion the IG package version (e.g. {@code 6.0.0})
   * @return candidate {@link ExternalLibrary} objects ready for persistence
   */
  public List<ExternalLibrary> discoverLibraries(
      String packagePath, String igPackageId, String igPackageVersion) {
    List<ExternalLibrary> allLibraries = new ArrayList<>();
    try {
      NpmPackage npmPackage = NpmPackage.fromFolder(packagePath);
      allLibraries.addAll(discoverLibraries(npmPackage, igPackageId, igPackageVersion));
    } catch (IOException e) {
      log.error(
          "Failed to load NpmPackage from path [{}] for package [{}#{}]: {}",
          packagePath,
          igPackageId,
          igPackageVersion,
          e.getMessage());
    }
    return allLibraries;
  }

  /**
   * Discovers all valid CQL logic libraries from the given in-memory {@link NpmPackage}.
   *
   * @param npmPackage the loaded NPM package
   * @param igPackageId the IG package ID
   * @param igPackageVersion the IG package version
   * @return candidate {@link ExternalLibrary} objects ready for persistence
   */
  public List<ExternalLibrary> discoverLibraries(
      NpmPackage npmPackage, String igPackageId, String igPackageVersion) {
    List<ExternalLibrary> discoveredLibraries = new ArrayList<>();

    JsonObject npm = npmPackage.getNpm();
    if (npm == null) {
      log.warn("Package [{}#{}] has no package.json – skipping", igPackageId, igPackageVersion);
      return discoveredLibraries;
    }

    String namespaceCanonical = npm.asString("canonical");
    String namespacePrefix = npm.asString("name");

    if (StringUtils.isBlank(namespaceCanonical)) {
      log.warn(
          "Package [{}#{}] package.json is missing 'canonical' – skipping",
          igPackageId,
          igPackageVersion);
      return discoveredLibraries;
    }
    if (StringUtils.isBlank(namespacePrefix)) {
      log.warn(
          "Package [{}#{}] package.json is missing 'name' – skipping discovery",
          igPackageId,
          igPackageVersion);
    }

    List<String> libraryFiles;
    try {
      libraryFiles = npmPackage.listResources("Library");
    } catch (IOException e) {
      log.error(
          "Failed to list Library resources in package [{}#{}]: {}",
          igPackageId,
          igPackageVersion,
          e.getMessage());
      return discoveredLibraries;
    }

    log.info(
        "Found {} Library resource(s) in package [{}#{}]",
        libraryFiles.size(),
        igPackageId,
        igPackageVersion);

    for (String filename : libraryFiles) {
      try (InputStream resourceStream = npmPackage.load("package", filename)) {
        ExternalLibrary discoveredLibrary =
            parseLibraryResource(resourceStream, namespaceCanonical, namespacePrefix, filename);
        if (discoveredLibrary != null) {
          discoveredLibraries.add(discoveredLibrary);
          log.debug(
              "Discovered CQL Library [{}] v[{}] from package [{}#{}]",
              discoveredLibrary.getLibraryName(),
              discoveredLibrary.getVersion(),
              igPackageId,
              igPackageVersion);
        }
      } catch (Exception e) {
        log.warn(
            "Skipping Library resource [{}] in package [{}#{}] due to error: {}",
            filename,
            igPackageId,
            igPackageVersion,
            e.getMessage());
      }
    }

    return discoveredLibraries;
  }

  /**
   * Parses a single FHIR Library JSON resource and returns a {@link ExternalLibrary} candidate if
   * it meets all criteria, or {@code null} if it should be ignored.
   */
  private ExternalLibrary parseLibraryResource(
      InputStream stream, String canonical, String namespacePrefix, String filename)
      throws IOException {

    JsonNode root = new ObjectMapper().readTree(stream);

    // 1. Must be resourceType == "Library"
    if (!"Library".equals(root.path("resourceType").asText(null))) {
      log.debug("Ignoring non-Library resource in file [{}]", filename);
      return null;
    }

    // 2. Must have type.coding[*].code == "logic-library"
    if (!hasLogicLibraryType(root)) {
      log.debug("Ignoring Library resource [{}] – not a logic-library type", filename);
      return null;
    }

    // 3. Must have cql(contentType == "text/cql")
    String cqlContent = extractCqlContent(root);
    if (cqlContent == null) {
      log.debug("Ignoring Library resource [{}] – no text/cql content found", filename);
      return null;
    }

    String libraryName = root.path("name").asText(null);
    String version = root.path("version").asText(null);
    String title = root.path("title").asText(null);
    String description = root.path("description").asText(null);
    String publisher = root.path("publisher").asText(null);

    if (StringUtils.isBlank(libraryName)) {
      log.warn("Library resource [{}] is missing 'name' – skipping", filename);
      return null;
    }
    if (StringUtils.isBlank(version)) {
      log.warn(
          "Library resource [{}] (name={}) is missing 'version' – skipping", filename, libraryName);
      return null;
    }

    return ExternalLibrary.builder()
        .libraryName(libraryName)
        .libraryTitle(title)
        .version(version)
        .description(description)
        .canonical(canonical)
        .namespacePrefix(namespacePrefix)
        .publisher(publisher)
        .cqlContent(cqlContent)
        .fhirResource(stripContentData(root))
        .draft(false)
        .dateImported(Instant.now())
        .build();
  }

  /**
   * Returns a JSON string of the FHIR resource with the entire {@code content} array removed.
   *
   * <p>FHIR Library resources embed base64-encoded payloads (CQL, ELM XML, ELM JSON, etc.) inside
   * the {@code content} array. These can be several MB each and, when stored verbatim, produce
   * documents large enough to crash MongoDB Compass. The decoded CQL is already persisted in the
   * dedicated {@code cqlContent} field, so dropping the whole array here is safe.
   */
  private String stripContentData(JsonNode root) {
    ObjectNode copy = root.deepCopy();
    copy.remove("content");
    return copy.toString();
  }

  /** Returns {@code true} if the resource has {@code type.coding[*].code == "logic-library"}. */
  private boolean hasLogicLibraryType(JsonNode root) {
    JsonNode codings = root.path("type").path("coding");
    if (codings.isMissingNode() || !codings.isArray()) {
      return false;
    }
    for (JsonNode coding : codings) {
      if ("logic-library".equals(coding.path("code").asText(null))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Finds the first {@code content} entry whose {@code contentType} is {@code "text/cql"} and
   * returns the base64-decoded CQL string. Returns {@code null} if no such entry exists.
   */
  private String extractCqlContent(JsonNode root) {
    JsonNode contentArray = root.path("content");
    if (contentArray.isMissingNode() || !contentArray.isArray()) {
      return null;
    }
    for (JsonNode content : contentArray) {
      if ("text/cql".equals(content.path("contentType").asText(null))) {
        String base64Data = content.path("data").asText(null);
        if (base64Data != null) {
          try {
            return new String(Base64.getDecoder().decode(base64Data), StandardCharsets.UTF_8);
          } catch (IllegalArgumentException e) {
            log.warn("Failed to base64-decode CQL content: {}", e.getMessage());
            return null;
          }
        }
        // data field absent but contentType matches – treat as no CQL available
        return null;
      }
    }
    return null;
  }
}
