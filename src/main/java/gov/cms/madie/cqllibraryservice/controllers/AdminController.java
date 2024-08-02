package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.dto.LibraryUsage;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
  private final CqlLibraryService cqlLibraryService;

  @GetMapping(
      value = "/library-usage",
      produces = {MediaType.APPLICATION_JSON_VALUE})
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<List<LibraryUsage>> getLibraryUsage(
      HttpServletRequest request,
      @RequestParam("libraryName") String libraryName,
      @Value("${lambda-api-key}") String apiKey) {
    return ResponseEntity.badRequest().body(cqlLibraryService.findLibraryUsage(libraryName));
  }
}
